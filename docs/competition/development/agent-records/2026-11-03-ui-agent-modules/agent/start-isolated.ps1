param([string]$RunLabel = 'isolated')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../..')).Path
$out = $PSScriptRoot
if ((Split-Path $repo -Leaf) -ne 'ai-learning-collaboration') { throw 'Unexpected workspace' }
if (Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue) { throw '8082 occupied' }
foreach ($line in Get-Content -LiteralPath (Join-Path $repo '.env.local')) {
    if ($line -match '^\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)$') {
        $name = $Matches[1]
        $value = $Matches[2].Trim().Trim('"').Trim("'")
        [Environment]::SetEnvironmentVariable($name, $(if ($value) { $value } else { $null }), 'Process')
    }
}
foreach ($suffix in @('BASE_URL', 'API_KEY', 'MODEL')) {
    $planningName = "AI_PLANNING_$suffix"
    if (-not [Environment]::GetEnvironmentVariable($planningName, 'Process')) {
        [Environment]::SetEnvironmentVariable($planningName, [Environment]::GetEnvironmentVariable("AI_GENERATION_$suffix", 'Process'), 'Process')
    }
}
$env:AI_AGENT_PLANNER_TIMEOUT_SECONDS = '10'
$env:AI_PLANNING_TIMEOUT_SECONDS = '10'
$env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$dbPath = (Join-Path $out 'isolated-data/academy').Replace('\','/')
$ragPath = (Join-Path $out 'isolated-data/documents').Replace('\','/')
$jar = Join-Path $repo 'ai-learning-backend/target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar'
$argsList = @('-jar', $jar, '--spring.profiles.active=contest-demo', '--server.port=8082',
    "--spring.datasource.url=jdbc:h2:file:$dbPath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE",
    "--rag.storage-path=$ragPath")
$process = Start-Process -FilePath 'D:\language\Java\bin\java.exe' -ArgumentList $argsList -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $out "$RunLabel.stdout.log") -RedirectStandardError (Join-Path $out "$RunLabel.stderr.log")
[pscustomobject]@{
    processId = $process.Id
    port = 8082
    timestamp = (Get-Date -Format o)
    planningModel = $(if ($env:AI_PLANNING_MODEL) { $env:AI_PLANNING_MODEL } else { $env:AI_GENERATION_MODEL })
    generationModel = $env:AI_GENERATION_MODEL
    intentTimeoutSeconds = 10
    runtimePlanningTimeoutSeconds = 10
    parameterTimeoutSeconds = 10
    chatRequestTimeoutSeconds = 25
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out "$RunLabel-process.json")
Get-Content -LiteralPath (Join-Path $out "$RunLabel-process.json")
