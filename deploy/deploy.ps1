param(
    [ValidateSet('init', 'up', 'down', 'status')]
    [string]$Action = 'up',
    [switch]$ExternalDatabase
)
$ErrorActionPreference = 'Stop'
$repository = Split-Path $PSScriptRoot -Parent
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    $desktopCli = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (-not (Test-Path -LiteralPath $desktopCli)) { throw 'Docker CLI is unavailable.' }
    $dockerExecutable = $desktopCli
} else {
    $dockerExecutable = $dockerCommand.Source
}
if ($Action -eq 'init') {
    $secretDirectory = Join-Path $PSScriptRoot 'secrets'
    New-Item -ItemType Directory -Path $secretDirectory -Force | Out-Null
    $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $rule = New-Object System.Security.AccessControl.FileSystemAccessRule(
        $identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'
    )
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $secretDirectory -AclObject $acl
    foreach ($name in @('managed-db-password', 'mysql-root-password')) {
        $secretFile = Join-Path $secretDirectory $name
        if (-not (Test-Path -LiteralPath $secretFile)) {
            $random = New-Object byte[] 48
            $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
            try { $generator.GetBytes($random) } finally { $generator.Dispose() }
            [System.IO.File]::WriteAllText(
                $secretFile, [Convert]::ToBase64String($random),
                (New-Object System.Text.UTF8Encoding($false))
            )
        }
    }
    Write-Output 'Database secret files are ready. Existing files were preserved.'
    exit 0
}
$composeArguments = @('compose', '--project-directory', $repository, '-f', (Join-Path $repository 'compose.yaml'))
if (-not $ExternalDatabase) {
    $composeArguments += @('-f', (Join-Path $repository 'compose.mysql.yaml'))
}
switch ($Action) {
    'up' { $composeArguments += @('up', '-d', '--build') }
    'down' { $composeArguments += 'down' }
    'status' { $composeArguments += 'ps' }
}
& $dockerExecutable @composeArguments
exit $LASTEXITCODE
