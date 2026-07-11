[CmdletBinding()]
param(
    [string]$RepoDir,
    [switch]$SkipGitUpdate,
    [switch]$OverwriteTemplates,
    [switch]$ForceLocalAppBuild
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
    $repoChanges = @(git -C $RepoDir status --porcelain)
    if ($repoChanges.Count -gt 0) {
        Write-Warning 'The local WeKnora checkout has uncommitted changes. Skipping git pull to preserve them.'
    } else {
        git -C $RepoDir pull --ff-only
    }
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
    $composeArgs = @('-f', 'docker-compose.yml', '-f', 'docker-compose.ai-learning.yml', '--env-file', '.env')
    $appImage = @(docker compose @composeArgs config --images | Where-Object { $_ -match 'weknora-app' } | Select-Object -First 1)
    $appImageUsable = $false
    if ($appImage.Count -gt 0 -and -not $ForceLocalAppBuild) {
        docker image inspect $appImage[0] *> $null
        if ($LASTEXITCODE -eq 0) {
            docker run --rm --entrypoint /bin/sh $appImage[0] -c 'test -s /app/WeKnora' *> $null
            $appImageUsable = $LASTEXITCODE -eq 0
        }
    }

    if (-not $appImageUsable) {
        Write-Warning 'The published WeKnora app image is missing a usable /app/WeKnora binary. Building the app image from the local checkout.'
        docker compose @composeArgs build app
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to build the WeKnora app image from source.'
        }
    }

    docker compose @composeArgs up -d
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to start WeKnora services.'
    }
} finally {
    Pop-Location
}

Write-Host 'WeKnora local services requested.'
Write-Host 'UI:  http://localhost:8091'
Write-Host 'API: http://localhost:8090'
Write-Host 'After creating a WeKnora API key in the UI, run configure-ai-learning-weknora.ps1 and restart ai-learning-backend.'
