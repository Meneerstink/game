"""Run actual step-pool predicates with real decoded cache definitions, without a server."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess

ROOT=Path(__file__).resolve().parents[2]
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
CACHE=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'


def jar(module):return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    out=ROOT/'build/clue/applied-assets'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    out.mkdir(parents=True)
    source=ROOT/'tools/clue/ClueAppliedAssetAudit.kt'
    if not source.exists():source=ROOT/'build/clue/applied-assets/ClueAppliedAssetAudit.kt'
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    cp=[str(out/'classes'),str(ROOT/'game/build/libs/game-0.0.4.jar'),str(ROOT/'game/plugins/build/libs/plugins-0.0.4.jar')]
    cp += [str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')]
    cp += [jar('io.mockk/'+m+'/1.13.4') for m in ['mockk-agent-api-jvm','mockk-agent-jvm','mockk-core-jvm','mockk-dsl-jvm','mockk-jvm']]
    cp += [jar(m) for m in ['org.jetbrains.kotlin/kotlin-reflect/1.8.0','net.bytebuddy/byte-buddy/1.12.20',
        'net.bytebuddy/byte-buddy-agent/1.12.20','org.objenesis/objenesis/3.3','org.jetbrains/annotations']]
    def run(args):subprocess.run(list(map(str,args)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    # Standalone jars can predate the source snapshot used by installDist. Compile
    # the actual predicate implementations; never accept an old jar's missing alias.
    folder=ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
    actual=[folder/(name+'.kt') for name in ['TreasureTrailBasicSteps','TreasureTrailNpcSteps',
        'TreasureTrailEmotes','TreasureTrailAgents','TreasureTrailWizards','TreasureTrailPuzzleBoxes',
        'TreasureTrailMasterPuzzles','TreasureTrailLightBoxes','TreasureTrailUri','ClueItems','ClueNpcs',
        'ClueInterfaces','TreasureTrailMaps','TreasureTrailStepCatalogue']]
    frozen={str(p.relative_to(ROOT)):sha256(p.read_bytes()).hexdigest() for p in actual+[source]}
    run([JAVA/'java.exe','-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         '-no-stdlib','-no-reflect','-jvm-target','11','-Xfriend-paths='+str(ROOT/'game/plugins/build/libs/plugins-0.0.4.jar'),
         '-cp',';'.join(cp),'-d',out/'classes',source]+actual)
    run([JAVA/'java.exe','-Xmx512m','-Drsps.data='+str(ROOT/'data'),'-cp',';'.join(cp),
         'gg.rsmod.plugins.content.scrolls.ClueAppliedAssetAudit','C:/RSPS/game/game/data/cache',out/'audit.json'])
    (ROOT/'build/clue/applied-assets/latest.txt').write_text(str(out))
    assert all(sha256((ROOT/p).read_bytes()).hexdigest()==h for p,h in frozen.items())
    (out/'source-hashes.json').write_text(json.dumps(frozen,indent=2)+'\n')
    print(out)


if __name__=='__main__':main()
