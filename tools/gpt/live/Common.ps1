$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Assert-LivePhase {
    foreach ($value in @($env:RSPS_LIVE_TOKEN, $env:RSPS_LIVE_EXPECTED_HEAD, $env:RSPS_LIVE_REPOSITORY, $env:RSPS_LIVE_SLOT)) {
        if (!$value) { throw 'Start this phase through Invoke-LiveRound.ps1' }
    }
    if ((Get-Content -LiteralPath $env:RSPS_LIVE_SLOT -Raw) -notmatch [regex]::Escape("token=$env:RSPS_LIVE_TOKEN")) { throw 'Live lease owner mismatch' }
    Set-Location -LiteralPath $env:RSPS_LIVE_REPOSITORY
    $head = & git.exe rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -ne $env:RSPS_LIVE_EXPECTED_HEAD) { throw 'Live phase HEAD mismatch' }
}
Assert-LivePhase
