param([switch]$UseMySql, [switch]$SkipBuild, [string]$JavaRuntimeHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
# A running desktop may predate newly saved User environment variables.
# Preserve explicit process settings; import only the model role variables that are missing.
foreach ($modelVariable in @('SILICONFLOW_API_KEY',
        'RAG_EMBEDDING_ENABLED','RAG_EMBEDDING_BASE_URL','RAG_EMBEDDING_MODEL','RAG_EMBEDDING_API_KEY','RAG_EMBEDDING_APIKEY','RAG_EMBEDDING_TIMEOUT_SECONDS',
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
if (-not $env:JWT_SECRET) {
    if ($UseMySql) { throw 'Set a private JWT_SECRET (at least 32 bytes) before using the MySQL profile.' }
    $env:JWT_SECRET = 'local-contest-demo-only-change-before-sharing-2026'
}
$backendRoot = Join-Path $projectRoot 'ai-learning-backend'
$javaExecutable = if ($JavaRuntimeHome) { Join-Path $JavaRuntimeHome 'bin/java.exe' } else { 'java' }
# Java writes its version to stderr even on success. Windows PowerShell 5 with
# ErrorActionPreference=Stop otherwise treats that output as a NativeCommandError.
$versionStart = New-Object System.Diagnostics.ProcessStartInfo
$versionStart.FileName = $javaExecutable
$versionStart.Arguments = '-version'
$versionStart.UseShellExecute = $false
$versionStart.CreateNoWindow = $true
$versionStart.RedirectStandardOutput = $true
$versionStart.RedirectStandardError = $true
$versionProcess = [System.Diagnostics.Process]::Start($versionStart)
try {
    if (-not $versionProcess.WaitForExit(10000)) {
        $versionProcess.Kill()
        throw 'Java version check timed out.'
    }
    $versionText = $versionProcess.StandardOutput.ReadToEnd() + $versionProcess.StandardError.ReadToEnd()
    if ($versionProcess.ExitCode -ne 0) { throw 'Java version check failed.' }
} finally { $versionProcess.Dispose() }
if ($versionText -notmatch 'version "(\d+)' -or [int]$Matches[1] -lt 21) {
    throw 'Java 21+ is required. Set JAVA_HOME or pass -JavaRuntimeHome with your JDK folder.'
}
if ($JavaRuntimeHome) {
    # Maven also selects its compiler/runtime through JAVA_HOME.
    $env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaRuntimeHome).Path
}
Push-Location $backendRoot
try {
    if (-not $SkipBuild) {
        & mvn '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    }
    $jar = Join-Path $backendRoot 'target/Final_exam_question_bank-0.0.1-SNAPSHOT.jar'
    if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the backend first.' }
    $arguments = @('-jar', $jar, '--server.address=127.0.0.1')
    if (-not $UseMySql) { $arguments += '--spring.profiles.active=contest-demo' }
    Write-Host 'Backend: http://127.0.0.1:8080. Start the frontend in another terminal: npm run dev.'
    & $javaExecutable @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Backend exited with an error; inspect the startup diagnostics.' }
} finally { Pop-Location }
