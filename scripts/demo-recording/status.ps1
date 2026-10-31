param(
    [string]$HostName = $(if ($env:DB_HOST) { $env:DB_HOST } else { 'localhost' }),
    [int]$Port = $(if ($env:DB_PORT) { [int]$env:DB_PORT } else { 3306 }),
    [string]$Database = $(if ($env:DB_NAME) { $env:DB_NAME } else { 'ai_academy' }),
    [string]$Username = $(if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }),
    [string]$Password = $(if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { '111111' }),
    [string]$MySqlPath = $(if ($env:MYSQL_PATH) { $env:MYSQL_PATH } else { '' })
)

. (Join-Path $PSScriptRoot 'common.ps1')

$statusSql = Join-Path $PSScriptRoot 'status_demo_recording.sql'
Invoke-DemoSqlFile -SqlFile $statusSql -HostName $HostName -Port $Port -Database $Database `
    -Username $Username -Password $Password -MySqlPath $MySqlPath -TableOutput
