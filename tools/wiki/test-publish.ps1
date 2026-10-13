# Focused publisher checks against disposable local Git repositories; no network.
[CmdletBinding()]
param()
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$publisher = Join-Path $PSScriptRoot 'publish.ps1'
$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$fixtureName = 'essence-wiki-test-' + [Guid]::NewGuid().ToString('N')
$fixtureRoot = Join-Path $tempParent $fixtureName
$fixtureSource = Join-Path $fixtureRoot 'source with spaces'
$fixturePages = Join-Path $fixtureSource 'docs/wiki'
$fixtureSeed = Join-Path $fixtureRoot 'seed'
$fixtureRemote = Join-Path $fixtureRoot 'wiki.git'
$testEnvironment = @{}
foreach ($name in @('GIT_AUTHOR_NAME', 'GIT_AUTHOR_EMAIL', 'GIT_COMMITTER_NAME', 'GIT_COMMITTER_EMAIL')) {
    $testEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

function Test-Git {
    param([string[]]$GitArguments)
    $ErrorActionPreference = 'Continue'
    $result = @(& git @GitArguments 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ($result -join [Environment]::NewLine) }
    return $result
}
function Assert-Test {
    param([bool]$Condition, [string]$Label)
    if (-not $Condition) { throw "FAIL: $Label" }
    Write-Host "PASS: $Label"
}
function Assert-Rejected {
    param([scriptblock]$Action, [string]$Expected, [string]$Label)
    $rejected = $false
    try { & $Action | Out-Null } catch { $rejected = $_.Exception.Message -like "*$Expected*" }
    Assert-Test $rejected $Label
}

try {
    [Environment]::SetEnvironmentVariable('GIT_AUTHOR_NAME', 'Wiki publisher test', 'Process')
    [Environment]::SetEnvironmentVariable('GIT_COMMITTER_NAME', 'Wiki publisher test', 'Process')
    [Environment]::SetEnvironmentVariable('GIT_AUTHOR_EMAIL', 'wiki-test@example.invalid', 'Process')
    [Environment]::SetEnvironmentVariable('GIT_COMMITTER_EMAIL', 'wiki-test@example.invalid', 'Process')
    New-Item -ItemType Directory -Path $fixturePages -Force | Out-Null
    Test-Git @('init', '--quiet', $fixtureSource) | Out-Null
    Set-Content -LiteralPath (Join-Path $fixturePages 'Home.md') -Value '# Updated Home' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $fixturePages 'Commands.md') -Value '# Updated Commands' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $fixtureSource 'code.txt') -Value 'Original code' -Encoding UTF8
    Test-Git @('-C', $fixtureSource, 'add', '.') | Out-Null
    Test-Git @('-C', $fixtureSource, 'commit', '--quiet', '-m', 'Source fixture') | Out-Null
    Test-Git @('init', '--quiet', '--bare', '--initial-branch=published', $fixtureRemote) | Out-Null
    Test-Git @('clone', '--quiet', $fixtureRemote, $fixtureSeed) | Out-Null
    Set-Content -LiteralPath (Join-Path $fixtureSeed 'Home.md') -Value '# Original Home' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $fixtureSeed 'Commands.md') -Value '# Original Commands' -Encoding UTF8
    Set-Content -LiteralPath (Join-Path $fixtureSeed 'Extra.md') -Value '# Preserve this page' -Encoding UTF8
    Test-Git @('-C', $fixtureSeed, 'add', '.') | Out-Null
    Test-Git @('-C', $fixtureSeed, 'commit', '--quiet', '-m', 'Wiki fixture') | Out-Null
    Test-Git @('-C', $fixtureSeed, 'push', '--quiet', 'origin', 'published') | Out-Null
    $initialHead = [string](Test-Git @('--git-dir', $fixtureRemote, 'rev-parse', 'published'))
    $tempBefore = @(Get-ChildItem -LiteralPath $tempParent -Directory -Filter 'essence-wiki-publish-*' | Select-Object -ExpandProperty FullName)

    & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'rev-parse', 'published'))) -eq $initialHead) 'Preview does not push'
    $tempAfter = @(Get-ChildItem -LiteralPath $tempParent -Directory -Filter 'essence-wiki-publish-*' | Select-Object -ExpandProperty FullName)
    Assert-Test (@($tempAfter | Where-Object { $_ -notin $tempBefore }).Count -eq 0) 'Successful preview removes temporary checkout'

    Set-Content -LiteralPath (Join-Path $fixtureSource 'code.txt') -Value 'Pending code changes' -Encoding UTF8
    $codeBefore = (Get-FileHash -LiteralPath (Join-Path $fixtureSource 'code.txt')).Hash
    & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote -Pages Home.md -Publish
    $focusedHead = [string](Test-Git @('--git-dir', $fixtureRemote, 'rev-parse', 'published'))
    Assert-Test ($focusedHead -ne $initialHead) 'Explicit publication discovers nonstandard default branch'
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'show', 'published:Home.md'))) -match 'Updated Home') 'Selected page published'
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'show', 'published:Commands.md'))) -match 'Original Commands') 'Unselected page preserved'
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'show', 'published:Extra.md'))) -match 'Preserve this page') 'Wiki-only page preserved'
    Assert-Test ((Get-FileHash -LiteralPath (Join-Path $fixtureSource 'code.txt')).Hash -eq $codeBefore) 'Pending code remains unchanged'
    Assert-Test (@(Test-Git @('-C', $fixtureSource, 'diff', '--cached', '--name-only')).Count -eq 0) 'Source index remains unchanged'

    & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote -Pages Home.md -Publish
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'rev-parse', 'published'))) -eq $focusedHead) 'Identical publication creates no commit'
    & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote -Publish
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'show', 'published:Commands.md'))) -match 'Updated Commands') 'Default publication selects all source pages'
    Assert-Test (([string](Test-Git @('--git-dir', $fixtureRemote, 'show', 'published:Extra.md'))) -match 'Preserve this page') 'Full publication preserves wiki-only content'

    Add-Content -LiteralPath (Join-Path $fixturePages 'Home.md') -Value 'Uncommitted edit'
    Assert-Rejected { & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote -Publish } 'uncommitted changes' 'Dirty wiki source rejected'
    Assert-Rejected { & $publisher -SourceDirectory $fixturePages -WikiRemote $fixtureRemote -Pages '../code.txt' } 'unsafe page filename' 'Path traversal rejected'
    Assert-Rejected { & $publisher -SourceDirectory $fixturePages -WikiRemote 'https://user:dummy@example.invalid/wiki.git' } 'without embedded credentials' 'Credentials in remote URL rejected'
    $tempAfter = @(Get-ChildItem -LiteralPath $tempParent -Directory -Filter 'essence-wiki-publish-*' | Select-Object -ExpandProperty FullName)
    Assert-Test (@($tempAfter | Where-Object { $_ -notin $tempBefore }).Count -eq 0) 'Successful runs leave no extra local wiki copies'
} finally {
    foreach ($name in $testEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($name, $testEnvironment[$name], 'Process')
    }
    if (Test-Path -LiteralPath $fixtureRoot) {
        $resolvedFixture = (Resolve-Path -LiteralPath $fixtureRoot).ProviderPath
        if ([IO.Path]::GetDirectoryName($resolvedFixture).TrimEnd('\', '/') -ne $tempParent -or
            [IO.Path]::GetFileName($resolvedFixture) -ne $fixtureName -or
            ((Get-Item -LiteralPath $resolvedFixture).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Test cleanup target failed its safety check.'
        }
        Remove-Item -LiteralPath $resolvedFixture -Recurse -Force
    }
}
