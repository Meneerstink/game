"""Existing Mimic arena batch: PLAN/PREFLIGHT only, never activate the encounter."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess
import sys
from preflight_stashes import ROOT, snapshot


def main():
    out=ROOT/'build/clue/mimic-arena'/datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S')
    out.mkdir(parents=True)
    staged='--staged' in sys.argv
    source=ROOT/('build/clue/mimic-arena/next/OsrsRegionImportTool.kt' if staged else
                 'game/src/main/kotlin/gg/rsmod/game/tools/importer/OsrsRegionImportTool.kt')
    dependency=ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/Rev667WorldCodec.kt'
    folder=source.parent
    extra=[folder/'ClueMimicArenaAssets.kt',folder/'ModernRegionProbeTool.kt']
    hashes={str(p):sha256(p.read_bytes()).hexdigest() for p in [source,dependency]+extra}
    cache=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'
    def jar(m):return str(sorted((cache/m).rglob('*.jar'))[-1])
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    cp=';'.join([str(out/'classes')]+[str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')])
    subprocess.run(['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe','-Xmx512m',
        '-cp',';'.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
        '-jvm-target','11','-cp',cp,'-d',str(out/'classes'),str(source),str(dependency)]+list(map(str,extra)),
        cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    command=['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe','-Xmx768m','-cp',cp,
             'gg.rsmod.game.tools.importer.OsrsRegionImportTool','clue-mimic-arena']
    before=snapshot()
    with (out/'result.txt').open('w') as stream:
        result=subprocess.run(command,cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,
                              creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after=snapshot()
    proof=dict(staged=staged,inputs=hashes,command=command,exit=result.returncode,before=before,after=after,unchanged=before==after)
    (out/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')
    text=(out/'result.txt').read_text(errors='replace');print(text[-6000:])
    assert before==after,'Concurrent cache/ledger application invalidates this preflight; rerun after it'
    assert result.returncode==0 and 'APPLIED' not in text and 'PREFLIGHT' in text
    assert 'BLOCKING' not in text
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in hashes.items())
    (ROOT/'docs/CLUE/MIMIC-ARENA-PREFLIGHT.txt').write_text(text)
    (ROOT/'docs/CLUE/validation/mimic-arena-preflight.json').write_text(json.dumps(proof,indent=2)+'\n')
    (ROOT/'build/clue/mimic-arena/latest.txt').write_text(str(out))
    print('Both caches/ledger unchanged:',out)


if __name__=='__main__':main()
