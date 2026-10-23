param(
    [string]$MySqlBin = '',
    [ValidateRange(1024, 65535)][int]$Port = 13307
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($MySqlBin)) {
    $MySqlBin = @('C:\Program Files\MySQL\MySQL Server 8.4\bin',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin') |
        Where-Object { Test-Path -LiteralPath (Join-Path $_ 'mysqld.exe') -PathType Leaf } |
        Select-Object -First 1
    if (!$MySqlBin) { throw 'Pass -MySqlBin with your MySQL 8.4 LTS bin directory.' }
}
$taskRoot = Split-Path -Parent $PSScriptRoot
$server = Join-Path $MySqlBin 'mysqld.exe'
$client = Join-Path $MySqlBin 'mysql.exe'
$admin = Join-Path $MySqlBin 'mysqladmin.exe'
foreach ($binary in @($server, $client, $admin)) {
    if (!(Test-Path -LiteralPath $binary -PathType Leaf)) { throw "Missing MySQL binary: $binary" }
}
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
try { $portProbe.Start() }
catch { throw "Port $Port is occupied. No existing server will be stopped or reused." }
finally { $portProbe.Stop() }
$runDirectory = Join-Path $taskRoot ('investment-tracker\build\mysql-tests\' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $runDirectory
$dataDirectory = Join-Path $runDirectory 'data'
$errorLog = Join-Path $runDirectory 'mysql.log'
$database = 'investment_test_p0'
$previousUrl = $env:MYSQL_TEST_URL
$previousUser = $env:MYSQL_TEST_USERNAME
$previousPassword = $env:MYSQL_TEST_PASSWORD
$serverProcess = $null
function Test-OwnsTestPort {
    # Windows mysqld may use a child server process. Its exact unique data path is the identity.
    if (!$serverProcess) { return $false }
    $serverDataPath = & $client --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root --skip-password --batch --raw --skip-column-names --execute='SELECT @@datadir' 2>$null
    return $LASTEXITCODE -eq 0 -and $serverDataPath -and
        [IO.Path]::GetFullPath($serverDataPath.Trim()).TrimEnd([char[]]'\/') -eq
        [IO.Path]::GetFullPath($dataDirectory).TrimEnd([char[]]'\/')
}
try {
    & $server --no-defaults --initialize-insecure "--basedir=$(Split-Path -Parent $MySqlBin)" "--datadir=$dataDirectory" "--log-error=$errorLog"
    if ($LASTEXITCODE -ne 0) { throw "Isolated MySQL initialization failed. See $errorLog" }
    # No service registration, no existing configuration/data directory, no remote interfaces.
    $serverArguments = @('--no-defaults', '--console', '--bind-address=127.0.0.1', "--port=$Port",
        '--mysqlx=0', '--skip-log-bin', "--datadir=`"$dataDirectory`"", "--log-error=`"$errorLog`"")
    $serverProcess = Start-Process -FilePath $server -ArgumentList $serverArguments -WindowStyle Hidden -PassThru
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        & $admin --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root --skip-password --silent ping 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Milliseconds 250
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0 -or !(Test-OwnsTestPort)) {
        throw "The ready server is not our isolated process. No database commands will run. See $errorLog"
    }
    & $client --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root --skip-password --execute="CREATE DATABASE $database CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    if ($LASTEXITCODE -ne 0) { throw 'Creating the isolated test database failed' }
    $env:MYSQL_TEST_URL = "jdbc:mysql://127.0.0.1:$Port/$database"
    $env:MYSQL_TEST_USERNAME = 'root'
    $env:MYSQL_TEST_PASSWORD = ''
    Push-Location (Join-Path $taskRoot 'investment-tracker')
    try {
        & .\gradlew.bat --no-daemon --console=plain mysqlTest
        if ($LASTEXITCODE -ne 0) { throw 'MySQL migration/concurrency tests failed' }
    } finally { Pop-Location }
} finally {
    if (Test-OwnsTestPort) {
        & $admin --no-defaults --protocol=tcp --host=127.0.0.1 "--port=$Port" --user=root --skip-password shutdown
        $serverProcess.WaitForExit(10000) | Out-Null
        if (!$serverProcess.HasExited) { $serverProcess.Kill() }
    } elseif ($serverProcess -and !$serverProcess.HasExited) {
        $serverProcess.Kill()
    }
    $env:MYSQL_TEST_URL = $previousUrl
    $env:MYSQL_TEST_USERNAME = $previousUser
    $env:MYSQL_TEST_PASSWORD = $previousPassword
    Write-Host "Only the disposable server was stopped. Test data/logs remain in $runDirectory"
}
