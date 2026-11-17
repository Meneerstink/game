"""Compile the actual 107-row plugin delta and only its four changed test classes."""
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
STAGED_ALIAS = '--staged-traiborn' in sys.argv
OUT = ROOT/('build/clue/traiborn/staged-tests' if STAGED_ALIAS else 'build/clue/unique-additions/actual-tests')
CACHE = Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'


def jar(module):
    return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT/'library').mkdir(exist_ok=True)
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
    # Gradle may be compiling a frozen production snapshot while this ignored
    # staged delta runs. Snapshot the completed jars; mutable class directories
    # can be temporarily empty and must not fall through to old installDist jars.
    import shutil
    snapshots = OUT/'dependencies'
    snapshots.mkdir(exist_ok=True)
    for module in ['game', 'plugins']:
        path = ROOT/('game/build/libs/game-0.0.4.jar' if module == 'game' else 'game/plugins/build/libs/plugins-0.0.4.jar')
        shutil.copy2(path, snapshots/(module+'.jar'))
    cp = [str(OUT/'library'), str(snapshots/'game.jar'), str(snapshots/'plugins.jar')] + libs + [jar(m) for m in modules]
    names = ['TreasureTrailNpcStepsTests', 'TreasureTrailNumberChallengeTests',
             'TreasureTrailStepCatalogueTests', 'TreasureTrailBasicStepsTests']
    libraries = [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'/(name+'.kt') for name in
                 ['TreasureTrailNpcSteps', 'TreasureTrailNumberDialogue', 'TreasureTrailBasicSteps']]
    tests = [ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls'/(name+'.kt') for name in names]
    if STAGED_ALIAS:
        stage = ROOT/'build/clue/traiborn/next'
        libraries = [stage/p.name if (stage/p.name).is_file() else p for p in libraries]
        tests = [stage/p.name if (stage/p.name).is_file() else p for p in tests]
    frozen = {str(p): sha256(p.read_bytes()).hexdigest() for p in libraries+tests}
    frozen.update({str(p):sha256(p.read_bytes()).hexdigest() for p in (ROOT/'data/cfg/clue').glob('*') if p.is_file()})

    def run(args):
        subprocess.run(args, cwd=ROOT, check=True, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)

    def compile(sources, target, file):
        path = OUT/file
        args = ['-no-stdlib', '-no-reflect', '-jvm-target', '11', '-nowarn', '-cp', ';'.join(cp), '-d', str(target)]
        args += list(map(str,sources))
        path.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args))
        run([JAVA, '-Xmx512m', '-cp', ';'.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '@'+str(path)])

    compile(libraries, OUT/'library', 'library.args')
    compile(tests, OUT/'tests', 'tests.args')
    # The repository reuses a JVM for up to25classes: registrations must coexist.
    run([JAVA, '-Xmx256m', '-Drsps.data='+str(ROOT/'data'), '-cp', ';'.join([str(OUT/'tests')]+cp),
         'org.junit.runner.JUnitCore'] + ['gg.rsmod.plugins.content.scrolls.'+name for name in names])
    assert all(sha256(Path(p).read_bytes()).hexdigest() == digest for p,digest in frozen.items())
    (OUT/'source-sha256.json').write_text(json.dumps(frozen, indent=2)+'\n')
    print('Actual unique-addition tests passed with unchanged source/data. No caches changed.')


if __name__ == '__main__':
    main()
