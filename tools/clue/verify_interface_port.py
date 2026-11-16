"""Small, explicit CLUE codec regressions; no cache writes, client build, window or server.

Compiles only three changed server files and the staged client Component against
existing build outputs. All products stay under this worktree's build/clue.
"""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(os.environ['USERPROFILE']) / '.gradle/caches/modules-2/files-2.1'
JAVA = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
OUT = ROOT / 'build/clue/interface-port'
CLIENT = Path('C:/RSPS/2011scape-client/2011scape-client')


def run(args, cwd=ROOT):
    subprocess.run([str(a) for a in args], cwd=cwd, check=True,
                   creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)


def jar(module):
    return str(sorted((CACHE / module).rglob('*.jar'))[-1])


def argument_file(name, args):
    OUT.mkdir(parents=True, exist_ok=True)
    path = OUT / name
    path.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
    return '@' + str(path)


def main():
    compiler = [jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
                'org.jetbrains.kotlin/kotlin-stdlib/1.8.0', 'org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
                'org.jetbrains.kotlin/kotlin-reflect/1.8.0', 'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains/annotations']]
    installed = list((ROOT / 'game/build/install/game/lib').glob('*.jar'))
    assert installed, 'Existing worktree server libraries are required; this verifier never starts a build'
    cp = [str(ROOT / 'game/build/classes/kotlin/main')] + [str(p) for p in installed]
    sources = [ROOT / 'game/src/main/kotlin/gg/rsmod/game/tools/importer' / (name + '.kt') for name in
               ['ClueInterfaceImportTool', 'OsrsInterfacePortTool', 'InterfaceHookProbeTool', 'CluePuzzleInterfaceBake']]
    print('Compiling only the four changed CLUE server codec files (512m, low priority)', flush=True)
    run([JAVA/'java.exe', '-Xmx512m', '-cp', ';'.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         argument_file('server.args', ['-no-stdlib', '-no-reflect', '-jvm-target', '11', '-cp', ';'.join(cp), '-d', OUT/'server'] + sources)])
    cp = [str(OUT/'server')] + cp
    testing = [jar(m) for m in ['org.jetbrains.kotlin/kotlin-test/1.8.0', 'org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
                               'junit/junit', 'org.hamcrest/hamcrest-core']]
    run([JAVA/'java.exe', '-Xmx256m', '-cp', ';'.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         argument_file('tests.args', ['-no-stdlib', '-no-reflect', '-jvm-target', '11', '-cp', ';'.join(cp+testing),
             '-d', OUT/'tests', ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/ClueInterfaceImportTests.kt',
             ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/ClueMapInterfaceImportTests.kt',
             ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer/CluePuzzleInterfaceTests.kt'])])
    run([JAVA/'java.exe', '-Xmx128m', '-cp', ';'.join([str(OUT/'tests')]+cp+testing),
         '-Drsps.data='+str(ROOT/'data'), 'org.junit.runner.JUnitCore', 'gg.rsmod.game.tools.importer.ClueInterfaceImportTests', 'gg.rsmod.game.tools.importer.ClueMapInterfaceImportTests', 'gg.rsmod.game.tools.importer.CluePuzzleInterfaceTests'], ROOT/'game')
    run([JAVA/'javac.exe', argument_file('fixtures.args', ['-cp', ';'.join(cp), '-d', OUT/'fixtures',
         ROOT/'tools/clue/ClueInterfaceFixtureProbe.java'])])
    run([JAVA/'java.exe', '-Xmx128m', '-cp', ';'.join([str(OUT/'fixtures')]+cp), 'ClueInterfaceFixtureProbe',
         ROOT/'docs/CLUE/cache/interfaces.json', OUT/'fixtures'])
    run(['python', ROOT/'tools/clue/prepare_client_patch.py'])
    client_cp = [str(CLIENT/'runescape/build/classes/java/main'), jar('org.openrs2/openrs2-deob-annotations')]
    run([JAVA/'javac.exe', argument_file('client.args', ['-cp', ';'.join(client_cp+cp), '-d', OUT/'client',
         ROOT/'build/clue/client-patch/Component.java', ROOT/'tools/clue/ClueWideInterfaceRegression.java',
         ROOT/'tools/clue/ClueMapClientRegression.java', ROOT/'tools/clue/CluePuzzleClientRegression.java'])])
    run([JAVA/'java.exe', '-Xmx128m', '-cp', ';'.join([str(OUT/'client')]+client_cp), 'ClueWideInterfaceRegression', OUT/'fixtures'])
    run([JAVA/'java.exe', '-Xmx128m', '-cp', ';'.join([str(OUT/'client')]+client_cp+cp),
         'ClueMapClientRegression', ROOT/'docs/CLUE/cache/map-interfaces.json'])
    run([JAVA/'java.exe', '-Xmx128m', '-cp', ';'.join([str(OUT/'client')]+client_cp+cp),
         'CluePuzzleClientRegression', ROOT/'docs/CLUE/cache/puzzle-interface.json'])
    print('CLUE codec regressions passed; canonical caches/client untouched', flush=True)


if __name__ == '__main__':
    main()
