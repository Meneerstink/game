"""Verify only the light-box delta against current server/client outputs; no window/server/cache writes."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/clue/lightbox'/('verify-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
NEXT=ROOT/'build/clue/lightbox/next'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
CACHE=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'


def jar(module):return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    OUT.mkdir(parents=True)
    for part in ['core','plugins','tests','client']: (OUT/part).mkdir()
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    libraries=[str(ROOT/p) for p in ['game/build/libs/game-0.0.4.jar','game/plugins/build/libs/plugins-0.0.4.jar']]
    libraries += [str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')]
    cp=[str(OUT/'core'),str(OUT/'plugins')]+libraries
    testing=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-test/1.8.0','org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
        'junit/junit','org.hamcrest/hamcrest-core','org.jetbrains.kotlin/kotlin-reflect/1.8.0',
        'net.bytebuddy/byte-buddy/1.12.20','net.bytebuddy/byte-buddy-agent/1.12.20','org.objenesis/objenesis/3.3',
        'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4']]
    testing += [jar('io.mockk/'+m+'/1.13.4') for m in ['mockk-agent-api-jvm','mockk-agent-jvm','mockk-core-jvm','mockk-dsl-jvm','mockk-jvm']]
    staged='--staged' in sys.argv
    def source(name,canonical): return NEXT/(name+'.kt') if staged else ROOT/canonical/(name+'.kt')
    core=[source('ItemAttribute','game/src/main/kotlin/gg/rsmod/game/model/item'),
          source('ClueLightBoxCacheTool','game/src/main/kotlin/gg/rsmod/game/tools/importer')]
    plugin=[source(name,'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls') for name in
            ['TreasureTrailLightPuzzle','TreasureTrailLightBoxes']]
    plugin += [source(name,'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls') for name in
               ['TreasureTrailMasterPuzzles','TreasureTrailPuzzleBoxes']]
    plugin += [source('Bank','game/plugins/src/main/kotlin/gg/rsmod/plugins/content/inter/bank')]
    tests=[source('TreasureTrailLightBoxTests','game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls')]
    tests += [source('TreasureTrailMasterPuzzleTests','game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls')]
    inputs=core+plugin+tests+[ROOT/'tools/clue/ClueLightBoxClientRegression.java']+list((ROOT/'docs/CLUE/cache').glob('lightbox-*.json'))
    hashes={str(p):sha256(p.read_bytes()).hexdigest() for p in inputs}
    def run(args,output=None):
        print('Running',str(args[0]).split('/')[-1],flush=True)
        result=subprocess.run(list(map(str,args)),cwd=ROOT,check=False,stdout=output,stderr=subprocess.STDOUT if output else None,
                              creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
        if result.returncode:
            if output:
                output.flush();print(Path(output.name).read_text(errors='replace')[-5000:])
            raise SystemExit('Light box check failed with exit '+str(result.returncode))
        return result
    def compile(sources,part):
        args=['-no-stdlib','-no-reflect','-jvm-target','11','-nowarn',
              '-Xfriend-paths='+str(ROOT/'game/plugins/build/libs/plugins-0.0.4.jar'),
              '-cp',';'.join(cp+testing),'-d',str(OUT/part)]+list(map(str,sources))
        argfile=OUT/(part+'.args');argfile.write_text('\n'.join('"'+str(a).replace('\\','/')+'"' for a in args))
        run([JAVA/'java.exe','-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)])
    compile(core,'core');compile(plugin,'plugins');compile(tests,'tests')
    names=['gg.rsmod.plugins.content.scrolls.TreasureTrailLightBoxTests']
    if len(tests)>1:names += ['gg.rsmod.plugins.content.scrolls.TreasureTrailMasterPuzzleTests']
    with (OUT/'tests.txt').open('w') as result:
        run([JAVA/'java.exe','-Xmx256m','-Drsps.data='+str(ROOT/'data'),'-cp',';'.join([str(OUT/'tests')]+cp+testing),
             'org.junit.runner.JUnitCore']+names,result)
    print((OUT/'tests.txt').read_text(errors='replace')[-2200:],flush=True)
    native=ROOT/'../..'/'2011scape-client/2011scape-client/runescape/build/classes/java/main'
    clientcp=[str(native.resolve())]+cp
    run([JAVA/'javac.exe','-cp',';'.join(clientcp),'-d',OUT/'client',ROOT/'tools/clue/ClueLightBoxClientRegression.java'])
    run([JAVA/'java.exe','-Xmx256m','-cp',';'.join([str(OUT/'client')]+clientcp),
         'ClueLightBoxClientRegression',ROOT/'docs/CLUE/cache'])
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in hashes.items())
    proof=dict(unchanged=True,inputs=hashes,tests=(OUT/'tests.txt').read_text(errors='replace'),nativeDecoder=True,
        note='Source/API tests and native decoder/CS2 semantic checks; loaded-client verification remains Claude')
    (OUT/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')
    (ROOT/'build/clue/lightbox/latest-verify.txt').write_text(str(OUT))
    print('Light box delta verified:',OUT,flush=True)


if __name__=='__main__':main()
