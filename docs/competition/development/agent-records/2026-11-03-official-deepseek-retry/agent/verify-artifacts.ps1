$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../../../..')).Path
$privateFiles = @((Join-Path $repo '.env.local'), (Join-Path $repo '.codex_tmp/official-deepseek-retry/redaction.env'))
$known = @()
foreach ($file in $privateFiles) {
    foreach ($line in Get-Content -LiteralPath $file) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*(?:KEY|TOKEN|SECRET|PASSWORD))\s*=(.*)$') {
            $value = $Matches[2].Trim().Trim('"').Trim("'")
            if ($value.Length -ge 8) { $known += $value }
        }
    }
}
$source = [IO.File]::ReadAllText((Join-Path (Split-Path $repo) '新建 文本文档.txt'))
$known += @([regex]::Matches($source, '\bsk-[A-Za-z0-9_-]+\b') | ForEach-Object Value)
$files = @(Get-ChildItem -LiteralPath (Split-Path $PSScriptRoot) -File -Recurse | Where-Object Name -ne 'artifact-verification.json')
$leakFiles = @()
foreach ($file in $files) {
    $text = [IO.File]::ReadAllText($file.FullName)
    if (@($known | Where-Object { $text.Contains($_) }).Count -gt 0) { $leakFiles += $file.Name }
}
$manifest = @($files | Where-Object { $_.DirectoryName -eq $PSScriptRoot } | ForEach-Object {
    @{file=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash}
})
$result = @{timestamp=(Get-Date -Format o);knownSecretLiteralScanPassed=($leakFiles.Count -eq 0);checkedFiles=$files.Count;leakFileNames=$leakFiles;manifest=$manifest}
$result | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'artifact-verification.json')
[pscustomobject]@{knownSecretLiteralScanPassed=$result.knownSecretLiteralScanPassed;checkedFiles=$files.Count;hashedFiles=$manifest.Count} | ConvertTo-Json
if ($leakFiles.Count -gt 0) { throw 'Known secret literal found in public artifacts' }
