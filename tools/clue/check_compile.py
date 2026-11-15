"""Compile this card's changed Kotlin libraries against existing jars before Gradle.

Writes only build/clue/compile in this worktree. Scripts are checked by Gradle's
script task because they require the project's script-definition plugin.
"""
from pathlib import Path
import subprocess
import os
import sys

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path(os.environ['USERPROFILE']) / '.gradle/caches/modules-2/files-2.1'
java = Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe')
compiler_modules = ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
                    'org.jetbrains.kotlin/kotlin-stdlib/1.8.0',
                    'org.jetbrains.kotlin/kotlin-stdlib-common/1.8.0',
                    'org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
                    'org.jetbrains.kotlin/kotlin-reflect/1.8.0',
                    'org.jetbrains.intellij.deps/trove4j', 'org.jetbrains/annotations']
compiler = [str(next((CACHE / module).rglob('*.jar'))) for module in compiler_modules]
libs = list((ROOT / 'game/build/install/game/lib').glob('*.jar'))
base = sys.argv[1] if len(sys.argv) > 1 else 'HEAD'
changed = subprocess.check_output(['git', 'diff', '--name-only', base], cwd=ROOT, text=True).splitlines()
untracked = subprocess.check_output(['git', 'ls-files', '--others', '--exclude-standard'], cwd=ROOT, text=True).splitlines()
sources = sorted(set(changed + untracked))
sources = [ROOT / s for s in sources if '/src/main/kotlin/' in s and s.endswith('.kt') and (ROOT / s).is_file()]
out = ROOT / 'build/clue/compile'
selected = sys.argv[2] if len(sys.argv) > 2 else None
if selected in ('step-tests', 'npc-tests', 'mimic-tests', 'stash-tests', 'stash-import-tests', 'watson-tests', 'stash-chart-tests'):
    out /= selected # Independent test modes cannot reuse a stale patch from another mode.
if selected == 'stash-chart-tests':
    sources = [ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/ClueStashChartCacheTool.kt'] + [
        ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'/(name+'.kt') for name in
        ['TreasureTrailStepCatalogue', 'TreasureTrailStashes', 'TreasureTrailStashChart']]
elif selected == 'watson-tests':
    sources = [ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/ClueWatsonCacheTool.kt'] + [
        ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/watson'/(name+'.kt') for name in ['WatsonAppearance', 'WatsonDialogue']]
elif selected == 'step-tests':
    # Self-contained across a fresh output directory: do not depend on classes
    # left by a previous invocation after HEAD moved to the next small commit.
    sources = [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'/(name+'.kt') for name in
               ['TreasureTrailChallenges', 'TreasureTrailStepCatalogue', 'ClueScrollManager',
                'ClueDeathItems', 'TreasureTrailTornParts']]
elif selected == 'stash-import-tests':
    sources = [ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer'/(name+'.kt') for name in
               ['ClueStashCacheTool', 'Rev667WorldCodec', 'ModelArchive']] + [ROOT/'game/src/main/kotlin/gg/rsmod/game/fs/def/ObjectDef.kt']
elif selected == 'stash-tests':
    sources = [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/scrolls'/(name+'.kt') for name in
               ['TreasureTrailStepCatalogue', 'TreasureTrailStashes', 'TreasureTrailStashWorld']]
elif selected == 'npc-tests':
    # Repository source is compiled with its tests below. Normal core/plugin
    # integration must additionally pass Gradle on frozen input.
    sources = []
elif selected == 'mimic-tests':
    sources = [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/areas/watson/MimicCombat.kt']
for module in (['game','plugins'] if selected in ('watson-tests', 'stash-chart-tests') else ['game'] if selected in ('core-tests', 'npc-tests', 'stash-import-tests') else ['plugins'] if selected in ('step-tests', 'mimic-tests', 'stash-tests', 'stash-import-tests') else [selected] if selected else ['game', 'plugins']):
    batch = [p for p in sources if ('/plugins/' in p.as_posix()) == (module == 'plugins')]
    if not batch:
        continue
    target = out / module
    target.mkdir(parents=True, exist_ok=True)
    cp = [str(out / 'game'), str(ROOT / 'game/build/classes/kotlin/main'),
          str(ROOT / 'game/plugins/build/classes/kotlin/main')] + [str(p) for p in libs]
    args = ['-no-stdlib', '-no-reflect', '-jvm-target', '11', '-nowarn', '-module-name', module,
            '-Xfriend-paths=' + str(ROOT / ('game/plugins/build/classes/kotlin/main' if module == 'plugins' else 'game/build/classes/kotlin/main')),
            '-cp', ';'.join(cp), '-d', str(target)] + [str(p) for p in batch]
    argfile = out / f'{module}.args'
    argfile.write_text('\n'.join('"' + a.replace('\\', '/') + '"' for a in args), encoding='utf-8')
    print(f'Compiling {len(batch)} {module} files', flush=True)
    subprocess.run([str(java), '-Xmx512m' if selected and selected.endswith('-tests') else '-Xmx2g', '-cp', ';'.join(compiler),
                    'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '@' + str(argfile)], cwd=ROOT, check=True,
                   creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)

if selected == 'core-tests':
    modules = ['org.jetbrains.kotlin/kotlin-test/1.8.0', 'org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
               'junit/junit', 'org.hamcrest/hamcrest-core']
    testing = [str(sorted((CACHE/m).rglob('*.jar'))[-1]) for m in modules]
    cp = [str(out/'game'), str(ROOT/'game/build/classes/kotlin/main')] + [str(p) for p in libs] + testing
    test_sources = [ROOT/'game/src/test/kotlin/gg/rsmod/game/tools/importer'/name for name in
                    ['ClueForearmImportTests.kt','ClueImportDryRunTests.kt']]
    target = out/'tests'; target.mkdir(exist_ok=True)
    args = ['-no-stdlib','-no-reflect','-jvm-target','11','-cp',';'.join(cp),'-d',str(target)] + [str(p) for p in test_sources]
    argfile=out/'tests.args'
    argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
    subprocess.run([str(java),'-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)],
                   cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    subprocess.run([str(java),'-Xmx512m','-cp',';'.join([str(target)]+cp),'org.junit.runner.JUnitCore',
                    'gg.rsmod.game.tools.importer.ClueForearmImportTests','gg.rsmod.game.tools.importer.ClueImportDryRunTests'],
                   cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)

if selected in ('step-tests', 'npc-tests', 'mimic-tests', 'stash-tests', 'stash-import-tests', 'watson-tests', 'stash-chart-tests'):
    # Explicit classes only. This is not a substitute for Gradle script/core integration.
    testing = [str(sorted((CACHE/m).rglob('*.jar'))[-1]) for m in
               ['org.jetbrains.kotlin/kotlin-test/1.8.0', 'org.jetbrains.kotlin/kotlin-test-junit/1.8.0',
                'junit/junit', 'org.hamcrest/hamcrest-core']]
    testing += [str(next((CACHE/'io.mockk'/name/'1.13.4').rglob('*.jar'))) for name in
                ['mockk-agent-api-jvm', 'mockk-agent-jvm', 'mockk-core-jvm', 'mockk-dsl-jvm', 'mockk-jvm']]
    testing += [str(next((CACHE/m).rglob('*.jar'))) for m in
                ['net.bytebuddy/byte-buddy/1.12.20', 'net.bytebuddy/byte-buddy-agent/1.12.20',
                 'org.objenesis/objenesis/3.3', 'org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.6.4',
                 'org.jetbrains.kotlin/kotlin-reflect/1.8.0']]
    cp = [str(out/'game'), str(out/'plugins'), str(ROOT/'game/build/classes/kotlin/main'),
          str(ROOT/'game/plugins/build/classes/kotlin/main')] + [str(p) for p in libs] + testing
    if selected == 'stash-chart-tests':
        names = ['gg.rsmod.game.tools.importer.ClueStashChartImportTests', 'gg.rsmod.plugins.content.scrolls.TreasureTrailStashChartTests']
    elif selected == 'watson-tests':
        names = ['gg.rsmod.game.tools.importer.ClueWatsonCacheTests', 'gg.rsmod.plugins.content.areas.watson.WatsonAppearanceTests']
    elif selected == 'npc-tests':
        names = ['gg.rsmod.game.plugin.ClueNpcOptionOverrideTests']
    elif selected == 'stash-import-tests':
        names = ['gg.rsmod.game.tools.importer.ClueStashImportTests']
    elif selected == 'stash-tests':
        names = ['gg.rsmod.plugins.content.scrolls.TreasureTrailStashTests']
    elif selected == 'mimic-tests':
        names = ['gg.rsmod.plugins.content.areas.watson.TheMimicCombatDefTests']
    else:
        names = ['gg.rsmod.plugins.content.scrolls.'+name for name in
                 ['TreasureTrailChallengeTests', 'TreasureTrailStepCatalogueTests',
                  'ClueScrollLifecycleTests', 'TreasureTrailTornPartsTests']]
    test_sources = [ROOT/('game/plugins/src/test/kotlin' if name.startswith('gg.rsmod.plugins.') else 'game/src/test/kotlin')/
                    (name.replace('.', '/')+'.kt') for name in names]
    if selected == 'npc-tests':
        test_sources.append(ROOT/'game/src/main/kotlin/gg/rsmod/game/plugin/PluginRepository.kt')
    target = out/'tests'; target.mkdir(parents=True, exist_ok=True)
    args = ['-no-stdlib','-no-reflect','-jvm-target','11','-cp',';'.join(cp),'-d',str(target)] + [str(p) for p in test_sources]
    if selected in ('npc-tests', 'watson-tests'):
        args += ['-Xfriend-paths='+str(ROOT/'game/build/classes/kotlin/main')]
    argfile=out/(selected+'.args')
    argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in args),encoding='utf-8')
    subprocess.run([str(java),'-Xmx512m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)],
                   cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    subprocess.run([str(java),'-Xmx256m','-Drsps.data='+str(ROOT/'data'),'-Dclue.fixtures='+str(out/'fixtures'),'-cp',';'.join([str(target)]+cp),'org.junit.runner.JUnitCore'] +
                   names,
                   cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
