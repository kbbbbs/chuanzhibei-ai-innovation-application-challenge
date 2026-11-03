$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../..')).Path
if ((Split-Path $repo -Leaf) -ne 'ai-learning-collaboration') { throw 'Unexpected workspace' }
if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) { throw '8082 occupied' }
$private = Join-Path $repo '.codex_tmp/official-deepseek-retry'
New-Item -ItemType Directory -Force -Path $private | Out-Null
$source = Get-Content -LiteralPath (Join-Path (Split-Path $repo) '新建 文本文档.txt') -Raw
$urls = [regex]::Matches($source, 'https?://[^\s"''<>]+')
$selected = @($urls | Where-Object { ([uri]$_.Value).Host -eq 'api.deepseek.com' })
if ($selected.Count -ne 1) { throw 'Expected exactly one official URL' }
$official = $selected[0]
$tail = $source.Substring($official.Index + $official.Length)
$key = [regex]::Match($tail, '\bsk-[A-Za-z0-9_-]+\b').Value
if (-not $key) { throw 'No official key after official URL' }
foreach ($line in Get-Content -LiteralPath (Join-Path $repo '.env.local')) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $name = $Matches[1]
        $value = $Matches[2].Trim().Trim('"').Trim("'")
        if ($value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
    }
}
foreach ($role in @('GENERATION', 'PLANNING')) {
    [Environment]::SetEnvironmentVariable("AI_${role}_BASE_URL", 'https://api.deepseek.com', 'Process')
    [Environment]::SetEnvironmentVariable("AI_${role}_API_KEY", $key, 'Process')
    [Environment]::SetEnvironmentVariable("AI_${role}_MODEL", 'deepseek-flash', 'Process')
}
$env:AI_AGENT_PLANNER_TIMEOUT_SECONDS = '5'
$env:AI_PLANNING_TIMEOUT_SECONDS = '10'
$env:AI_AGENT_PLANNER_PARAMETER_TIMEOUT_SECONDS = '10'
$env:AI_AGENT_REQUEST_TIMEOUT_SECONDS = '25'
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
"OFFICIAL_API_KEY=$key`nJWT_SECRET=$($env:JWT_SECRET)" | Set-Content -LiteralPath (Join-Path $private 'redaction.env')
$jar = Join-Path $repo 'ai-learning-backend/target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar'
$baseline = [ordered]@{
    timestamp = (Get-Date -Format o)
    jarSha256 = (Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash
    envSha256 = (Get-FileHash -LiteralPath (Join-Path $repo '.env.local') -Algorithm SHA256).Hash
    listeners = @(Get-NetTCPConnection -State Listen -LocalPort 8080,5173 | Select-Object LocalPort,OwningProcess)
}
$db = (Join-Path $private 'data/academy').Replace('\','/')
$rag = (Join-Path $private 'documents').Replace('\','/')
$argsList = @('-jar', $jar, '--spring.profiles.active=contest-demo', '--server.port=8082',
    "--spring.datasource.url=jdbc:h2:file:$db;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
    "--rag.storage-path=$rag")
$proc = Start-Process -FilePath 'D:\language\Java\bin\java.exe' -ArgumentList $argsList -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $PSScriptRoot 'official.stdout.log') -RedirectStandardError (Join-Path $PSScriptRoot 'official.stderr.log')
$baseline.processId = $proc.Id
$baseline.configuration = @{baseUrl='https://api.deepseek.com';model='deepseek-flash';intent=5;runtimePlanning=10;parameters=10;chat=25}
$baseline | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $private 'process-baseline-private.json')
$public = [ordered]@{timestamp=$baseline.timestamp;processId=$baseline.processId;listeners=$baseline.listeners;configuration=$baseline.configuration;jarExists=$true;baselineCapturedPrivately=$true}
$public | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'process-baseline.json')
$public | ConvertTo-Json -Depth 6
