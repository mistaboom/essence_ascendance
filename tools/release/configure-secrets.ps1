param(
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')]
    [string]$Repository
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw 'Install GitHub CLI and run gh auth login before using this script.'
}
gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) { throw 'Sign in with gh auth login first.' }

# Confirm the destination account/repository before reading any local tokens.
gh repo view $Repository --json nameWithOwner,url
if ($LASTEXITCODE -ne 0) { throw 'Repository is missing or inaccessible.' }

foreach ($secretName in @('CURSEFORGE_ESSENCE_ASCENDANCE', 'MODRINTH_ESSENCE_ASCENDANCE')) {
    $token = [Environment]::GetEnvironmentVariable($secretName, 'User')
    if ([string]::IsNullOrWhiteSpace($token)) {
        $token = [Environment]::GetEnvironmentVariable($secretName, 'Process')
    }
    if ([string]::IsNullOrWhiteSpace($token)) {
        $token = [Environment]::GetEnvironmentVariable($secretName, 'Machine')
    }
    if ([string]::IsNullOrWhiteSpace($token)) { throw "Missing Windows environment variable: $secretName" }
    # Feed the token through stdin, never command arguments, logs, or a file.
    $token | gh secret set $secretName --repo $Repository
    if ($LASTEXITCODE -ne 0) { throw "Could not configure repository secret: $secretName" }
    $token = $null
    Write-Output "Configured $secretName"
}
gh secret list --repo $Repository
if ($LASTEXITCODE -ne 0) { throw 'Could not verify the configured secret names.' }

