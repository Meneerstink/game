"""One low-priority Gradle run for the changed CLUE classes.

Records the input hashes before and after, so a source-drift run cannot be accepted.
No server/client process, apply flag, broad test suite or unrelated data is accessed.
"""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
CORE = ['gg.rsmod.game.plugin.ClueNpcOptionOverrideTests', 'gg.rsmod.game.tools.importer.ClueStashImportTests',
        'gg.rsmod.game.tools.importer.ClueWatsonCacheTests', 'gg.rsmod.game.tools.importer.ClueStashChartImportTests',
        'gg.rsmod.game.tools.importer.ClueStashSettingImportTests',
        'gg.rsmod.game.tools.importer.ClueInterfaceImportTests', 'gg.rsmod.game.tools.importer.ClueForearmImportTests',
        'gg.rsmod.game.tools.importer.ClueImportDryRunTests', 'gg.rsmod.game.model.entity.TransformResolutionTests',
        'gg.rsmod.game.action.ObjectInteractionDispatchTests']
PLUGINS = ['gg.rsmod.plugins.content.areas.watson.' + name for name in
           ['MimicChallengeTests', 'MimicRewardsTests', 'OsrsMimicImportTests', 'StrangeCasketTests',
            'TheMimicCombatDefTests', 'WatsonClueTests', 'WatsonAppearanceTests']]
PLUGINS += ['gg.rsmod.plugins.content.items.osrs.' + name for name in
            ['OsrsCasketOrnamentsImportTests', 'OsrsCasketTeleportScrollsImportTests', 'OsrsCasketWeaponsImportTests']]
PLUGINS += ['gg.rsmod.plugins.content.items.OsrsEliteCasketTests']
PLUGINS += ['gg.rsmod.plugins.content.scrolls.' + name for name in
            ['ClueCasketTransactionTests', 'ClueDropIndexTests', 'ClueDropOwnershipTests', 'ClueInterfacesTests',
             'ClueLogCatalogueTests', 'ClueNpcSourcesTests', 'ClueRewardCatalogueTests', 'ClueScrollLifecycleTests',
             'TreasureTrailChallengeTests', 'TreasureTrailStepCatalogueTests', 'TreasureTrailTornPartsTests', 'TreasureTrailStashTests', 'TreasureTrailStashChartTests', 'TreasureTrailStashSettingTests',
             'TreasureTrailFaloTests', 'TreasureTrailSherlockTests', 'TreasureTrailContractTests', 'ClueNpcAssetsTests']]


# Explicit combined handoff gate: only the card's changed classes and shared decoder regressions.
CORE += ['gg.rsmod.game.tools.importer.' + name for name in
         ['ClueSearchMenuImportTests', 'ClueCoreCacheTests', 'ClueMapInterfaceImportTests', 'CluePuzzleInterfaceTests']]
PLUGINS += ['gg.rsmod.plugins.content.scrolls.' + name for name in
            ['TreasureTrailNumberChallengeTests', 'TreasureTrailBasicStepsTests', 'ClueStepSelectionTests',
             'TreasureTrailMapsTests', 'TreasureTrailEmoteTests', 'TreasureTrailUriSourceTests', 'TreasureTrailPuzzleBoxTests']]

if '--basic' in sys.argv:
    CORE = ['gg.rsmod.game.tools.importer.' + name for name in
            ['ClueSearchMenuImportTests', 'ClueCoreCacheTests', 'ClueInterfaceImportTests', 'ClueMapInterfaceImportTests', 'CluePuzzleInterfaceTests']]
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.' + name for name in
               ['TreasureTrailNumberChallengeTests', 'TreasureTrailBasicStepsTests',
                'ClueStepSelectionTests', 'TreasureTrailStepCatalogueTests',
                'TreasureTrailTornPartsTests', 'TreasureTrailContractTests',
                'ClueScrollLifecycleTests', 'TreasureTrailFaloTests',
                'TreasureTrailSherlockTests', 'ClueNpcAssetsTests',
                'ClueDropOwnershipTests', 'ClueCasketTransactionTests', 'TreasureTrailMapsTests', 'TreasureTrailEmoteTests', 'TreasureTrailUriSourceTests', 'TreasureTrailPuzzleBoxTests']]
    PLUGINS += ['gg.rsmod.plugins.content.areas.watson.WatsonClueTests']

if '--continuation' in sys.argv:
    CORE = []
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.' + name for name in
               ['ClueScrollLifecycleTests', 'TreasureTrailContractTests', 'TreasureTrailTornPartsTests',
                'TreasureTrailBasicStepsTests', 'TreasureTrailEmoteTests', 'TreasureTrailNumberChallengeTests',
                'TreasureTrailMapsTests', 'ClueStepSelectionTests', 'ClueCasketTransactionTests',
                'ClueDropOwnershipTests', 'ClueGuardianCombatDataTests']]
    PLUGINS += ['gg.rsmod.plugins.content.areas.watson.' + name for name in
                ['MimicEncounterTests', 'MimicChallengeTests', 'WatsonClueTests']]

if '--restored' in sys.argv:
    CORE = ['gg.rsmod.game.tools.importer.' + name for name in
            ['ClueStashImportTests', 'ClueStashChartImportTests', 'ClueMapInterfaceImportTests',
             'ClueInterfaceImportTests', 'CluePuzzleInterfaceTests']]
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.' + name for name in
               ['ClueScrollLifecycleTests', 'TreasureTrailContractTests', 'TreasureTrailGuardianTests',
                'TreasureTrailStepCatalogueTests', 'TreasureTrailEmoteTests', 'TreasureTrailUriSourceTests',
                'TreasureTrailStashTests', 'TreasureTrailStashChartTests', 'TreasureTrailStashSettingTests',
                'TreasureTrailBasicStepsTests', 'ClueStepSelectionTests', 'TreasureTrailTornPartsTests',
                'TreasureTrailFaloTests', 'TreasureTrailSherlockTests', 'TreasureTrailNumberChallengeTests',
                'TreasureTrailMapsTests', 'TreasureTrailPuzzleBoxTests', 'ClueDropOwnershipTests',
                'ClueCasketTransactionTests', 'ClueInterfacesTests', 'ClueNpcAssetsTests', 'ClueDropIndexTests']]
    PLUGINS += ['gg.rsmod.plugins.content.areas.watson.' + name for name in
                ['WatsonClueTests', 'MimicEncounterTests', 'MimicChallengeTests', 'TheMimicCombatDefTests', 'MimicRewardsTests']]

if '--unique' in sys.argv:
    CORE = []
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.' + name for name in
               ['TreasureTrailNpcStepsTests', 'TreasureTrailNumberChallengeTests', 'TreasureTrailBasicStepsTests',
                'TreasureTrailStepCatalogueTests', 'ClueScrollLifecycleTests', 'TreasureTrailContractTests',
                'TreasureTrailEmoteTests', 'ClueStepSelectionTests', 'TreasureTrailMapsTests']]

if '--guardian-pvp' in sys.argv:
    CORE = []
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.' + name for name in
               ['ClueGuardianPvpTests', 'TreasureTrailGuardianTests', 'TreasureTrailAgentTests', 'TreasureTrailNpcStepsTests',
                'TreasureTrailNumberChallengeTests', 'TreasureTrailBasicStepsTests',
                'TreasureTrailStepCatalogueTests', 'ClueScrollLifecycleTests', 'TreasureTrailContractTests',
                'TreasureTrailEmoteTests', 'TreasureTrailUriSourceTests', 'ClueStepSelectionTests', 'TreasureTrailMapsTests']]
    PLUGINS += ['gg.rsmod.plugins.content.combat.SinglesPlusTests']

if '--master-puzzles' in sys.argv:
    CORE = []
    PLUGINS = ['gg.rsmod.plugins.content.scrolls.'+name for name in
               ['TreasureTrailLightBoxTests', 'TreasureTrailMasterPuzzleTests', 'TreasureTrailPuzzleBoxTests',
                'TreasureTrailContractTests', 'ClueScrollLifecycleTests', 'ClueDropOwnershipTests',
                'TreasureTrailStepCatalogueTests', 'ClueNpcAssetsTests', 'ClueStepSelectionTests',
                'ClueCasketTransactionTests']]


def inputs():
    tracked = subprocess.check_output(['git', 'ls-files'], cwd=ROOT, text=True).splitlines()
    untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
    allowed = [p for p in sorted(set(tracked + untracked))
               if p.endswith(('.kt', '.kts', '.gradle')) or p.startswith(('data/cfg/clue/', 'docs/CLUE/sources/Transcript_')) or p in {
                   'data/cfg/objs.yml', 'data/cfg/npcs/combat-defs.json', 'docs/CLUE/cache/stash-chart-baked.json', 'docs/CLUE/cache/hover.json',
                   'docs/CLUE/cache/interfaces.json', 'docs/CLUE/cache/stash-setting.json',
                   'docs/CLUE/cache/map-interfaces.json', 'docs/CLUE/cache/puzzle-interface.json', 'docs/CLUE/cache/puzzle-pictures.json', 'docs/CLUE/cache/uri-source.json',
                   'docs/CLUE/cache/ui-camera-order.json', 'docs/CLUE/cache/search-bucket.json',
                   'docs/CLUE/cache/core-source-items.json', 'docs/CLUE/cache/puzzle-source-items.json', 'docs/CLUE/cache/puzzle-routes.json',
                   'docs/CLUE/sources/Puzzle_box.wiki', 'docs/CLUE/sources/Clue_scroll_(hard)_-_BLUE_GRIM_GUIDED.wiki',
                   'docs/CLUE/sources/Clue_scroll_(hard)_-_IWPPLQTP.wiki', 'docs/CLUE/sources/Clue_scroll_(master).wiki',
                   'docs/CLUE/NPC-DIALOGUE-SOURCES.json', 'docs/CLUE/NPC-COMPLETION-SOURCES.json',
                   'docs/CLUE/cache/lightbox-interface.json', 'docs/CLUE/cache/lightbox-scripts.json',
                   'docs/CLUE/cache/lightbox-items.json', 'docs/CLUE/cache/lightbox-layout.json',
                   'docs/CLUE/sources/Light_box.wiki', 'docs/CLUE/sources/Treasure_Trails__Guide__Light_boxes.wiki',
                   'docs/CLUE/sources/File_Killerwatt_attack.wav.wiki', 'docs/CLUE/sources/File_Killerwatt_death.wav.wiki'}]
    return {p: sha256((ROOT/p).read_bytes()).hexdigest() for p in allowed if (ROOT/p).is_file()}


def main():
    stamp = datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    out = ROOT / 'build/clue' / ('integration-' + stamp)
    out.mkdir(parents=True)
    before = inputs()
    started = datetime.now(timezone.utc).timestamp()
    assert CORE or PLUGINS
    args = ['cmd.exe', '/d', '/c', str(ROOT/'gradlew.bat'), '-I', 'tools/clue/test-init.gradle']
    # Never emit an unfiltered test task when a module has no selected classes.
    for task, names in [(':game:test', CORE), (':game:plugins:test', PLUGINS)]:
        if names:
            args += [task]
            for name in names:
                args += ['--tests', name]
    args += ['--priority', 'low', '--console=plain']
    (out/'inputs-before.json').write_text(json.dumps(before, indent=2))
    (out/'command.json').write_text(json.dumps(args, indent=2))
    print('Frozen', len(before), 'inputs;', len(CORE), 'core and', len(PLUGINS), 'plugin test classes;', out, flush=True)
    with (out/'gradle.txt').open('w') as stream:
        result = subprocess.run(args, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT,
                                creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after = inputs()
    (out/'inputs-after.json').write_text(json.dumps(after, indent=2))
    reports = []
    missing = []
    for folder, names in [('game/build/test-results/test', CORE), ('game/plugins/build/test-results/test', PLUGINS)]:
        for name in names:
            path = ROOT/folder/('TEST-'+name+'.xml')
            if not path.exists():
                missing.append(name)
                continue
            suite = ET.parse(path).getroot()
            reports.append(dict(name=name, tests=int(suite.attrib['tests']), failures=int(suite.attrib['failures']),
                                errors=int(suite.attrib['errors']), skipped=int(suite.attrib.get('skipped', '0')),
                                modified=path.stat().st_mtime, fresh=path.stat().st_mtime >= started))
    changed = [p for p in sorted(set(before) | set(after)) if before.get(p) != after.get(p)]
    # Gradle may legitimately reuse an up-to-date test task; a successful task graph
    # validates that cache entry against its declared inputs. Record freshness separately.
    accepted = result.returncode == 0 and not changed and not missing and all(r['failures'] == r['errors'] == r['skipped'] == 0 for r in reports)
    summary = dict(exit=result.returncode, unchangedInputs=not changed, changed=changed, missing=missing,
                   accepted=accepted, tests=sum(r['tests'] for r in reports), skipped=sum(r['skipped'] for r in reports), classes=reports)
    (out/'summary.json').write_text(json.dumps(summary, indent=2))
    print('Integration complete:', {k:v for k,v in summary.items() if k != 'classes'}, flush=True)
    if not accepted:
        print((out/'gradle.txt').read_text(errors='replace')[-8000:])
        sys.exit(1)


if __name__ == '__main__':
    main()
