[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8090',
    [string]$Email,
    [string]$Username,
    [string]$KeyName = 'ai-learning-agent-rag'
)

$ErrorActionPreference = 'Stop'

if (-not $Email) {
    $Email = Read-Host 'WeKnora login email'
}
if (-not $Username) {
    $suggestedUsername = ($Email -split '@', 2)[0]
    $Username = Read-Host "WeKnora username [$suggestedUsername]"
    if (-not $Username) {
        $Username = $suggestedUsername
    }
}

$securePassword = Read-Host 'WeKnora password' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

$loginBody = @{ email = $Email; password = $password } | ConvertTo-Json
$login = $null
try {
    $login = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $loginBody `
        -TimeoutSec 30
} catch {
    Write-Host 'Login did not succeed; registering a new local WeKnora account.'
    $registerBody = @{
        username = $Username
        email = $Email
        password = $password
    } | ConvertTo-Json
    Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/auth/register" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $registerBody `
        -TimeoutSec 30 | Out-Null
    $login = Invoke-RestMethod `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -Method Post `
        -ContentType 'application/json' `
        -Body $loginBody `
        -TimeoutSec 30
}

$password = $null
if (-not $login.token -or -not $login.active_tenant.id) {
    throw 'WeKnora login response did not include a token and active tenant.'
}

$authHeaders = @{ Authorization = "Bearer $($login.token)" }
$keyBody = @{
    name = $KeyName
    full_access = $false
    capabilities = @('retrieve', 'ingest', 'manage_kbs', 'chat')
    knowledge_base_ids = @()
} | ConvertTo-Json -Depth 4
$keyResponse = Invoke-RestMethod `
    -Uri "$BaseUrl/api/v1/tenants/$($login.active_tenant.id)/api-keys" `
    -Method Post `
    -Headers $authHeaders `
    -ContentType 'application/json' `
    -Body $keyBody `
    -TimeoutSec 30

$apiKey = $keyResponse.data.token
if (-not $apiKey) {
    throw 'WeKnora did not return the newly created API key token.'
}

[Environment]::SetEnvironmentVariable('WEKNORA_BASE_URL', $BaseUrl, 'User')
[Environment]::SetEnvironmentVariable('WEKNORA_API_KEY', $apiKey, 'User')
$env:WEKNORA_BASE_URL = $BaseUrl
$env:WEKNORA_API_KEY = $apiKey

$masked = if ($apiKey.Length -le 8) {
    '********'
} else {
    $apiKey.Substring(0, 4) + '...' + $apiKey.Substring($apiKey.Length - 4)
}
Write-Host "Agent RAG API key created with retrieve, ingest, manage_kbs, and chat capabilities: $masked"
Write-Host "WEKNORA_BASE_URL=$BaseUrl and WEKNORA_API_KEY were saved at Windows User scope."
Write-Host 'Restart ai-learning-backend from a new terminal or IDE process before testing Agent RAG.'
