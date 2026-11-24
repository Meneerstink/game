[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Repository,
    [Parameter(Mandatory=$true)][string]$ExpectedHead,
    [Parameter(Mandatory=$true)][string]$SourceRef,
    [string]$SlotPath = 'C:\RSPS\team\LIVE-SLOT.txt',
    [string]$Branch = 'project48',
    [string]$BuildScript = "$PSScriptRoot\live\Build.ps1",
    [string]$BootcheckScript = "$PSScriptRoot\live\Bootcheck.ps1",
    [string]$ReleaseScript = "$PSScriptRoot\live\Release.ps1",
    [string]$Remote = 'audit',
    [string]$Dossier = 'C:\RSPS\team\dossiers\REST-ONDERZOEK-2411.txt',
    [string]$LogDirectory = 'C:\RSPS\launcher-logs',
    [ValidateSet('ChatGPT-A','ChatGPT-B')][string]$Agent = 'ChatGPT-A',
    [switch]$DryRun
)
# One foreground process owns the lease through merge/build/bootcheck/release/push.
# A failed/interrupted round leaves a recovery marker. Never take over by age.
# Phase scripts must finish synchronously, check their own native exits, and not
# detach children. They receive RSPS_LIVE_TOKEN and RSPS_LIVE_EXPECTED_HEAD.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$lease = $null
$completed = $false
$token = [Guid]::NewGuid().ToString('N')
$expected = $ExpectedHead
$merged = $false
$phase = 'claim'
$previousToken = $env:RSPS_LIVE_TOKEN
$previousHead = $env:RSPS_LIVE_EXPECTED_HEAD
$previousRepository = $env:RSPS_LIVE_REPOSITORY
$previousSlot = $env:RSPS_LIVE_SLOT
$round = Get-Date -Format 'yyyyMMdd-HHmmss-fff'

function Invoke-Native([string]$File, [string[]]$Arguments) {
    # All supplied native arguments are single-line and quoted using Windows argv rules.
    $quoted = foreach ($arg in $Arguments) {
        if ($arg -match '[\r\n\x00]') { throw 'Multiline native argument refused' }
        '"' + [regex]::Replace([regex]::Replace($arg, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
    }
    $psi = New-Object Diagnostics.ProcessStartInfo
    $psi.FileName = $File
    $psi.Arguments = $quoted -join ' '
    $psi.WorkingDirectory = $Repository
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $psi
    try {
        if (!$process.Start()) { throw "Cannot start $File" }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $out = $stdout.Result
        $err = $stderr.Result
        if ($err) { Write-Host $err.TrimEnd() }
        if ($process.ExitCode -ne 0) { throw "$File exit $($process.ExitCode): $out $err" }
        return $out.TrimEnd()
    } finally {
        # On uncertain child status the marker is retained by the outer finally.
        $process.Dispose()
    }
}
function Git([string[]]$Arguments) { Invoke-Native 'git.exe' $Arguments }
function Assert-State([string]$Phase) {
    if (!$lease -or !$lease.CanWrite) { throw "Lease lost before $Phase" }
    $lease.Position = 0
    $reader = New-Object IO.StreamReader($lease, [Text.Encoding]::UTF8, $true, 1024, $true)
    try { $owner = $reader.ReadToEnd() } finally { $reader.Dispose() }
    if ($owner -notmatch [regex]::Escape("token=$token")) { throw "Lease owner mismatch before $Phase" }
    if ((Git -Arguments @('symbolic-ref', '--short', 'HEAD')) -ne $Branch) { throw "Branch changed before $Phase" }
    if ((Git -Arguments @('rev-parse', 'HEAD')) -ne $expected) { throw "HEAD changed before $Phase" }
    if (Git -Arguments @('status', '--porcelain', '--untracked-files=no')) { throw "Dirty index/worktree before $Phase" }
    foreach ($name in @('MERGE_HEAD', 'index.lock', 'CHERRY_PICK_HEAD', 'REVERT_HEAD')) {
        $path = Git -Arguments @('rev-parse', '--git-path', $name)
        if (![IO.Path]::IsPathRooted($path)) { $path = Join-Path $Repository $path }
        if (Test-Path -LiteralPath $path) { throw "$name present before $Phase" }
    }
    $env:RSPS_LIVE_EXPECTED_HEAD = $expected
}
function Invoke-Phase([string]$Name, [string]$Script) {
    Assert-State $Name
    if ($DryRun) { Write-Host "DRY-RUN $Name HEAD=$expected"; return }
    $log = Join-Path $LogDirectory "live-$round-$Name.out.log"
    $err = Join-Path $LogDirectory "live-$round-$Name.err.log"
    Write-Host "LIVE $Name HEAD=$expected log=$log stderr=$err"
    # OS file redirection: no ReadToEnd/pipe that inherits server/daemon handles.
    $child = Start-Process powershell.exe -ArgumentList @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $Script + '"')) -WorkingDirectory $Repository -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $err -PassThru
    try {
        # Cache the process handle before WaitForExit (PowerShell 5 can otherwise
        # return a null ExitCode from a Start-Process Process object).
        $null = $child.Handle
        $child.WaitForExit()
        $child.Refresh()
        if ($child.ExitCode -ne 0) { throw "$Name exit $($child.ExitCode); zie $log en $err" }
    } finally { $child.Dispose() }
    Write-Host "LIVE $Name exit 0 log=$log"
    Assert-State "after $Name"
}
try {
    $Repository = (Resolve-Path -LiteralPath $Repository).Path
    New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null
    if (!$DryRun) {
        foreach ($script in @($BuildScript, $BootcheckScript, $ReleaseScript)) {
            if (!$script -or !(Test-Path -LiteralPath $script -PathType Leaf) -or ![IO.Path]::IsPathRooted($script)) {
                throw 'Supply absolute synchronous build, bootcheck and release .ps1 scripts'
            }
        }
    } elseif ([IO.Path]::GetFullPath($SlotPath) -ieq 'C:\RSPS\team\LIVE-SLOT.txt') {
        throw 'Dry-run requires an isolated trial SlotPath'
    }
    # CreateNew failure is fatal: no merge/build/release/push is reached.
    $lease = [IO.File]::Open($SlotPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::ReadWrite, [IO.FileShare]::Read)
    $bytes = [Text.Encoding]::UTF8.GetBytes("ChatGPT token=$token pid=$PID start=$((Get-Process -Id $PID).StartTime.ToString('o')) repo=$Repository")
    $lease.Write($bytes, 0, $bytes.Length)
    $lease.Flush($true)
    $env:RSPS_LIVE_TOKEN = $token
    $env:RSPS_LIVE_REPOSITORY = $Repository
    $env:RSPS_LIVE_SLOT = $SlotPath
    Assert-State 'merge'
    $source = Git -Arguments @('rev-parse', '--verify', "$SourceRef^{commit}")
    if ($DryRun) { Write-Host "DRY-RUN merge $source (no mutation)" } else {
        Git -Arguments @('merge', '--ff-only', $source) | Write-Host
        $expected = $source
        $merged = $true
        Assert-State 'after merge'
    }
    $phase = 'build'; Invoke-Phase $phase $BuildScript
    $phase = 'bootcheck'; Invoke-Phase $phase $BootcheckScript
    $phase = 'release'; Invoke-Phase $phase $ReleaseScript
    $phase = 'push'
    Assert-State 'push'
    if ($DryRun) { Write-Host "DRY-RUN push $expected (no network)" } else {
        Git -Arguments @('push', $Remote, "${expected}:refs/heads/$Branch") | Write-Host
        Assert-State 'after push'
    }
    if (!$DryRun -and $Dossier) { Add-Content -LiteralPath $Dossier -Encoding UTF8 -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm') ${Agent}: LIVE $expected | build+gerichte tests exit0, bootcheck exit0, release exit0, push $Remote $Branch exit0; fase-logs live-$round-* in $LogDirectory" }
    $completed = $true
    Write-Host "LIVE ROUND COMPLETE HEAD=$expected dryrun=$DryRun"
} catch {
    $failure = $_.Exception.Message
    $rollback = 'niet van toepassing'
    if ($merged -and $phase -in @('build', 'bootcheck')) {
        try {
            # Only our own exact merge may be reset. Never discard concurrent work.
            Assert-State 'rollback'
            Git -Arguments @('reset', '--hard', $ExpectedHead) | Write-Host
            $expected = $ExpectedHead
            Assert-State 'after rollback'
            $rollback = "terug op ExpectedHead $ExpectedHead"
        } catch { $rollback = "NIET teruggezet: $($_.Exception.Message)" }
    }
    $message = "STOP LIVE-ROUND fase=$phase : $failure; $rollback; slotmarker=$SlotPath"
    Write-Host $message
    if ($lease -and $Dossier) { Add-Content -LiteralPath $Dossier -Encoding UTF8 -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm') ${Agent}: $message" }
    exit 1
} finally {
    $env:RSPS_LIVE_TOKEN = $previousToken
    $env:RSPS_LIVE_EXPECTED_HEAD = $previousHead
    $env:RSPS_LIVE_REPOSITORY = $previousRepository
    $env:RSPS_LIVE_SLOT = $previousSlot
    if ($lease) {
        $lease.Dispose()
        if ($completed) {
            # CreateNew contenders cannot claim until deletion; refuse a replaced marker.
            if (([IO.File]::ReadAllText($SlotPath)) -match [regex]::Escape("token=$token")) {
                Remove-Item -LiteralPath $SlotPath -ErrorAction Stop
            } else { throw 'Slot replaced; recovery marker retained' }
        } else { Write-Host "Recovery marker retained: $SlotPath (no age-based takeover)" }
    }
}
