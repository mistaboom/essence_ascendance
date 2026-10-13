<#
.SYNOPSIS
Preview or publish committed docs/wiki pages using a temporary wiki checkout.
.DESCRIPTION
Without -Publish, shows the proposed diff without pushing. Uses existing Git
authentication, preserves other wiki files, and never force-pushes or deletes pages.
#>
[CmdletBinding()]
param(
    [switch]$Publish,
    [string[]]$Pages,
    [ValidateNotNullOrEmpty()]
    [string]$Message = 'Publish reviewed wiki documentation',
    [string]$SourceDirectory = (Join-Path $PSScriptRoot '../../docs/wiki'),
    [string]$WikiRemote = 'https://github.com/mistaboom/essence_ascendance.wiki.git'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'Git is required. Use your existing GitHub Git sign-in before publishing.'
}

function Invoke-WikiGit {
    param([string[]]$GitArguments)
    # Windows PowerShell can turn native stderr into ErrorRecords even on success.
    $ErrorActionPreference = 'Continue'
    $output = @(& git @GitArguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw ('Git failed: ' + ($output -join [Environment]::NewLine))
    }
    return $output
}

# Never accept credentials embedded in a publishing URL.
$remoteUri = $null
if ([Uri]::TryCreate($WikiRemote, [UriKind]::Absolute, [ref]$remoteUri) -and
    $remoteUri.Scheme -ne 'file' -and
    ($remoteUri.Scheme -ne 'https' -or $remoteUri.UserInfo)) {
    throw 'Use an HTTPS wiki URL without embedded credentials, or a local test repository.'
}

$sourcePath = (Resolve-Path -LiteralPath $SourceDirectory).ProviderPath
$sourceRoot = [string](Invoke-WikiGit @('-C', $sourcePath, 'rev-parse', '--show-toplevel'))
$sourceCommit = [string](Invoke-WikiGit @('-C', $sourceRoot, 'rev-parse', 'HEAD'))
$sourcePrefix = [string](Invoke-WikiGit @('-C', $sourcePath, 'rev-parse', '--show-prefix'))
$sourceFiles = @(Get-ChildItem -LiteralPath $sourcePath -File -Filter '*.md')
if ($sourceFiles.Count -eq 0) { throw 'No Markdown pages were found.' }

if ($Pages) {
    foreach ($page in $Pages) {
        if ($page -notmatch '^[A-Za-z0-9_-]+\.md$' -or
            $page -notin $sourceFiles.Name) {
            throw "Unknown or unsafe page filename: $page. Use an existing Markdown filename."
        }
    }
    $sourceFiles = @($sourceFiles | Where-Object { $_.Name -in $Pages })
}

$relativePaths = @()
foreach ($file in $sourceFiles) {
    if ($file.Name -notmatch '^[A-Za-z0-9_-]+\.md$' -or
        ($file.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
        throw "Page filenames must be ordinary Markdown files: $($file.Name)"
    }
    $relativePaths += $sourcePrefix + $file.Name
}
Invoke-WikiGit (@('-C', $sourceRoot, 'ls-files', '--error-unmatch', '--') + $relativePaths) | Out-Null
$sourceStatus = @(Invoke-WikiGit (@('-C', $sourceRoot, 'status', '--porcelain', '--') + $relativePaths))
if ($sourceStatus.Count -gt 0) {
    throw 'Selected pages have uncommitted changes. Review and commit them before publishing or previewing.'
}

$tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
$tempName = 'essence-wiki-publish-' + [Guid]::NewGuid().ToString('N')
$tempPath = Join-Path $tempParent $tempName
$checkoutPath = Join-Path $tempPath 'wiki'
$success = $false
try {
    New-Item -ItemType Directory -Path $tempPath | Out-Null
    Write-Host "Source commit: $sourceCommit"
    Write-Host ('Selected pages: ' + ($sourceFiles.Name -join ', '))
    Invoke-WikiGit @('clone', '--quiet', '--', $WikiRemote, $checkoutPath) | Out-Null
    $branch = [string](Invoke-WikiGit @('-C', $checkoutPath, 'symbolic-ref', '--short', 'HEAD'))
    $baseCommit = [string](Invoke-WikiGit @('-C', $checkoutPath, 'rev-parse', 'HEAD'))
    Write-Host "Wiki default branch: $branch; current commit: $baseCommit"

    foreach ($file in $sourceFiles) {
        $destination = Join-Path $checkoutPath $file.Name
        if ((Test-Path -LiteralPath $destination) -and
            ((Get-Item -LiteralPath $destination).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "Wiki page is a symbolic link; reconcile it manually: $($file.Name)"
        }
        Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
    }
    # Catch source edits made while this publication was being prepared.
    Invoke-WikiGit (@('-C', $sourceRoot, 'diff', '--exit-code', $sourceCommit, '--') + $relativePaths) | Out-Null
    $pageNames = @($sourceFiles.Name)
    Invoke-WikiGit (@('-C', $checkoutPath, 'add', '--') + $pageNames) | Out-Null
    Invoke-WikiGit @('-C', $checkoutPath, 'diff', '--cached', '--check') | Out-Null
    $changed = @(Invoke-WikiGit @('-C', $checkoutPath, 'diff', '--cached', '--name-only'))
    if ($changed.Count -eq 0) {
        Write-Host 'Selected pages already match the published wiki. Nothing to push.'
    } else {
        Invoke-WikiGit @('-C', $checkoutPath, '--no-pager', 'diff', '--cached', '--no-ext-diff', '--color=never') | ForEach-Object { Write-Host $_ }
        if ($Publish) {
            Invoke-WikiGit @('-C', $checkoutPath, 'commit', '-m', $Message, '-m', "Canonical documentation source: $sourceCommit") | ForEach-Object { Write-Host $_ }
            Invoke-WikiGit @('-C', $checkoutPath, 'push', 'origin', "HEAD:refs/heads/$branch") | ForEach-Object { Write-Host $_ }
            $publishedCommit = [string](Invoke-WikiGit @('-C', $checkoutPath, 'rev-parse', 'HEAD'))
            $remoteRef = [string](Invoke-WikiGit @('-C', $checkoutPath, 'ls-remote', 'origin', "refs/heads/$branch"))
            if (($remoteRef -split '\s+')[0] -ne $publishedCommit) {
                throw 'The remote changed during publication. Inspect the retained checkout before retrying.'
            }
            Write-Host "Published wiki commit: $publishedCommit"
        } else {
            Write-Host 'Preview only. Reconcile any wiki-side edits, then rerun with -Publish to push.'
        }
    }
    $success = $true
} finally {
    if ($success) {
        # Validate the exact absolute target before any recursive Windows deletion.
        $resolvedTemp = (Resolve-Path -LiteralPath $tempPath).ProviderPath
        $resolvedParent = [IO.Path]::GetDirectoryName($resolvedTemp).TrimEnd('\', '/')
        if ($resolvedParent -ne $tempParent -or
            [IO.Path]::GetFileName($resolvedTemp) -ne $tempName -or
            ((Get-Item -LiteralPath $resolvedTemp).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Temporary directory cleanup target failed its safety check.'
        }
        Remove-Item -LiteralPath $resolvedTemp -Recurse -Force
        Write-Host 'Temporary wiki checkout removed.'
    } elseif (Test-Path -LiteralPath $tempPath) {
        Write-Warning "Publication did not complete. Recovery files retained at: $tempPath"
    }
}
