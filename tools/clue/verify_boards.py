"""Compile only the Watson board delta and its own tests, using immutable dependency snapshots."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
STAGED='--staged' in sys.argv
NEXT=ROOT/'build/clue/watson-boards/next'
OUT=ROOT/'build/clue/watson-boards'/('verify-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
CACHE=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'

def jar(module):return str(sorted((CACHE/module).rglob('*.jar'))[-1])

def main():
    for part in ['libraries','tests','client','dependencies']:(OUT/part).mkdir(parents=True)
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    modules=['org.jetbrains.kotlin/kotlin-test/1.8.0','org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
        'junit/junit','org.hamcrest/hamcrest-core','org.jetbrains.kotlin/kotlin-reflect/1.8.0',
        'net.bytebuddy/byte-buddy/1.12.20','net.bytebuddy/byte-buddy-agent/1.12.20','org.objenesis/objenesis/3.3',
        'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4']
    modules+=['io.mockk/'+m+'/1.13.4' for m in ['mockk-agent-api-jvm','mockk-agent-jvm','mockk-core-jvm','mockk-dsl-jvm','mockk-jvm']]
    libraries=[]
    for name,path in [('game','game/build/libs/game-0.0.4.jar'),('plugins','game/plugins/build/libs/plugins-0.0.4.jar')]:
        source=ROOT/path;before=sha256(source.read_bytes()).hexdigest();target=OUT/'dependencies'/(name+'.jar')
        shutil.copy2(source,target)
        assert before==sha256(target.read_bytes()).hexdigest()==sha256(source.read_bytes()).hexdigest(),'Dependency changed during snapshot; rerun'
        libraries.append(str(target))
    libraries += list(map(str,(ROOT/'game/build/install/game/lib').glob('*.jar')))
    cp=[str(OUT/'libraries')]+libraries+[jar(m) for m in modules]
    def source(name,folder):return NEXT/(name+'.kt') if STAGED else ROOT/folder/(name+'.kt')
    core='game/src/main/kotlin/gg/rsmod/game/tools/importer'
    plugins='game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'
    sources=[source('ClueInterfaceImportTool',core),source('TreasureTrailStatistics',plugins)]
    tests=[source('ClueBoardInterfaceImportTests',core.replace('/main/','/test/')),
           source('TreasureTrailStatisticsTests',plugins.replace('/main/','/test/'))]
    inputs=sources+tests+[ROOT/'docs/CLUE/cache/board-interfaces.json',ROOT/'tools/clue/ClueBoardClientRegression.java',
                         ROOT/'data/cfg/clue/watson-contributors.json']
    frozen={str(p):sha256(p.read_bytes()).hexdigest() for p in inputs}
    def run(args,stream=None):
        result=subprocess.run(list(map(str,args)),cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT if stream else None,
                              creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
        if result.returncode:
            if stream:stream.flush();print(Path(stream.name).read_text(errors='replace')[-6500:])
            raise SystemExit('Board verification failed: '+str(result.returncode))
    def compile(paths,part):
        args=['-no-stdlib','-no-reflect','-jvm-target','11','-nowarn','-cp',';'.join(cp),'-d',str(OUT/part)]+list(map(str,paths))
        argfile=OUT/(part+'.args');argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
        run([JAVA/'java.exe','-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)])
    compile(sources,'libraries');compile(tests,'tests')
    with (OUT/'tests.txt').open('w') as stream:
        run([JAVA/'java.exe','-Xmx256m','-Drsps.data='+str(ROOT/'data'),'-cp',';'.join([str(OUT/'tests')]+cp),
             'org.junit.runner.JUnitCore','gg.rsmod.game.tools.importer.ClueBoardInterfaceImportTests',
             'gg.rsmod.plugins.content.scrolls.TreasureTrailStatisticsTests'],stream)
    print((OUT/'tests.txt').read_text(errors='replace'),flush=True)
    patched=ROOT/'build/clue/client-patch/Component.java'
    assert patched.is_file(),'Run prepare_client_patch.py first; canonical client stays untouched'
    native=Path('C:/RSPS/2011scape-client/2011scape-client/runescape/build/classes/java/main')
    clientcp=[str(native),jar('org.openrs2/openrs2-deob-annotations')]+cp
    run([JAVA/'javac.exe','-cp',';'.join(clientcp),'-d',OUT/'client',patched,ROOT/'tools/clue/ClueBoardClientRegression.java'])
    run([JAVA/'java.exe','-Xmx128m','-cp',';'.join([str(OUT/'client')]+clientcp),'ClueBoardClientRegression',ROOT/'docs/CLUE/cache/board-interfaces.json'])
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in frozen.items())
    proof=dict(unchanged=True,staged=STAGED,inputs=frozen,tests=(OUT/'tests.txt').read_text(errors='replace'),nativeDecoder=True,
               note='No cache/client writes or loaded-client acceptance')
    (OUT/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')
    (ROOT/'build/clue/watson-boards/latest-verify.txt').write_text(str(OUT))
    print('Boards verified:',OUT,flush=True)

if __name__=='__main__':main()
