param([switch]$BreakOnly)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$runArgs = @()
if (-not $BreakOnly) {
    $runArgs += @(':game:test', '--tests', '*DeadmanExperienceLocksTests', ':game:plugins:scripts:compileKotlin')
}
$runArgs += ':game:plugins:test'
$filters = if ($BreakOnly) { @('Dmm2BreakTests') } else {
    @('DeadmanExperienceExtensionTests', 'SafeDepositBoxTests', 'DeadmanStarterPackTests', 'StarterWeaponCombatTests',
      'StarterPackInteractionsTests', 'NewPlayerStartTests', 'NigelRewardStoreTests', 'NigelHouseRewardsTests',
      'Dmm2ItemServicesTests', 'Dmm2CosmeticsTests', 'Dmm2CurrencyTests', 'Dmm2InteractionTests',
      'Dmm2BreakTests', 'Dmm2SourcePolicyTests')
}
foreach ($filter in $filters) { $runArgs += @('--tests', ('*' + $filter)) }
$runArgs += @('--continue', '--priority', 'low', '--no-parallel', '-I', 'docs/dmm2-research/phase3-tests.gradle')
Push-Location $taskRoot
try {
    & .\gradlew.bat @runArgs
    $testExit = $LASTEXITCODE
} finally { Pop-Location }
exit $testExit
