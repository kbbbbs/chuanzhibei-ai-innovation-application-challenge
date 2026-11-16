param(
    [ValidateSet('start', 'restart', 'status')]
    [string]$Action = 'start'
)

$ErrorActionPreference = 'Stop'
$workspacePath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$privateDirectory = Join-Path $workspacePath '.codex_tmp/knowledge-graph'
$privateEnvironment = Join-Path $privateDirectory 'neo4j.env'
$containerName = 'ai-learning-knowledge-graph'
$imageName = 'neo4j:5.26.33-community@sha256:577eb43120e8688e62e753e962c47d30044ecaa95147ee5386c29f226b878008'

docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw '请先启动 Docker Desktop。' }

$containerState = docker inspect $containerName --format '{{.State.Status}}' 2>$null
if ($Action -eq 'status') {
    if ($LASTEXITCODE -ne 0) { Write-Output '知识图谱数据库尚未创建。'; exit 1 }
    Write-Output "知识图谱数据库状态 $containerState"
    exit 0
}

if ($containerState) {
    if ($Action -eq 'restart') { docker restart $containerName | Out-Null }
    else { docker start $containerName | Out-Null }
    if ($LASTEXITCODE -ne 0) { throw '数据库启动失败，请检查 Docker 状态。' }
} else {
    New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
    if (-not (Test-Path -LiteralPath $privateEnvironment)) {
        $randomBytes = New-Object byte[] 32
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
        $generatedPassword = [Convert]::ToBase64String($randomBytes)
        $lines = @(
            "NEO4J_AUTH=neo4j/$generatedPassword",
            'NEO4J_server_memory_heap_initial__size=256m',
            'NEO4J_server_memory_heap_max__size=512m',
            'NEO4J_server_memory_pagecache_size=256m'
        )
        [IO.File]::WriteAllLines($privateEnvironment, $lines, (New-Object Text.UTF8Encoding $false))
    }
    docker run --name $containerName --detach --publish 127.0.0.1:7687:7687 --publish 127.0.0.1:7474:7474 `
        --env-file $privateEnvironment --volume ai-learning-knowledge-graph-data:/data --restart unless-stopped $imageName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '数据库创建失败，请检查镜像下载或端口占用。' }
}

Write-Output 'Neo4j 正在启动，数据库端口位于本机 7687，管理页面位于 http://127.0.0.1:7474。'
Write-Output '凭据保存在工作区 .codex_tmp/knowledge-graph/neo4j.env。请勿提交或分享此文件。'
