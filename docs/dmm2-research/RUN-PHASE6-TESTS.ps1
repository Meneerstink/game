param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runArgs = @(':game:test', '--tests', '*Dmm2UiPlanTests', ':game:plugins:scripts:compileKotlin', ':game:plugins:test')
foreach ($name in @('Dmm2WindowTests','Dmm2InteractionTests','SafeDepositBoxTests','NigelRewardStoreTests','Dmm2BootTests','Dmm2BreakTests','Dmm2WeaponPresentationTests')) {
    $runArgs += @('--tests', ('*' + $name))
}
$runArgs += @('--offline', '--continue', '--priority', 'low', '--no-parallel', '-I', 'docs/dmm2-research/phase6-regression.gradle')
Push-Location $taskRoot
try { & C:/RSPS/tools/remote/rgradle.cmd @runArgs; $testExit = $LASTEXITCODE } finally { Pop-Location }
exit $testExit
