param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPdf
)

$ErrorActionPreference = 'Stop'

function Invoke-WithRetry {
    param(
        [Parameter(Mandatory = $true)]
        [scriptblock]$Action,
        [int]$Attempts = 20,
        [int]$DelayMilliseconds = 750
    )

    $lastError = $null
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            return & $Action
        }
        catch {
            $lastError = $_
            if ($attempt -eq $Attempts) {
                throw
            }
            Start-Sleep -Milliseconds $DelayMilliseconds
        }
    }

    throw $lastError
}

$existingWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id)
$word = $null
$document = $null
$createdWordPids = @()

try {
    $word = New-Object -ComObject Word.Application
    Start-Sleep -Seconds 2
    $createdWordPids = @(Get-Process WINWORD -ErrorAction SilentlyContinue |
        Where-Object { $_.Id -notin $existingWordPids } |
        Select-Object -ExpandProperty Id)

    Invoke-WithRetry { $word.Visible = $false } | Out-Null
    Invoke-WithRetry { $word.DisplayAlerts = 0 } | Out-Null
    $document = Invoke-WithRetry { $word.Documents.Open($InputPath, $false, $false, $false) }
    Start-Sleep -Seconds 2
    try {
        Invoke-WithRetry { $document.SaveAs2($OutputPdf, 17) } -Attempts 20 | Out-Null
    }
    catch {
        Invoke-WithRetry { $document.ExportAsFixedFormat($OutputPdf, 17) } -Attempts 20 | Out-Null
    }

    if (-not (Test-Path -LiteralPath $OutputPdf)) {
        throw "Word did not create the requested PDF: $OutputPdf"
    }
}
finally {
    if ($null -ne $document) {
        try {
            Invoke-WithRetry { $document.Close($false) } -Attempts 10 | Out-Null
        }
        catch {
            Write-Warning "Unable to close the isolated document cleanly: $($_.Exception.Message)"
        }
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($document) | Out-Null
    }

    if ($null -ne $word) {
        try {
            Invoke-WithRetry { $word.Quit() } -Attempts 10 | Out-Null
        }
        catch {
            Write-Warning "Unable to quit the isolated Word instance cleanly: $($_.Exception.Message)"
        }
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    }

    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()

    foreach ($pidToStop in $createdWordPids) {
        $process = Get-Process -Id $pidToStop -ErrorAction SilentlyContinue
        if ($null -ne $process -and [string]::IsNullOrWhiteSpace($process.MainWindowTitle)) {
            Stop-Process -Id $process.Id -Force
        }
    }
}

Get-Item -LiteralPath $OutputPdf | Select-Object FullName, Length, LastWriteTime
