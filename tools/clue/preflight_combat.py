"""Supplemental CLUE sequence PLAN/PREFLIGHT, never apply or runtime mapping writes."""
from pathlib import Path
import json
import subprocess
from preflight_stashes import ROOT,snapshot


def main():
    out=ROOT/'build/clue/combat-preflight';out.mkdir(parents=True,exist_ok=True)
    cp=';'.join(str(ROOT/p) for p in ['build/clue/compile/game','game/build/classes/kotlin/main','game/build/libs/game-0.0.4.jar','game/build/install/game/lib/*'])
    command=['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe','-Xmx768m','-cp',cp,
             'gg.rsmod.game.tools.importer.OsrsFxImportTool','clue-guard-combat']
    before=snapshot()
    with (out/'result.txt').open('w') as stream:
        result=subprocess.run(command,cwd=ROOT,stdout=stream,stderr=subprocess.STDOUT,
                              creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after=snapshot()
    proof=dict(command=command,exit=result.returncode,before=before,after=after,unchanged=before==after)
    (out/'proof.json').write_text(json.dumps(proof,indent=2)+'\n')
    text=(out/'result.txt').read_text(errors='replace')
    print(text[-5000:]);assert before==after;assert result.returncode==0
    assert 'APPLIED' not in text and 'PREFLIGHT' in text
    (ROOT/'docs/CLUE/COMBAT-PREFLIGHT.txt').write_text(text)
    (ROOT/'docs/CLUE/validation/combat-preflight-read-only.json').write_text(json.dumps(proof,indent=2)+'\n')
    print('Both caches/ledger unchanged. No NPC/item/UI roster change.')


if __name__=='__main__':main()
