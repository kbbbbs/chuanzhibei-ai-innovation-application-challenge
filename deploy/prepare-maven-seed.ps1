param(
    [string]$MavenRepository = (Join-Path $env:USERPROFILE '.m2\repository')
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$source = (Resolve-Path -LiteralPath $MavenRepository).Path
$destination = Join-Path $repository ('.codex_tmp\maven-seed-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$copied = 0
$totalBytes = 0L
foreach ($file in Get-ChildItem -LiteralPath $source -Recurse -File) {
    if ($file.Extension -notin @('.jar', '.pom')) { continue }
    $relative = $file.FullName.Substring($source.Length).TrimStart('\', '/')
    # Exclude this project's coordinates and common locally installed app groups.
    if ($relative -match '^(com[\\/]example|com[\\/]cupk)[\\/]' -or
        $relative -match '(ai-learning-backend|Final_exam_question_bank)') { continue }
    $target = Join-Path $destination $relative
    New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $file.FullName -Destination $target
    $copied++
    $totalBytes += $file.Length
}
$unexpected = Get-ChildItem -LiteralPath $destination -Recurse -File |
    Where-Object { $_.Extension -notin @('.jar', '.pom') }
if ($unexpected) { throw 'Seed contains unexpected file types.' }
[PSCustomObject]@{
    Context = $destination
    DependencyFiles = $copied
    Bytes = $totalBytes
    Excluded = 'settings, credentials, metadata, local product artifacts'
}
