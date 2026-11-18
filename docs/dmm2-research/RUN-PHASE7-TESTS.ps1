param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$core = @('Dmm2SkeletalExtraSeqTests','Dmm2ImportBatchTests','Dmm2UiPlanTests')
$plugins = @('Dmm2BootTests','Dmm2BreakTests','Dmm2CosmeticsTests','Dmm2CurrencyTests','Dmm2InteractionTests',
 'Dmm2ItemServicesTests','Dmm2SourcePolicyTests','Dmm2WeaponPresentationTests','Dmm2WindowTests',
 'StarterWeaponCombatTests','StarterPackInteractionsTests','DeadmanStarterPackTests',
 'NigelHouseRewardsTests','NigelRewardStoreTests','SafeDepositBoxTests','DeadmanExperienceExtensionTests',
 'HomeTeleportDeadmanGateTests','PvpDeathBreakablesTests','UntradeableDeathProtectionTests','PvpLootTimesTests',
 'FairyRingTravelLogTests','SpiritTreeMenuTests','N503PetDeathTests')
$runArgs = @(':game:installDist', ':game:test')
foreach ($name in $core) { $runArgs += @('--tests', ('*' + $name)) }
$runArgs += ':game:plugins:test'
foreach ($name in $plugins) { $runArgs += @('--tests', ('*' + $name)) }
$runArgs += ('-Pdmm2CoreTests=' + ($core -join ','))
$runArgs += ('-Pdmm2PluginTests=' + ($plugins -join ','))
$runArgs += @('-I', 'docs/dmm2-research/phase7-tests.gradle', '--offline', '--continue', '--priority', 'low', '--no-parallel')
Push-Location $taskRoot
try { & C:/RSPS/tools/remote/rgradle.cmd @runArgs; $testExit = $LASTEXITCODE } finally { Pop-Location }
exit $testExit
