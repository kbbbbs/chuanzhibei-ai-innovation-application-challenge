param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'ai_academy' }),
    [string]$Username = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }),
    [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '111111' }),
    [string]$MySqlPath = $(if ($env:MYSQL_PATH) { $env:MYSQL_PATH } else { '' })
)

. (Join-Path $PSScriptRoot 'common.ps1')

$repoRoot = Get-DemoRepoRoot
$applySql = Join-Path $repoRoot 'database\demo_recording.sql'
$statusSql = Join-Path $PSScriptRoot 'status_demo_recording.sql'

Write-Host "正在写入录屏演示数据：$Username@$HostName`:$Port/$Database"
Invoke-DemoSqlFile -SqlFile $applySql -HostName $HostName -Port $Port -Database $Database `
    -Username $Username -Password $Password -MySqlPath $MySqlPath

Write-Host '写入完成，开始核验：'
Invoke-DemoSqlFile -SqlFile $statusSql -HostName $HostName -Port $Port -Database $Database `
    -Username $Username -Password $Password -MySqlPath $MySqlPath -TableOutput
