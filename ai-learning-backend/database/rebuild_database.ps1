# AI 智学平台 - 一键重建数据库脚本
# 路径: d:\computer\ai_lenaring\ai-learning\ai-learning-backend\database\rebuild_database.ps1
# 用法: 右键 → 使用 PowerShell 运行，或在 PowerShell 中执行: .\rebuild_database.ps1

# 配置参数
$mysqlHost = "127.0.0.1"
$mysqlPort = "3306"
$mysqlUser = "root"
$mysqlPassword = "[REDACTED]"  # 请根据实际情况修改密码
$databaseName = "ai_academy"
$sqlFilePath = "d:\computer\ai_lenaring\ai-learning\ai-learning-backend\database\ai_academy.sql"

# 颜色输出函数
function Write-ColorOutput($ForegroundColor) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    if ($args) { Write-Output $args }
    $host.UI.RawUI.ForegroundColor = $fc
}

Write-ColorOutput Green "=========================================="
Write-ColorOutput Green "  AI 智学平台 - 数据库一键重建工具"
Write-ColorOutput Green "=========================================="
Write-Output ""

# 检查 MySQL 是否可用
try {
    $mysqlVersion = mysql --version 2>$null
    if ($LASTEXITCODE -ne 0) {
        Write-ColorOutput Red "[错误] 未找到 MySQL 命令，请确保 MySQL 已安装并添加到环境变量 PATH 中"
        exit 1
    }
    Write-ColorOutput Green "[OK] MySQL 已安装: $mysqlVersion"
} catch {
    Write-ColorOutput Red "[错误] 未找到 MySQL 命令，请确保 MySQL 已安装并添加到环境变量 PATH 中"
    exit 1
}

# 检查 SQL 文件是否存在
if (-not (Test-Path $sqlFilePath)) {
    Write-ColorOutput Red "[错误] SQL 文件不存在: $sqlFilePath"
    exit 1
}
Write-ColorOutput Green "[OK] SQL 文件存在: $sqlFilePath"

# 检查 MySQL 服务是否运行
try {
    $service = Get-Service -Name "MySQL80" -ErrorAction SilentlyContinue
    if ($service -and $service.Status -eq "Running") {
        Write-ColorOutput Green "[OK] MySQL 服务正在运行"
    } else {
        Write-ColorOutput Yellow "[警告] MySQL 服务未运行，尝试启动..."
        Start-Service -Name "MySQL80" -ErrorAction Stop
        Write-ColorOutput Green "[OK] MySQL 服务已启动"
    }
} catch {
    Write-ColorOutput Yellow "[警告] 无法检查/启动 MySQL 服务，继续尝试连接..."
}

Write-Output ""
Write-ColorOutput Yellow "[提示] 此操作将删除并重建数据库 '$databaseName'，所有数据将被清空！"
Write-ColorOutput Yellow "[提示] 按 Enter 键继续，或按 Ctrl+C 取消..."
Read-Host

Write-Output ""
Write-ColorOutput Cyan "[步骤 1/3] 删除旧数据库 (如果存在)..."
$dropResult = mysql -h $mysqlHost -P $mysqlPort -u $mysqlUser -p$mysqlPassword -e "DROP DATABASE IF EXISTS $databaseName;" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-ColorOutput Red "[错误] 删除数据库失败: $dropResult"
    exit 1
}
Write-ColorOutput Green "[OK] 旧数据库已删除"

Write-Output ""
Write-ColorOutput Cyan "[步骤 2/3] 创建新数据库..."
$createResult = mysql -h $mysqlHost -P $mysqlPort -u $mysqlUser -p$mysqlPassword -e "CREATE DATABASE $databaseName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-ColorOutput Red "[错误] 创建数据库失败: $createResult"
    exit 1
}
Write-ColorOutput Green "[OK] 新数据库已创建"

Write-Output ""
Write-ColorOutput Cyan "[步骤 3/3] 导入 SQL 文件 (这可能需要几秒钟)..."
$importResult = mysql -h $mysqlHost -P $mysqlPort -u $mysqlUser -p$mysqlPassword $databaseName < $sqlFilePath 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-ColorOutput Red "[错误] 导入 SQL 失败: $importResult"
    exit 1
}
Write-ColorOutput Green "[OK] SQL 文件导入成功"

Write-Output ""
Write-ColorOutput Cyan "[验证] 检查数据库表..."
$tables = mysql -h $mysqlHost -P $mysqlPort -u $mysqlUser -p$mysqlPassword -e "USE $databaseName; SHOW TABLES;" 2>&1
if ($LASTEXITCODE -eq 0) {
    $tableCount = ($tables | Where-Object { $_ -match "^t_" }).Count
    Write-ColorOutput Green "[OK] 数据库重建完成！共 $tableCount 张表已创建"
    Write-Output ""
    Write-ColorOutput Green "数据库表列表:"
    $tables | Where-Object { $_ -match "^t_" } | ForEach-Object { Write-Output "  - $_" }
} else {
    Write-ColorOutput Yellow "[警告] 验证失败: $tables"
}

Write-Output ""
Write-ColorOutput Green "=========================================="
Write-ColorOutput Green "  数据库重建完成！"
Write-ColorOutput Green "=========================================="
Write-Output ""
Write-Output "现在可以启动后端服务了:"
Write-Output "  cd d:\computer\ai_lenaring\ai-learning\ai-learning-backend"
Write-Output "  mvn spring-boot:run"
Write-Output ""
Read-Host "按 Enter 键退出"
