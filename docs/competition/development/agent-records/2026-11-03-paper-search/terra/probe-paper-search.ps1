param([string]$BaseUrl = "http://localhost:8080", [string]$Keyword = "machine learning", [string]$OutputPath = (Join-Path $PSScriptRoot "original-query-results.json"))
$ErrorActionPreference = "Stop"
$readme = Get-Content (Join-Path $PSScriptRoot "../../../../../../README.md") -Raw -Encoding UTF8
$match = [regex]::Match($readme, 'student`[^`]*`([^`]+)`')
if (-not $match.Success) { throw "Could not locate demo credentials in README" }
$loginBody = @{ username = "student"; password = $match.Groups[1].Value } | ConvertTo-Json -Compress
$login = Invoke-RestMethod "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $loginBody
if (-not $login.data.token) { throw "Demo login failed" }
$headers = @{ Authorization = "Bearer $($login.data.token)" }
$results = @()
for ($i = 1; $i -le 1; $i++) {
    Start-Sleep -Seconds 3
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $httpStatus = $null
    $businessCode = $null
    $total = $null
    $papers = @()
    $paper = $null
    $classification = "transport_error"
    try {
        $encodedKeyword = [uri]::EscapeDataString($Keyword)
        $response = Invoke-WebRequest "$BaseUrl/api/paper/search?source=arxiv&keyword=$encodedKeyword&page=1&size=1" -Headers $headers -UseBasicParsing
        $httpStatus = [int]$response.StatusCode
        $body = $response.Content | ConvertFrom-Json
        $businessCode = [string]$body.code
        $total = $body.data.total
        $papers = @($body.data.data | Where-Object { $null -ne $_ })
        if ($papers.Count -gt 0) { $paper = $papers[0] }
        $validPaper = $total -gt 0 -and $papers.Count -eq 1 -and $paper.paperId -and
            $paper.paperId -notmatch "api/errors" -and $paper.title -and $paper.abstractText -and
            @($paper.authors).Count -gt 0 -and @($paper.authors | Where-Object { -not $_.name }).Count -eq 0
        if ($businessCode -eq "200" -and $validPaper) { $classification = "valid_paper" }
        elseif ($businessCode -eq "200") { $classification = "invalid_paper_response" }
        elseif ($body.message -match "timeout|timed out" -or $body.msg -match "timeout|timed out") { $classification = "timeout" }
        else { $classification = "business_error" }
    } catch {
        if ($_.Exception.Response) { $httpStatus = [int]$_.Exception.Response.StatusCode }
    } finally { $watch.Stop() }
    $results += [pscustomobject]@{
        timestamp = (Get-Date).ToString("o")
        source = "arxiv"
        keyword = $Keyword
        attempt = $i
        http_status = $httpStatus
        business_code = $businessCode
        classification = $classification
        elapsed_ms = $watch.ElapsedMilliseconds
        total = $total
        paper_count = $papers.Count
        paper_id = $paper.paperId
        title = $paper.title
        author_count = @($paper.authors | Where-Object { $null -ne $_ }).Count
        abstract_present = [bool]$paper.abstractText
    }
}
$results | ConvertTo-Json | Set-Content -LiteralPath $OutputPath -Encoding UTF8
$results | Format-Table -AutoSize
