Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Resolve-DemoMySqlClient {
    param([string]$MySqlPath)

    $candidates = @()
    if (-not [string]::IsNullOrWhiteSpace($MySqlPath)) {
        $candidates += $MySqlPath
    }

    $command = Get-Command mysql -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        $candidates += $command.Source
    }

    $candidates += @(
        'D:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe'
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw '未找到 mysql.exe。请安装 MySQL Client，或通过 -MySqlPath / MYSQL_PATH 指定完整路径。'
}

function Invoke-DemoSqlFile {
    param(
        [Parameter(Mandatory = $true)][string]$SqlFile,
        [Parameter(Mandatory = $true)][string]$HostName,
        [Parameter(Mandatory = $true)][int]$Port,
        [Parameter(Mandatory = $true)][string]$Database,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password,
        [string]$MySqlPath,
        [switch]$TableOutput
    )

    if (-not (Test-Path -LiteralPath $SqlFile)) {
        throw "SQL 文件不存在：$SqlFile"
    }

    $client = Resolve-DemoMySqlClient -MySqlPath $MySqlPath
    $resolvedSql = (Resolve-Path -LiteralPath $SqlFile).Path.Replace('\', '/')
    $arguments = @(
        '--protocol=tcp',
        "--host=$HostName",
        "--port=$Port",
        "--user=$Username",
        "--database=$Database",
        '--default-character-set=utf8mb4',
        '--show-warnings'
    )
    if ($TableOutput) {
        $arguments += '--table'
    }
    $arguments += "--execute=source $resolvedSql"

    $hadOldPassword = Test-Path Env:MYSQL_PWD
    $oldPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        & $client @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "mysql 执行失败，退出码：$LASTEXITCODE，SQL：$SqlFile"
        }
    }
    finally {
        if ($hadOldPassword) {
            $env:MYSQL_PWD = $oldPassword
        }
        else {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        }
    }
}

function Get-DemoRepoRoot {
    return (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
}
