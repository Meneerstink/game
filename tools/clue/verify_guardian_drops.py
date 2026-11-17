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
                              'org.junit.runner.JUnitCore','gg.rsmod.plugins.content.scrolls.ClueGuardianDropsTests'],
                             cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    output=(OUT/'tests.txt').read_text(errors='replace');print(output[-6500:],flush=True)
    assert result.returncode==0
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in frozen.items())
    (OUT/'proof.json').write_text(json.dumps(dict(inputs=frozen,unchanged=True,tests=output),indent=2)+'\n')
    (ROOT/'build/clue/guardian-assets/latest-verify.txt').write_text(str(OUT))
    print('Fresh guardian loot tests passed:',OUT,flush=True)


if __name__=='__main__': main()
