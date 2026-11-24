. "$PSScriptRoot\Common.ps1"
$scripts = 'C:\RSPS\work\claude-scripts'
$bash = 'C:\Program Files\Git\bin\bash.exe'
$server = $null
$owned = $false
$ready = $false
function Invoke-Rb([string[]]$Arguments) {
    & $bash lib/rb_server.sh @Arguments
    if ($LASTEXITCODE -ne 0) { throw "rb_server $($Arguments -join ' ') exit $LASTEXITCODE" }
}
function Wait-Log([string]$Path, [string]$Pattern, [int]$Seconds) {
    # One bounded file-event monitor, no sleep/polling loop or inherited output pipe.
    $watch = New-Object IO.FileSystemWatcher (Split-Path $Path), (Split-Path $Path -Leaf)
    $watch.NotifyFilter = [IO.NotifyFilters]'LastWrite,Size,FileName'
    $watch.EnableRaisingEvents = $true
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    try {
        do {
            $text = if (Test-Path -LiteralPath $Path) { [IO.File]::ReadAllText($Path) } else { '' }
            if ($text -match '(?m)\bERROR\b|Exception') { throw "Boot log contains ERROR/Exception: $Path" }
            if ($text -match $Pattern) { return $Matches[0] }
            if ($server.HasExited) { throw "Boot process exited: $($server.ExitCode)" }
            $remaining = [int]($deadline - [DateTime]::UtcNow).TotalMilliseconds
            if ($remaining -le 0) { throw "Boot log timeout: $Path" }
            $change = $watch.WaitForChanged([IO.WatcherChangeTypes]::All, $remaining)
            if ($change.TimedOut) { throw "Boot log timeout: $Path" }
        } while ($true)
    } finally { $watch.Dispose() }
}
try {
    $built = Get-Content -LiteralPath 'build\live-round-build.json' -Raw | ConvertFrom-Json
    if ($built.token -ne $env:RSPS_LIVE_TOKEN -or $built.head -ne $env:RSPS_LIVE_EXPECTED_HEAD) { throw 'No matching build proof' }
    Set-Location -LiteralPath $scripts
    # Refuse an existing server/tunnel; never stop another agent's instance.
    $port = New-Object Net.Sockets.TcpClient
    try {
        $connection = $port.BeginConnect('127.0.0.1', 50317, $null, $null)
        if ($connection.AsyncWaitHandle.WaitOne(1000)) {
            try { $port.EndConnect($connection); throw 'rb command port 50317 already in use; refusing bootcheck' } catch [Net.Sockets.SocketException] { }
        }
    } finally { $port.Dispose() }
    Invoke-Rb @('prep')
    $tag = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $out = "C:\RSPS\launcher-logs\live-boot-$tag.out.log"
    $err = "C:\RSPS\launcher-logs\live-boot-$tag.err.log"
    $server = Start-Process $bash -ArgumentList @('lib/rb_server.sh', 'run') -WorkingDirectory $scripts -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    $null = $server.Handle
    $owned = $true
    $line = Wait-Log $out 'RB-SERVER START log=[^\r\n]+' 30
    $log = ($line -replace '^RB-SERVER START log=', '') -replace '^/c/', 'C:/'
    Write-Output "BOOTCHECK serverlog=$log runner=$out"
    Wait-Log $log 'Now listening for incoming connections on port 50215' 180 | Write-Output
    $ready = $true
    $answer = & $bash lib/rb_server.sh tl cmd online
    if ($LASTEXITCODE -ne 0 -or "$answer" -notmatch '(?i)online|players') { throw "Boot command check failed: $answer" }
    Write-Output "BOOTCHECK online: $answer"
    # No gameplay account/GUI; the targeted build tests cover the changed rules.
    if ((Get-Content -LiteralPath $log -Raw) -match '(?m)\bERROR\b|Exception') { throw "Boot log contains ERROR/Exception: $log" }
    Assert-LivePhase
    @{ token=$env:RSPS_LIVE_TOKEN; head=$env:RSPS_LIVE_EXPECTED_HEAD; log=$log } | ConvertTo-Json | Set-Content -LiteralPath 'build\live-round-boot.json' -Encoding UTF8
} catch { Write-Output "BOOTCHECK STOP: $($_.Exception.Message)"; exit 1 } finally {
    if ($owned) {
        Set-Location -LiteralPath $scripts
        try {
            if ($ready) {
                # Use OUR tunnel to the server whose successful bind was just proved.
                # The generic rb_server stop kills by shared classpath, so do not use it.
                Invoke-Rb -Arguments @('tl', 'cmd', 'shutdown')
                if (!$server.WaitForExit(30000)) { throw 'Own boot server did not stop after shutdown; STOP and retain slot' }
                Write-Output 'BOOTCHECK own server stopped'
            }
        } finally {
            if ($server -and !$server.HasExited) {
                & taskkill.exe /PID $server.Id /T /F | Out-Null
                if ($LASTEXITCODE -ne 0) { throw 'Could not terminate own boot runner; recovery marker required' }
                $server.WaitForExit()
            }
            if ($server) { $server.Dispose() }
        }
    }
}
Write-Output 'LIVE BOOTCHECK OK exit 0'
exit 0
