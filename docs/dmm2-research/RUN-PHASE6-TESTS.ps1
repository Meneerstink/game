param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path -LiteralPath (Join-Path $taskRoot 'build/dmm2/phase5-classes/gg/rsmod/plugins/content/mechanics/deadman/Dmm2Ids.class'))) {
    throw 'Missing validated phase5 dependency snapshot. Run RUN-PHASE5-TESTS.ps1 first in this worktree.'
}
$runArgs = @(':game:test', '--tests', '*Dmm2UiPlanTests', ':game:plugins:scripts:compileKotlin', ':game:plugins:test')
foreach ($name in @('Dmm2WindowTests','Dmm2InteractionTests','SafeDepositBoxTests','NigelRewardStoreTests','Dmm2BootTests','Dmm2BreakTests')) {
    $runArgs += @('--tests', ('*' + $name))
}
$runArgs += @('--continue', '--priority', 'low', '--no-parallel', '-I', 'docs/dmm2-research/phase6-tests.gradle')
Push-Location $taskRoot
try { & .\gradlew.bat @runArgs; $testExit = $LASTEXITCODE } finally { Pop-Location }
exit $testExit
