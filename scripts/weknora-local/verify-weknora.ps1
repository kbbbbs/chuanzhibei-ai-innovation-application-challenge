[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8090',
    [string]$ApiKey = $env:WEKNORA_API_KEY
)

$ErrorActionPreference = 'Stop'

$healthUrl = "$BaseUrl/health"
Write-Host "Checking $healthUrl"
$health = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 10
Write-Host "Health OK: $($health | ConvertTo-Json -Compress)"

if (-not $ApiKey) {
    Write-Warning 'WEKNORA_API_KEY is empty. Health is OK, but authenticated API checks were skipped.'
    return
}

$headers = @{ 'X-API-Key' = $ApiKey }
$kbUrl = "$BaseUrl/api/v1/knowledge-bases"
Write-Host "Checking $kbUrl"
$knowledgeBases = Invoke-RestMethod -Uri $kbUrl -Method Get -Headers $headers -TimeoutSec 20
Write-Host "Knowledge base API OK: $($knowledgeBases | ConvertTo-Json -Depth 6 -Compress)"
