. "$PSScriptRoot\Common.ps1"
try {
    $built = Get-Content -LiteralPath 'build\live-round-build.json' -Raw | ConvertFrom-Json
    $boot = Get-Content -LiteralPath 'build\live-round-boot.json' -Raw | ConvertFrom-Json
    foreach ($proof in @($built, $boot)) {
        if ($proof.token -ne $env:RSPS_LIVE_TOKEN -or $proof.head -ne $env:RSPS_LIVE_EXPECTED_HEAD) { throw 'Build/boot proof does not belong to this round' }
    }
    & powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File 'C:\RSPS\work\claude-scripts\release_live_nobuild.ps1'
    if ($LASTEXITCODE -ne 0) { throw "release_live_nobuild exit $LASTEXITCODE (STOP, no push)" }
    Assert-LivePhase
    Write-Output 'LIVE RELEASE OK exit 0'
    exit 0
} catch { Write-Output "RELEASE STOP: $($_.Exception.Message)"; exit 1 }
