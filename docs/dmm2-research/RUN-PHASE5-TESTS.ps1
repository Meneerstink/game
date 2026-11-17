param()
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$selection = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'phase5-output/test-selection.json') | ConvertFrom-Json
$filters = @($selection | ForEach-Object { ($_ -split '/')[-1] }) + @(
    'DeadmanExperienceExtensionTests', 'SafeDepositBoxTests', 'DeadmanStarterPackTests', 'StarterWeaponCombatTests',
    'StarterPackInteractionsTests', 'NewPlayerStartTests', 'NigelRewardStoreTests', 'NigelHouseRewardsTests',
    'Dmm2ItemServicesTests', 'Dmm2CosmeticsTests', 'Dmm2CurrencyTests', 'Dmm2InteractionTests',
    'Dmm2BreakTests', 'Dmm2SourcePolicyTests', 'Dmm2WeaponPresentationTests')
$runArgs = @(':game:test', '--tests', '*DeadmanExperienceLocksTests', ':game:plugins:scripts:compileKotlin', ':game:plugins:test')
foreach ($filter in ($filters | Select-Object -Unique)) { $runArgs += @('--tests', ('*' + $filter)) }
$runArgs += @('--continue', '--priority', 'low', '--no-parallel', '-I', 'docs/dmm2-research/phase5-tests.gradle')
Push-Location $taskRoot
try { & .\gradlew.bat @runArgs; $testExit = $LASTEXITCODE } finally { Pop-Location }
exit $testExit
