"""Fresh compile and PLAN/PREFLIGHT only for Bloodhound Dig: source sequence and two existing menus."""
from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
import json
import os
import subprocess
import sys
from preflight_stashes import ROOT, snapshot

JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin')
CACHE=Path(os.environ['USERPROFILE'])/'.gradle/caches/modules-2/files-2.1'


def jar(module):return str(sorted((CACHE/module).rglob('*.jar'))[-1])


def main():
    staged='--staged' in sys.argv
    folder=ROOT/('build/clue/pet-dig/next' if staged else 'game/src/main/kotlin/gg/rsmod/game/tools/importer')
    sources=[folder/(name+'.kt') for name in ['CluePetDigCacheTool','OsrsFxImportTool']]
    sources += [ROOT/'game/src/main/kotlin/gg/rsmod/game/tools/importer/Rev667WorldCodec.kt']
    frozen={str(p):sha256(p.read_bytes()).hexdigest() for p in sources}
    out=ROOT/'build/clue/pet-dig'/('preflight-'+datetime.now(timezone.utc).strftime('%Y%m%d-%H%M%S'))
    out.mkdir(parents=True)
    compiler=[jar(m) for m in ['org.jetbrains.kotlin/kotlin-compiler-embeddable/1.8.0',
        'org.jetbrains.kotlin/kotlin-stdlib/1.8.0','org.jetbrains.kotlin/kotlin-script-runtime/1.8.0',
        'org.jetbrains.kotlin/kotlin-reflect/1.8.0','org.jetbrains.intellij.deps/trove4j','org.jetbrains/annotations']]
    cp=[str(out/'classes'),str(ROOT/'game/build/libs/game-0.0.4.jar')]
    cp += [str(p) for p in (ROOT/'game/build/install/game/lib').glob('*.jar')]
    subprocess.run(list(map(str,[JAVA/'java.exe','-Xmx512m','-cp',';'.join(compiler),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-jvm-target','11',
        '-cp',';'.join(cp),'-d',out/'classes']+sources)),cwd=ROOT,check=True,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    env=dict(os.environ,RSPS_OSRS_SOURCE_CACHE=str(ROOT/'build/clue/pet-dig/source-2762/cache'))
    assert Path(env['RSPS_OSRS_SOURCE_CACHE']).is_dir(),'Run prepare_pet_dig_source.py first'
    commands=[['gg.rsmod.game.tools.importer.OsrsFxImportTool','clue-pet-dig'],
              ['gg.rsmod.game.tools.importer.CluePetDigCacheTool',str(out/'menus.tsv')]]
    before=snapshot()
    exits=[]
    with (out/'result.txt').open('w') as stream:
        for args in commands:
            result=subprocess.run([str(JAVA/'java.exe'),'-Xmx768m','-cp',';'.join(cp)]+args,
                cwd=ROOT,env=env,stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
            exits.append(result.returncode)
            if result.returncode:break
    after=snapshot()
    proof=dict(staged=staged,commands=commands,source=env['RSPS_OSRS_SOURCE_CACHE'],inputs=frozen,
               before=before,after=after,unchanged=before==after,exits=exits)
    (out/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')
    text=(out/'result.txt').read_text(errors='replace');print(text[-5500:])
    assert before==after,'Caches/ledger changed during preflight; do not accept concurrent application'
    assert exits==[0,0] and 'REFUSED' not in text and 'APPLIED' not in text
    assert all(sha256(Path(p).read_bytes()).hexdigest()==h for p,h in frozen.items())
    (ROOT/'build/clue/pet-dig/latest-preflight.txt').write_text(str(out))
    print('Both caches/ledger unchanged:',out)


if __name__=='__main__':main()
