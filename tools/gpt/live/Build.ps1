. "$PSScriptRoot\Common.ps1"
try {
    if (!$env:RSPS_LIVE_TESTS) { throw 'RSPS_LIVE_TESTS must contain a JSON array of targeted plugin test filters' }
    $tests = @($env:RSPS_LIVE_TESTS | ConvertFrom-Json)
    if (!$tests.Count) { throw 'No targeted tests supplied' }
    $arguments = @(':game:installDist', ':game:plugins:test')
    foreach ($test in $tests) {
        if (!$test -or $test -match '[\r\n";&|<>]') { throw 'Invalid test filter' }
        $arguments += @('--tests', $test)
    }
    # rb/rbuild.sh adds --console=plain itself; passing it twice stops Gradle ('Multiple arguments ... --console', 25-11 00:48).
    $arguments += @('--priority', 'low')
    & 'C:\RSPS\tools\remote\rgradle.cmd' @arguments
    if ($LASTEXITCODE -ne 0) { throw "rgradle exit $LASTEXITCODE" }
    Assert-LivePhase
    @{ token=$env:RSPS_LIVE_TOKEN; head=$env:RSPS_LIVE_EXPECTED_HEAD; tests=$tests } | ConvertTo-Json | Set-Content -LiteralPath 'build\live-round-build.json' -Encoding UTF8
    Write-Output 'LIVE BUILD OK exit 0'
    exit 0
} catch { Write-Output "BUILD STOP: $($_.Exception.Message)"; exit 1 }
