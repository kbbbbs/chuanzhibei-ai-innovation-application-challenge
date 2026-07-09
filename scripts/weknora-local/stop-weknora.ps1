[CmdletBinding()]
param(
    [string]$RepoDir
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = Resolve-Path (Join-Path $scriptRoot '..\..')
if (-not $RepoDir) {
    $RepoDir = Join-Path $workspaceRoot '.omx\WeKnora'
}

if (-not (Test-Path -LiteralPath (Join-Path $RepoDir 'docker-compose.yml'))) {
    throw "WeKnora repo was not found at $RepoDir."
}

Push-Location $RepoDir
try {
    $composeArgs = @('-f', 'docker-compose.yml')
    if (Test-Path -LiteralPath (Join-Path $RepoDir 'docker-compose.ai-learning.yml')) {
        $composeArgs += @('-f', 'docker-compose.ai-learning.yml')
    }
    if (Test-Path -LiteralPath (Join-Path $RepoDir '.env')) {
        $composeArgs += @('--env-file', '.env')
    }
    docker compose @composeArgs down
} finally {
    Pop-Location
}
