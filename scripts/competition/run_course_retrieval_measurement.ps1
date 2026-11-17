param([string]$JavaRuntimeHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $projectRoot 'ai-learning-backend'
$evidenceRoot = Join-Path $projectRoot 'docs/competition/submission-10039/retrieval'
$modelNames = @('RAG_EMBEDDING_BASE_URL', 'RAG_EMBEDDING_MODEL', 'RAG_EMBEDDING_API_KEY',
    'RAG_EMBEDDING_APIKEY', 'SILICONFLOW_API_KEY', 'OCR_BASE_URL', 'OCR_MODEL', 'OCR_API_KEY', 'OCR_APIKEY')
foreach ($name in $modelNames) {
    if (-not [Environment]::GetEnvironmentVariable($name, 'Process')) {
        $value = [Environment]::GetEnvironmentVariable($name, 'User')
        if ($value) { [Environment]::SetEnvironmentVariable($name, $value, 'Process') }
    }
}
$privateSettings = Join-Path $projectRoot '.env.local'
if (Test-Path -LiteralPath $privateSettings) {
    foreach ($line in Get-Content -LiteralPath $privateSettings) {
        if ($line -match '^([A-Z][A-Z0-9_]+)=(.*)$' -and $Matches[2] -ne '' -and
            -not [Environment]::GetEnvironmentVariable($Matches[1], 'Process')) {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
}
if ($JavaRuntimeHome) { $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaRuntimeHome).Path }
$runId = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$runDirectory = Join-Path $backendRoot "target/contest-course-retrieval/$runId"
$env:CONTEST_COURSE_RETRIEVAL_CHECK = 'true'
$env:CONTEST_MEASUREMENT_OUTPUT = $runDirectory
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
Push-Location $backendRoot
try {
    # Windows PowerShell turns JVM warnings on stderr into NativeCommandError.
    # Use the native exit code to decide whether Maven completed successfully.
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & mvn '-Dtest=ContestCourseRetrievalMeasurementTest' test *> (Join-Path $runDirectory 'maven.log')
        $testExit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousErrorPreference }
} finally { Pop-Location }
$reportPath = Join-Path $runDirectory 'report.json'
if (Test-Path -LiteralPath $reportPath) {
    $publishedRun = Join-Path $evidenceRoot "runs/$runId"
    New-Item -ItemType Directory -Path $publishedRun -Force | Out-Null
    Copy-Item -LiteralPath $reportPath -Destination (Join-Path $publishedRun 'report.json')
    $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
    if ($report.questions.Count -eq 20) {
        & python (Join-Path $PSScriptRoot 'measure_course_retrieval.py') `
            --dataset (Join-Path $evidenceRoot 'dataset.json') --report $reportPath `
            --output (Join-Path $publishedRun 'metrics.json')
        if ($LASTEXITCODE -ne 0) { throw 'Measurement summary failed. Retain the run report for diagnosis.' }
    }
    Write-Output "Measurement evidence saved to $publishedRun"
}
if ($testExit -eq 0 -and -not (Test-Path -LiteralPath $reportPath)) {
    throw 'Maven completed without a measurement report. Check that the live test was enabled.'
}
if ($testExit -ne 0) { throw "Live measurement failed. Inspect $runDirectory/maven.log" }
