[CmdletBinding()]
param(
    [string]$BaseUrl = 'http://localhost:8090',
    [string]$ApiKey = $env:WEKNORA_API_KEY,
    [string]$KnowledgeBaseId,
    [string]$Query = 'AI 智学平台课程资料',
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\..\.omx\WeKnora\.env'),
    [switch]$SkipEmbeddingCheck
)

$ErrorActionPreference = 'Stop'

function Read-DotEnv {
    param([Parameter(Mandatory = $true)][string]$Path)
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') {
            $values[$matches[1].Trim()] = $matches[2].Trim()
        }
    }
    return $values
}

if (-not $SkipEmbeddingCheck) {
    $modelEnv = Read-DotEnv -Path $EnvFile
    $embeddingBaseUrl = $modelEnv['EMBEDDING_BASE_URL']
    $embeddingApiKey = $modelEnv['EMBEDDING_API_KEY']
    $embeddingModel = $modelEnv['EMBEDDING_MODEL_NAME']
    if ($embeddingBaseUrl -and $embeddingApiKey -and $embeddingApiKey -ne 'replace-me' -and $embeddingModel) {
        $headers = @{ Authorization = "Bearer $embeddingApiKey" }
        $body = @{
            model = $embeddingModel
            input = @('WeKnora Agent RAG connectivity check')
            encoding_format = 'float'
        } | ConvertTo-Json -Depth 4
        $embeddingResponse = Invoke-RestMethod `
            -Uri "$($embeddingBaseUrl.TrimEnd('/'))/embeddings" `
            -Method Post `
            -Headers $headers `
            -UserAgent 'WeKnora-Agent-RAG-HealthCheck/1.0' `
            -ContentType 'application/json' `
            -Body $body `
            -TimeoutSec 30
        $dimension = @($embeddingResponse.data[0].embedding).Count
        if ($dimension -le 0) {
            throw 'The remote embedding endpoint returned no vector.'
        }
        Write-Host "Remote embedding OK: model=$embeddingModel, dimension=$dimension"
    } else {
        Write-Warning 'Remote embedding check skipped because the model Base URL, model name, or API key is missing.'
    }
}

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
$knowledgeBaseItems = @($knowledgeBases.data)
Write-Host "Knowledge base API OK: count=$($knowledgeBaseItems.Count)"
$knowledgeBaseItems | Select-Object -First 10 | ForEach-Object {
    Write-Host "  KB id=$($_.id), name=$($_.name), knowledgeCount=$($_.knowledge_count), embeddingModel=$($_.embedding_model_id)"
}

$sessionsUrl = "$BaseUrl/api/v1/sessions"
Write-Host "Checking $sessionsUrl"
$sessions = Invoke-RestMethod -Uri $sessionsUrl -Method Get -Headers $headers -TimeoutSec 20
$sessionItems = if ($sessions.data.items) { @($sessions.data.items) } else { @($sessions.data) }
Write-Host "Session API OK: count=$($sessionItems.Count)"

if (-not $KnowledgeBaseId) {
    Write-Warning 'KnowledgeBaseId was not supplied. Authenticated search verification was skipped.'
    return
}

$searchUrl = "$BaseUrl/api/v1/knowledge-search"
$searchBody = @{
    query = $Query
    knowledge_base_ids = @($KnowledgeBaseId)
    limit = 5
} | ConvertTo-Json -Depth 4
Write-Host "Checking Agent RAG search against knowledge base $KnowledgeBaseId"
$searchResult = Invoke-RestMethod `
    -Uri $searchUrl `
    -Method Post `
    -Headers $headers `
    -ContentType 'application/json' `
    -Body $searchBody `
    -TimeoutSec 60
$searchItems = @($searchResult.data)
if ($searchItems.Count -eq 0) {
    throw 'Knowledge search completed successfully but returned no snippets.'
}
Write-Host "Knowledge search API OK: snippets=$($searchItems.Count)"
$searchItems | Select-Object -First 5 | ForEach-Object {
    $preview = [string]$_.content
    $preview = ($preview -replace '\s+', ' ').Trim()
    if ($preview.Length -gt 120) {
        $preview = $preview.Substring(0, 120) + '...'
    }
    Write-Host "  score=$($_.score), title=$($_.knowledge_title), preview=$preview"
}
