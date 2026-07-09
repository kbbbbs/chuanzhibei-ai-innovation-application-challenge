[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiKey,

    [string]$BaseUrl = 'http://localhost:8090',

    [ValidateSet('Process', 'User')]
    [string]$Scope = 'User'
)

$ErrorActionPreference = 'Stop'

[Environment]::SetEnvironmentVariable('WEKNORA_BASE_URL', $BaseUrl, $Scope)
[Environment]::SetEnvironmentVariable('WEKNORA_API_KEY', $ApiKey, $Scope)

$masked = if ($ApiKey.Length -le 8) { '********' } else { $ApiKey.Substring(0, 4) + '...' + $ApiKey.Substring($ApiKey.Length - 4) }
Write-Host "Set WEKNORA_BASE_URL=$BaseUrl at $Scope scope."
Write-Host "Set WEKNORA_API_KEY=$masked at $Scope scope."
Write-Host 'Restart ai-learning-backend from a new terminal/IDE run configuration so it can read the updated environment.'
