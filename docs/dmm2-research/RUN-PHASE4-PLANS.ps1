param([switch]$Resume)
$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot '..\..')
try {
    if ($Resume) {
        python docs/dmm2-research/phase4_round.py snapshot
    } else {
        python docs/dmm2-research/phase4_round.py prepare
    }
    if ($LASTEXITCODE -ne 0) { throw 'Snapshot preparation/verification failed' }
    $runArgs = @('dmm2Phase4Plans', '--priority', 'low', '-I', 'docs/dmm2-research/phase4-plans.gradle')
    if ($Resume) { $runArgs += '-Pdmm2Resume' }
    & .\gradlew.bat @runArgs
    if ($LASTEXITCODE -ne 0) { throw 'Named native PLAN failed' }
    python docs/dmm2-research/phase4_round.py verify
    if ($LASTEXITCODE -ne 0) { throw 'Post-PLAN verification failed' }
} finally { Pop-Location }
