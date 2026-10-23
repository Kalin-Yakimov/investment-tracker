[CmdletBinding(SupportsShouldProcess = $true)]
param (
    [ValidatePattern('^[A-Za-z0-9_]+$')]
    [string]$Database = 'investment',

    [ValidatePattern('^[A-Za-z0-9_.-]+$')]
    [string]$Username = 'investment_app',

    [ValidateSet('localhost', '127.0.0.1')]
    [string]$DatabaseHost = 'localhost',

    [ValidateRange(1, 65535)]
    [int]$Port = 3306,

    [string]$DestinationDirectory = (Join-Path (Split-Path $PSScriptRoot -Parent) 'backups\database'),

    [string]$MySqlDumpPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not $MySqlDumpPath) {
    $taskDumpCommand = Get-Command mysqldump.exe -ErrorAction SilentlyContinue
    if ($taskDumpCommand) {
        $MySqlDumpPath = $taskDumpCommand.Source
    } else {
        $MySqlDumpPath = Join-Path $env:ProgramFiles 'MySQL\MySQL Server 8.0\bin\mysqldump.exe'
    }
}

if (-not (Test-Path -LiteralPath $MySqlDumpPath -PathType Leaf)) {
    throw 'mysqldump.exe was not found. Install the MySQL client or supply -MySqlDumpPath.'
}

$taskDestination = [System.IO.Path]::GetFullPath($DestinationDirectory)
$taskStamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$taskSuffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
$taskBackupPath = Join-Path $taskDestination "${Database}_${taskStamp}_${taskSuffix}.sql"
$taskPartialPath = "$taskBackupPath.partial"

if (-not $PSCmdlet.ShouldProcess("$DatabaseHost`:$Port/$Database", "Create private backup at $taskBackupPath")) {
    return
}

New-Item -ItemType Directory -Path $taskDestination -Force | Out-Null
Write-Warning 'The SQL backup is not encrypted and contains private data. Keep it out of Git and shared folders.'
Write-Host 'Enter your MySQL password at the prompt. The script does not store or print it.'

# Let mysqldump prompt for the password. Do not put credentials in arguments,
# environment variables, a checked-in file or a PowerShell transcript.
# --result-file avoids the UTF-16 redirection used by Windows PowerShell 5.1.
$taskDumpArguments = @(
    '--protocol=TCP',
    "--host=$DatabaseHost",
    "--port=$Port",
    "--user=$Username",
    '--password',
    '--single-transaction',
    '--quick',
    '--routines',
    '--events',
    '--triggers',
    '--hex-blob',
    '--no-tablespaces',
    '--set-gtid-purged=OFF',
    '--column-statistics=0',
    '--default-character-set=utf8mb4',
    "--result-file=$taskPartialPath",
    $Database
)

& $MySqlDumpPath @taskDumpArguments
if ($LASTEXITCODE -ne 0) {
    throw "Backup failed. Do not restore the incomplete .partial file at $taskPartialPath. No database changes were made by this script."
}
if (-not (Test-Path -LiteralPath $taskPartialPath -PathType Leaf) -or
        (Get-Item -LiteralPath $taskPartialPath).Length -eq 0) {
    throw 'mysqldump did not produce a non-empty backup. Do not proceed with database changes.'
}

# Both exact paths were resolved within the destination above; no overwrite.
Move-Item -LiteralPath $taskPartialPath -Destination $taskBackupPath
$taskBackupFile = Get-Item -LiteralPath $taskBackupPath
[pscustomobject]@{
    BackupPath = $taskBackupPath
    Bytes = $taskBackupFile.Length
    SHA256 = (Get-FileHash -LiteralPath $taskBackupPath -Algorithm SHA256).Hash
    CreatedUtc = $taskBackupFile.LastWriteTimeUtc
}
Write-Host 'Verify the backup by restoring it into a separate database before changing the original database.'
