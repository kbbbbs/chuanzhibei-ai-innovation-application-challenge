param(
    [Parameter(Mandatory = $true)][string]$Build,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [ValidateRange(1, 65535)][int]$Port = 8082
)
$ErrorActionPreference = 'Stop'
$build = (Resolve-Path -LiteralPath $Build).Path
$out = (Resolve-Path -LiteralPath $OutputDirectory).Path
foreach ($log in @('http-server.log', 'http-server-error.log')) {
    if (Test-Path -LiteralPath (Join-Path $out $log)) { throw "Output log already exists: $log" }
}
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.h2.Driver'
$env:SPRING_DATASOURCE_URL = 'jdbc:h2:mem:categoryhttp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1'
$env:SPRING_DATASOURCE_USERNAME = 'sa'
$env:SPRING_DATASOURCE_PASSWORD = ''
$env:SPRING_SQL_INIT_MODE = 'always'
$env:SPRING_SQL_INIT_SCHEMA_LOCATIONS = 'file:' + (Join-Path $build 'src\test\resources\test-schema.sql').Replace('\','/')
$env:JWT_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:RAG_STORAGE_PATH = Join-Path $build 'http-rag'
$env:RAG_INGESTION_WORKER_ENABLED = 'false'
$env:WEKNORA_ENABLED = 'false'
$env:SERVER_PORT = "$Port"
$env:SERVER_ADDRESS = '127.0.0.1'
$env:AI_OPENAI_API_KEY = 'test-only'
$env:SPRING_AI_OPENAI_API_KEY = 'test-only'
function Base64Url([byte[]]$bytes) { [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
function Token([int]$id, [string]$role) {
    $head = Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256"}'))
    $payload = @{sub=$role; userId=$id; roleCode=$role; exp=[DateTimeOffset]::UtcNow.AddMinutes(15).ToUnixTimeSeconds()} | ConvertTo-Json -Compress
    $body = Base64Url ([Text.Encoding]::UTF8.GetBytes($payload))
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($env:JWT_SECRET))
    try { return "$head.$body.$(Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$head.$body"))))" } finally { $hmac.Dispose() }
}
function Request($method, $path, $token, $body = $null) {
    $args = @{Method=$method; Uri="http://127.0.0.1:${Port}$path"; SkipHttpErrorCheck=$true; TimeoutSec=10}
    if ($token) { $args.Headers = @{Authorization="Bearer $token"} }
    if ($null -ne $body) { $args.ContentType='application/json'; $args.Body=($body | ConvertTo-Json -Compress) }
    $r = Invoke-WebRequest @args
    $json = $r.Content | ConvertFrom-Json
    return @{status=[int]$r.StatusCode; json=$json}
}
function Check($condition, $message) {
    if (-not $condition) { throw "FAIL $message" }
    Write-Output "PASS $message"
}
$process = $null
try {
    $jar = Join-Path $build 'target\Final_exam_question_bank-0.0.1-SNAPSHOT.jar'
    $process = Start-Process -FilePath 'java.exe' -ArgumentList @('-jar', "`"$jar`"") -WorkingDirectory $build -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $out 'http-server.log') -RedirectStandardError (Join-Path $out 'http-server-error.log')
    $ready = $false
    for ($i=0; $i -lt 90; $i++) {
        if ($process.HasExited) { throw "Server exited with code $($process.ExitCode)" }
        try { $probe = Request GET '/categories' $null; $ready = $true; break } catch { Start-Sleep -Seconds 1 }
    }
    Check $ready "isolated server started on 127.0.0.1:$Port"
    $admin = Token 1 'admin'; $teacher = Token 2 'teacher'; $student = Token 3 'student'
    $create = Request POST '/categories' $admin @{name='HTTP security synthetic'; description='isolated test'; sortOrder=88}
    Check ($create.status -eq 200 -and $create.json.code -eq '200') 'admin creates synthetic category'
    $list = Request GET '/categories' $admin
    $cat = @($list.json.data | Where-Object name -eq 'HTTP security synthetic')
    Check ($cat.Count -eq 1) 'admin reads synthetic category'
    $id = $cat[0].id
    $course = Request POST '/courses' $admin @{title='HTTP security synthetic course'; categoryId=$id; teacherId=2; teacher='test'; difficulty='beginner'; status=1}
    Check ($course.status -eq 200 -and $course.json.code -eq '200') 'admin creates synthetic course'
    foreach ($entry in @(@('student',$student),@('teacher',$teacher),@('anonymous',$null))) {
        $role=$entry[0]; $token=$entry[1]; $expected=if ($role -eq 'anonymous') {401} else {403}
        foreach ($action in @('create','update','delete','cascade')) {
            $r = switch ($action) {
                'create' { Request POST '/categories' $token @{name="forbidden $role"} }
                'update' { Request PUT "/categories/$id" $token @{name='unauthorized edit'} }
                'delete' { Request DELETE "/categories/$id" $token }
                'cascade' { Request DELETE "/categories/${id}?cascade=true" $token }
            }
            Check ($r.status -eq $expected) "$role $action returns $expected"
        }
        $verify = Request GET '/categories' $admin
        $unchanged = @($verify.json.data | Where-Object { $_.id -eq $id -and $_.name -eq 'HTTP security synthetic' })
        $courses = Request GET "/categories/$id/courses" $admin
        $found = @($courses.json.data.records | Where-Object title -eq 'HTTP security synthetic course')
        Check ($unchanged.Count -eq 1 -and $found.Count -eq 1) "$role attempts leave category and course intact"
    }
    $update = Request PUT "/categories/$id" $admin @{name='HTTP security synthetic updated'}
    Check ($update.status -eq 200 -and $update.json.code -eq '200') 'admin updates category'
    $delete = Request DELETE "/categories/${id}?cascade=true" $admin
    Check ($delete.status -eq 200 -and $delete.json.code -eq '200' -and $delete.json.data.deletedCourses -eq 1) 'admin cascade deletes synthetic category and course'
    $verify = Request GET '/categories' $admin
    Check (@($verify.json.data | Where-Object id -eq $id).Count -eq 0) 'admin confirms category removed'
    $create = Request POST '/categories' $admin @{name='HTTP security normal delete'}
    $list = Request GET '/categories' $admin
    $normal = @($list.json.data | Where-Object name -eq 'HTTP security normal delete')
    Check ($create.json.code -eq '200' -and $normal.Count -eq 1) 'admin creates normal-delete category'
    $delete = Request DELETE "/categories/$($normal[0].id)" $admin
    Check ($delete.status -eq 200 -and $delete.json.code -eq '200') 'admin normal delete succeeds'
} finally {
    if ($null -ne $process -and -not $process.HasExited) { Stop-Process -Id $process.Id; $process.WaitForExit() }
    Remove-Item Env:JWT_SECRET -ErrorAction SilentlyContinue
}
