"""Run only the map test against the freshly compiled basic production cohort."""
from pathlib import Path
import json, subprocess
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/clue/compile/basic-tests'
arguments=[json.loads(line) for line in (OUT/'basic-tests.args').read_text().splitlines()]
arguments=arguments[:arguments.index('-d')+2]+[str(ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/scrolls/TreasureTrailMapsTests.kt')]
argfile=OUT/'map-test.args'
argfile.write_text('\n'.join(json.dumps(a.replace('\\','/')) for a in arguments))
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
compiler=[str(next((cache/m).rglob('*.jar'))) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0','org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0','org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
java='C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'
with (OUT/'map-test-result.txt').open('w') as log:
 result=subprocess.run([java,'-Xmx256m','-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT,cwd=ROOT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
 assert result.returncode==0
 cp=str(OUT/'tests')+';'+arguments[arguments.index('-cp')+1]
 result=subprocess.run([java,'-Xmx256m','-Drsps.data='+str(ROOT/'data'),'-cp',cp,'org.junit.runner.JUnitCore','gg.rsmod.plugins.content.scrolls.TreasureTrailMapsTests'],stdout=log,stderr=subprocess.STDOUT,cwd=ROOT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
print((OUT/'map-test-result.txt').read_text(errors='replace')[-1600:])
assert result.returncode==0
