"""Fresh 512m compiler/JUnit run for the guardian loot delta; no Gradle/cache writes."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/clue/guardian-assets'/('verify-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
NEXT=ROOT/'build/clue/guardian-assets/next'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
CACHE=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'


def jar(module): return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    OUT.mkdir(parents=True)
    staged='--staged' in sys.argv
    libraries=[NEXT/'ClueGuardianDrops.kt'] if staged else [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls/ClueGuardianDrops.kt']
    tests=[NEXT/'ClueGuardianDropsTests.kt'] if staged else [ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/ClueGuardianDropsTests.kt']
    inputs=libraries+tests+[ROOT/'data/cfg/clue/guardian-drops.json']
    names=['gg.rsmod.plugins.content.scrolls.ClueGuardianDropsTests']
    if '--hotcold' in sys.argv:
        following=ROOT/'build/clue/hotcold/next'
        libraries=[following/(name+'.kt') for name in ['TreasureTrailHotCold','TreasureTrailHotColdDialogue',
                    'TreasureTrailWizards','TreasureTrailAgents']]
        libraries += [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls/ClueGuardianDrops.kt']
        tests=[following/(name+'.kt') for name in ['TreasureTrailHotColdTests','TreasureTrailWizardTests']]
        inputs=libraries+tests+[ROOT/'build/clue/hotcold/hotcold.json']
        names=['gg.rsmod.plugins.content.scrolls.'+name for name in ['TreasureTrailHotColdTests','TreasureTrailWizardTests']]
    if '--mimic-candies' in sys.argv:
        following=ROOT/'build/clue/mimic-candies/next'
        libraries=[following/'MimicCandies.kt']
        tests=[following/'MimicCandiesTests.kt']
        inputs=libraries+tests
        names=['gg.rsmod.plugins.content.areas.watson.MimicCandiesTests']
    if '--hotcold-runtime' in sys.argv:
        main=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
        test=ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls'
        libraries=[main/(name+'.kt') for name in ['TreasureTrailHotCold','TreasureTrailHotColdDialogue',
                    'TreasureTrailWizards','TreasureTrailAgents','ClueScrollManager','ClueGuardianDrops']]
        tests=[test/(name+'.kt') for name in ['TreasureTrailHotColdTests','TreasureTrailWizardTests']]
        inputs=libraries+tests+[ROOT/'data/cfg/clue/hotcold.json']
        names=['gg.rsmod.plugins.content.scrolls.'+name for name in ['TreasureTrailHotColdTests','TreasureTrailWizardTests']]
    if '--mimic-candies-runtime' in sys.argv:
        main=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/watson'
        libraries=[main/(name+'.kt') for name in ['MimicCandies','MimicEncounter','MimicMinions','MimicAttacks','MimicCaskets']]
        scrolls=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
        libraries += [scrolls/(name+'.kt') for name in ['ClueGuardianProfiles','ClueGuardianCombat','ClueCombatAssets']]
        cohorts=['MimicCandiesTests','MimicAttackTests','MimicEncounterTests','MimicChallengeTests','MimicRewardsTests','TheMimicCombatDefTests']
        tests=[ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/areas/watson'/(name+'.kt') for name in cohorts]
        if '--mimic-arena-staged' in sys.argv:
            arena=ROOT/'build/clue/mimic-arena/next'
            libraries=[arena/'MimicEncounter.kt' if p.name=='MimicEncounter.kt' else p for p in libraries]
            tests=[arena/'MimicEncounterTests.kt' if p.name=='MimicEncounterTests.kt' else p for p in tests]
        inputs=libraries+tests+[ROOT/'data/cfg/clue/mimic-encounter.json']
        names=['gg.rsmod.plugins.content.areas.watson.'+name for name in cohorts]
    if '--dig-guards' in sys.argv:
        main=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
        test=ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls'
        libraries=[main/(name+'.kt') for name in ['ClueGuardianProfiles','TreasureTrailDigGuards','TreasureTrailGuardians',
                    'TreasureTrailHotCold','TreasureTrailHotColdDialogue','TreasureTrailWizards','TreasureTrailAgents',
                    'ClueScrollManager','ClueGuardianDrops','ClueGuardianCombat','ClueCombatAssets','TreasureTrailChallenges','TreasureTrailGuardianDialogue']]
        libraries += [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/slayer/Krystilia.kt']
        cohorts=['TreasureTrailDigGuardTests','TreasureTrailGuardianTests','TreasureTrailHotColdTests','TreasureTrailWizardTests','ClueGuardianCombatTests','TreasureTrailChallengeTests','TreasureTrailGuardianDialogueTests']
        tests=[test/(name+'.kt') for name in cohorts]
        inputs=libraries+tests+[ROOT/'data/cfg/clue/hotcold.json',ROOT/'data/cfg/clue/guardians.json']
        names=['gg.rsmod.plugins.content.scrolls.'+name for name in cohorts]
    if '--npc-resolution' in sys.argv:
        main=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
        libraries=[main/'ClueNpcSources.kt']
        tests=[ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/ClueNpcSourcesTests.kt']
        inputs=libraries+tests+[ROOT/'data/cfg/clue/npc-sources.json']
        names=['gg.rsmod.plugins.content.scrolls.ClueNpcSourcesTests']
    if '--pet-dig-staged' in sys.argv:
        following=ROOT/'build/clue/pet-dig/next'
        libraries=[following/'CluePetDig.kt',following/'CluePetDigCacheTool.kt',following/'Pets.kt']
        tests=[following/'CluePetDigTests.kt',following/'CluePetDigCacheTests.kt']
        inputs=libraries+tests
        names=['gg.rsmod.plugins.content.scrolls.CluePetDigTests','gg.rsmod.game.tools.importer.CluePetDigCacheTests']
    if '--pet-dig-runtime' in sys.argv:
        libraries=[ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls/CluePetDig.kt',
                   ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/CluePetDigCacheTool.kt',
                   ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/skills/summoning/Pets.kt']
        tests=[ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/CluePetDigTests.kt',
               ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/CluePetDigCacheTests.kt']
        inputs=libraries+tests
        names=['gg.rsmod.plugins.content.scrolls.CluePetDigTests','gg.rsmod.game.tools.importer.CluePetDigCacheTests']
    if '--clue-admin-staged' in sys.argv:
        following=ROOT/'build/clue/admin/next'
        libraries=[following/'ClueAdminSteps.kt',following/'ClueScrollManager.kt']
        tests=[following/'ClueAdminStepsTests.kt']
        inputs=libraries+tests
        names=['gg.rsmod.plugins.content.scrolls.ClueAdminStepsTests']
    if '--clue-admin-runtime' in sys.argv:
        main=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
        libraries=[main/'ClueAdminSteps.kt',main/'ClueScrollManager.kt']
        tests=[ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/ClueAdminStepsTests.kt']
        inputs=libraries+tests
        names=['gg.rsmod.plugins.content.scrolls.ClueAdminStepsTests']
    if '--mimic-map-staged' in sys.argv:
        following=ROOT/'build/clue/mimic-arena/next'
        libraries=[following/(name+'.kt') for name in ['ModernRegionProbeTool','ClueMimicArenaAssets','OsrsRegionImportTool']]
        libraries += [ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/Rev667WorldCodec.kt']
        tests=[following/'ClueMimicArenaImportTests.kt']
        inputs=libraries+tests+[ROOT/'docs/CLUE/cache/mimic-arena-source.json']
        names=['gg.rsmod.game.tools.importer.ClueMimicArenaImportTests']
    if '--mimic-map-runtime' in sys.argv:
        main=ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer'
        libraries=[main/(name+'.kt') for name in ['ModernRegionProbeTool','ClueMimicArenaAssets','OsrsRegionImportTool','Rev667WorldCodec']]
        tests=[ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/ClueMimicArenaImportTests.kt']
        inputs=libraries+tests+[ROOT/'docs/CLUE/cache/mimic-arena-source.json']
        names=['gg.rsmod.game.tools.importer.ClueMimicArenaImportTests']
    frozen={str(p):sha256(p.read_bytes()).hexdigest() for p in inputs}
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    deps=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-test/1.8.0','org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
        'junit/junit','org.hamcrest/hamcrest-core','org.jetbrains.kotlin/kotlin-reflect/1.8.0',
        'net.bytebuddy/byte-buddy/1.12.20','net.bytebuddy/byte-buddy-agent/1.12.20','org.objenesis/objenesis/3.3',
        'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4']]
    deps += [jar('io.mockk/'+m+'/1.13.4') for m in ['mockk-agent-api-jvm','mockk-agent-jvm','mockk-core-jvm','mockk-dsl-jvm','mockk-jvm']]
    plugin=ROOT/'game/plugins/build/libs/plugins-0.0.4.jar'
    cp=[str(OUT/'classes'),str(ROOT/'game/build/libs/game-0.0.4.jar'),str(plugin)]
    cp += [str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')]+deps
    def run(args): subprocess.run(list(map(str,args)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    args=['-no-stdlib','-no-reflect','-jvm-target','11','-nowarn','-Xfriend-paths='+str(plugin),
          '-cp',';'.join(cp),'-d',str(OUT/'classes')]+list(map(str,libraries+tests))
    argfile=OUT/'compile.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
    run([JAVA/'java.exe','-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)])
    with (OUT/'tests.txt').open('w') as stream:
        result=subprocess.run([str(JAVA/'java.exe'),'-Xmx256m','-Drsps.data='+str(ROOT/'data'),'-cp',';'.join(cp),
                              'org.junit.runner.JUnitCore']+names,
                             cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    output=(OUT/'tests.txt').read_text(errors='replace');print(output[-6500:],flush=True)
    assert result.returncode==0
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in frozen.items())
    (OUT/'proof.json').write_text(json.dumps(dict(inputs=frozen,unchanged=True,tests=output),indent=2)+'\n')
    (ROOT/'build/clue/guardian-assets/latest-verify.txt').write_text(str(OUT))
    print('Fresh guardian loot tests passed:',OUT,flush=True)


if __name__=='__main__': main()
