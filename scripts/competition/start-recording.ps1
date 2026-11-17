param(
    [switch]$SkipBuild,
    [string]$JavaRuntimeHome = $env:JAVA_HOME,
    [ValidateRange(1024, 65535)][int]$Port = 18049
)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backendRoot = Join-Path $projectRoot 'ai-learning-backend'

# Import missing model-role settings without printing their private values.
foreach ($modelVariable in @(
        'AI_GENERATION_BASE_URL','AI_GENERATION_MODEL','AI_GENERATION_API_KEY','AI_GENERATION_TIMEOUT_SECONDS',
        'AI_PLANNING_BASE_URL','AI_PLANNING_MODEL','AI_PLANNING_API_KEY','AI_PLANNING_TIMEOUT_SECONDS',
        'AI_OPENAI_BASE_URL','AI_OPENAI_MODEL','AI_OPENAI_API_KEY','BASE_URL','API_KEY',
        'SILICONFLOW_API_KEY','RAG_EMBEDDING_ENABLED','RAG_EMBEDDING_BASE_URL','RAG_EMBEDDING_MODEL',
        'RAG_EMBEDDING_API_KEY','RAG_EMBEDDING_APIKEY','RAG_EMBEDDING_TIMEOUT_SECONDS',
        'OCR_BASE_URL','OCR_MODEL','OCR_API_KEY','OCR_APIKEY','OCR_TIMEOUT_SECONDS')) {
    if (-not [Environment]::GetEnvironmentVariable($modelVariable, 'Process')) {
        $modelUserValue = [Environment]::GetEnvironmentVariable($modelVariable, 'User')
        if (-not [string]::IsNullOrWhiteSpace($modelUserValue)) {
            [Environment]::SetEnvironmentVariable($modelVariable, $modelUserValue, 'Process')
        }
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
if (-not $env:JWT_SECRET) { $env:JWT_SECRET = 'local-contest-recording-only-change-before-sharing-2026' }
if ($JavaRuntimeHome) { $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaRuntimeHome).Path }
$javaExecutable = if ($JavaRuntimeHome) { Join-Path $JavaRuntimeHome 'bin/java.exe' } else { 'java' }
$versionInfo = New-Object System.Diagnostics.ProcessStartInfo
$versionInfo.FileName = $javaExecutable
$versionInfo.Arguments = '-version'
$versionInfo.UseShellExecute = $false
$versionInfo.CreateNoWindow = $true
$versionInfo.RedirectStandardOutput = $true
$versionInfo.RedirectStandardError = $true
$versionProcess = [System.Diagnostics.Process]::Start($versionInfo)
try {
    if (-not $versionProcess.WaitForExit(10000)) { $versionProcess.Kill(); throw 'Java version check timed out.' }
    $versionText = $versionProcess.StandardOutput.ReadToEnd() + $versionProcess.StandardError.ReadToEnd()
    if ($versionProcess.ExitCode -ne 0 -or $versionText -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) {
        throw 'Java 21+ is required. Pass -JavaRuntimeHome with your JDK directory.'
    }
} finally { $versionProcess.Dispose() }

Push-Location $backendRoot
try {
    if (-not $SkipBuild) {
        & mvn '-DskipTests' compile dependency:build-classpath '-Dmdep.outputFile=target/contest-recording-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Recording backend compilation failed.' }
    }
    $classpathFile = Join-Path $backendRoot 'target/contest-recording-classpath.txt'
    if (-not (Test-Path -LiteralPath $classpathFile)) { throw 'Run this script once without -SkipBuild.' }
    $classpath = (Join-Path $backendRoot 'target/classes') + ';' + (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $databasePath = (Join-Path $backendRoot 'data/contest-recording/academy').Replace('\', '/')
    $storagePath = Join-Path $backendRoot 'data/contest-recording/documents'
    Write-Host "Recording backend: http://127.0.0.1:$Port"
    Write-Host 'Synthetic accounts: demo_teacher / demo_student, password Demo10039!'
    & $javaExecutable '-cp' $classpath 'com.cupk.aiacademy.AiAcademyApplication' `
        '--spring.profiles.active=contest-recording' '--server.address=127.0.0.1' "--server.port=$Port" `
        '--spring.datasource.driver-class-name=org.h2.Driver' `
        "--spring.datasource.url=jdbc:h2:file:$databasePath;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE" `
        '--spring.datasource.username=sa' '--spring.datasource.password=' "--rag.storage-path=$storagePath"
    if ($LASTEXITCODE -ne 0) { throw 'Recording backend exited; inspect startup diagnostics.' }
} finally { Pop-Location }
