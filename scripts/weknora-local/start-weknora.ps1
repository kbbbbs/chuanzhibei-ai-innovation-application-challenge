[CmdletBinding()]
param(
    [string]$RepoDir,
    [switch]$SkipGitUpdate,
    [switch]$OverwriteTemplates
)

$ErrorActionPreference = 'Stop'

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceRoot = Resolve-Path (Join-Path $scriptRoot '..\..')
if (-not $RepoDir) {
    $RepoDir = Join-Path $workspaceRoot '.omx\WeKnora'
}

function Test-Command {
    param([Parameter(Mandatory = $true)][string]$Name)
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

if (-not (Test-Command git)) {
    throw 'Git is required to download Tencent/WeKnora.'
}

if (-not (Test-Command docker)) {
    throw 'Docker Desktop with Docker Compose is required. Install/start Docker Desktop, then rerun this script.'
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw 'Docker is installed but the Docker daemon is not reachable. Start Docker Desktop, then rerun this script.'
}

$repoParent = Split-Path -Parent $RepoDir
if (-not (Test-Path -LiteralPath $repoParent)) {
    New-Item -ItemType Directory -Path $repoParent | Out-Null
}

if (-not (Test-Path -LiteralPath (Join-Path $RepoDir '.git'))) {
    git clone --depth 1 --filter=blob:none https://github.com/Tencent/WeKnora.git $RepoDir
} elseif (-not $SkipGitUpdate) {
    git -C $RepoDir pull --ff-only
}

$envTarget = Join-Path $RepoDir '.env'
if ($OverwriteTemplates -or -not (Test-Path -LiteralPath $envTarget)) {
    Copy-Item -LiteralPath (Join-Path $scriptRoot 'weknora.env.example') -Destination $envTarget -Force
}

$modelTarget = Join-Path $RepoDir 'config\builtin_models.yaml'
if ($OverwriteTemplates -or -not (Test-Path -LiteralPath $modelTarget)) {
    Copy-Item -LiteralPath (Join-Path $scriptRoot 'builtin_models.yaml') -Destination $modelTarget -Force
}

$composeTarget = Join-Path $RepoDir 'docker-compose.ai-learning.yml'
Copy-Item -LiteralPath (Join-Path $scriptRoot 'docker-compose.ai-learning.yml') -Destination $composeTarget -Force

$envText = Get-Content -LiteralPath $envTarget -Raw
if ($envText -match 'replace-me') {
    Write-Warning 'Model API keys are still placeholders in .omx\WeKnora\.env. WeKnora can start, but embedding/chat calls will fail until you fill them.'
}

Push-Location $RepoDir
try {
    docker compose -f docker-compose.yml -f docker-compose.ai-learning.yml --env-file .env up -d
} finally {
    Pop-Location
}

Write-Host 'WeKnora local services requested.'
Write-Host 'UI:  http://localhost:8091'
Write-Host 'API: http://localhost:8090'
Write-Host 'After creating a WeKnora API key in the UI, run configure-ai-learning-weknora.ps1 and restart ai-learning-backend.'
