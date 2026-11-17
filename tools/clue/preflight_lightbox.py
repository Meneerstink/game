"""Light-box PLAN/PREFLIGHT only, using the just-verified delta; never passes an apply argument."""
from hashlib import sha256
from pathlib import Path
import json
import re
import subprocess
from preflight_stashes import ROOT, snapshot


def main():
    verified=Path((ROOT/'build/clue/lightbox/latest-verify.txt').read_text().strip())
    proof=json.loads((verified/'proof.json').read_text())
    assert proof['unchanged'] and proof['nativeDecoder']
    assert all(sha256(Path(p).read_bytes()).hexdigest()==digest for p,digest in proof['inputs'].items())
    arguments=[json.loads(line) for line in (verified/'core.args').read_text().splitlines()]
    cp=arguments[arguments.index('-cp')+1]
    runtime=ROOT/'data/cfg/clue/lightbox.tsv'
    command=['C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe','-Xmx512m','-cp',cp,
        'gg.rsmod.game.tools.importer.ClueLightBoxCacheTool',str(ROOT/'docs/CLUE/cache/lightbox-interface.json'),
        str(ROOT/'docs/CLUE/cache/lightbox-scripts.json'),str(ROOT/'docs/CLUE/ITEM-PREFLIGHT.tsv'),
        str(runtime),str(ROOT/'docs/CLUE/LIGHTBOX-PREFLIGHT.tsv')]
    out=ROOT/'build/clue/lightbox/preflight';out.mkdir(parents=True,exist_ok=True)
    def state():
        value=snapshot()
        value['lightboxMapping']=sha256(runtime.read_bytes()).hexdigest() if runtime.exists() else None
        for name in ['items.tsv','import-items.tsv','interfaces.tsv']:
            p=ROOT/'data/cfg/clue'/name
            value[name]=sha256(p.read_bytes()).hexdigest() if p.exists() else None
        return value
    before=state()
    with (out/'result.txt').open('w') as result:
        finished=subprocess.run(command,cwd=ROOT,stdout=result,stderr=subprocess.STDOUT,
            creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    after=state()
    text=(out/'result.txt').read_text(errors='replace')
    (out/'proof.json').write_text(json.dumps(dict(command=command,exit=finished.returncode,
        before=before,after=after,unchanged=before==after,verified=str(verified)),indent=2)+'\n')
    print(text[-3500:])
    assert finished.returncode==0
    assert before==after,'A cache/ledger/mapping changed; inspect read-only proof'
    assert re.search(r'PREFLIGHT tx-\d{8}-\d{6} mutations=\d+',text) and 'PLAN_ONLY' in text
    print('Light box preflight passed; both caches, ledger, item roster and all runtime mappings unchanged.')


if __name__=='__main__':main()
