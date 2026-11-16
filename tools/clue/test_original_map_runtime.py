"""Compile staged map-library delta and explicit tests against the accepted actual-source cohort."""
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
STAGE = ROOT/'build/clue/original-maps/next'
OUT = ROOT/'build/clue/original-maps/runtime-tests'
CACHE = Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'


def jar(module):
    return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    compiler = [jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0', 'org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0', 'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains/annotations']]
    modules = ['org.jetbrains.kotlin/kotlin-test/1.8.0', 'org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
        'junit/junit', 'org.hamcrest/hamcrest-core', 'org.jetbrains.kotlin/kotlin-reflect/1.8.0',
        'net.bytebuddy/byte-buddy/1.12.20', 'net.bytebuddy/byte-buddy-agent/1.12.20', 'org.objenesis/objenesis/3.3',
        'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4']
    modules += ['io.mockk/'+name+'/1.13.4' for name in
        ['mockk-agent-api-jvm', 'mockk-agent-jvm', 'mockk-core-jvm', 'mockk-dsl-jvm', 'mockk-jvm']]
    libs = [str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')]
    cp = [str(OUT/'library'), str(ROOT/'build/clue/compile/basic-tests/game'),
          str(ROOT/'build/clue/compile/basic-tests/plugins'), str(ROOT/'game/build/classes/kotlin/main'),
          str(ROOT/'game/plugins/build/classes/kotlin/main')] + libs + [jar(m) for m in modules]
    names = ['TreasureTrailMapsTests', 'TreasureTrailStepCatalogueTests', 'TreasureTrailBasicStepsTests']
    sources = [STAGE/'TreasureTrailMaps.kt'] + [STAGE/(name+'.kt') for name in names]
    frozen = {str(p): sha256(p.read_bytes()).hexdigest() for p in sources}
    frozen.update({str(p):sha256(p.read_bytes()).hexdigest() for p in (STAGE/'data/cfg/clue').glob('*') if p.is_file()})

    def run(args):
        subprocess.run(args, cwd=ROOT, check=True, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)

    def compile(args, file):
        path = OUT/file
        path.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
        run([JAVA, '-Xmx512m', '-cp', ';'.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '@'+str(path)])

    options = ['-no-stdlib', '-no-reflect', '-jvm-target', '11', '-cp', ';'.join(cp)]
    compile(options+['-d', str(OUT/'library'), str(sources[0])], 'library.args')
    compile(options+['-d', str(OUT/'tests')] + list(map(str, sources[1:])), 'tests.args')
    run([JAVA, '-Xmx256m', '-Drsps.data='+str(STAGE/'data'), '-cp', ';'.join([str(OUT/'tests')]+cp),
         'org.junit.runner.JUnitCore'] + ['gg.rsmod.plugins.content.scrolls.'+name for name in names])
    assert all(sha256(Path(p).read_bytes()).hexdigest() == digest for p,digest in frozen.items())
    (OUT/'source-sha256.json').write_text(json.dumps(frozen, indent=2)+'\n')
    print('Staged map/runtime tests passed with unchanged staged source/data. No production/caches changed.')


if __name__ == '__main__':
    main()
