$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../..')).Path
$baseline = Get-Content -LiteralPath (Join-Path $repo '.codex_tmp/official-deepseek-retry/process-baseline-private.json') -Raw | ConvertFrom-Json
$listener = Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue
if ($listener -and $listener.OwningProcess -ne $baseline.processId) { throw '8082 process is not owned by this experiment' }
$proc = Get-Process -Id $baseline.processId -ErrorAction SilentlyContinue
if ($proc) {
    if ($proc.Path -ne 'D:\language\Java\bin\java.exe') { throw 'Unexpected executable' }
    Stop-Process -Id $baseline.processId
    $proc.WaitForExit(10000) | Out-Null
}
$remaining = @(Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue)
$checks = @(
    @{name='own-8082-closed';ok=($remaining.Count -eq 0)},
    @{name='env-local-unchanged';ok=((Get-FileHash -LiteralPath (Join-Path $repo '.env.local') -Algorithm SHA256).Hash -eq $baseline.envSha256)},
    @{name='jar-unchanged';ok=((Get-FileHash -LiteralPath (Join-Path $repo 'ai-learning-backend/target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar') -Algorithm SHA256).Hash -eq $baseline.jarSha256)}
)
foreach ($port in @(8080,5173)) {
    $current = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    $previous = @($baseline.listeners | Where-Object LocalPort -eq $port)[0]
    $ok = $current -and ($current.OwningProcess -eq $previous.OwningProcess)
    $checks += @{name="original-$port-listener-unchanged";ok=[bool]$ok;processId=$current.OwningProcess}
    try {
        $uri = if ($port -eq 8080) { 'http://127.0.0.1:8080/courses?pageSize=1' } else { 'http://127.0.0.1:5173/' }
        $response = Invoke-WebRequest -Uri $uri -TimeoutSec 10 -SkipHttpErrorCheck
        $checks += @{name="original-$port-http-responsive";ok=($response.StatusCode -in @(200,401,403));httpStatus=[int]$response.StatusCode}
    } catch { $checks += @{name="original-$port-http-responsive";ok=$false;error=$_.Exception.Message} }
}
$result = @{timestamp=(Get-Date -Format o);stoppedProcessId=$baseline.processId;checks=$checks;verdict=$(if (@($checks | Where-Object { -not $_.ok }).Count -eq 0) {'PASS'} else {'FAIL'})}
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'cleanup-results.json')
$result | ConvertTo-Json -Depth 8
