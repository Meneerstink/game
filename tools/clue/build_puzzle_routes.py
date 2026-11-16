"""Pin the two selected NPC puzzle routes without assuming adjacent item IDs."""
from pathlib import Path
from hashlib import sha256
import json
import re
from build_step_catalogue import templates

ROOT = Path(__file__).resolve().parents[2]

def main():
    folder=ROOT/'docs/CLUE/sources'
    pins={r['page']:r for r in json.loads((folder/'manifest.json').read_text())['pages'] if 'file' in r}
    all_steps={r['key']:r for r in json.loads((ROOT/'docs/CLUE/steps-source.json').read_text())}
    for row in json.loads((ROOT/'data/cfg/clue/steps.json').read_text()):
        pins[row['key']]=dict(file=row['sourceFile'],url=row['source'],sha256=row['sourceSha256'],
                              revision=all_steps.get(row['key'],{}).get('revision'))
    pages=['Puzzle box','Clue scroll (hard) - BLUE GRIM GUIDED','Clue scroll (hard) - IWPPLQTP',
           'Transcript:Lumbridge Guide','Transcript:Gunnjorn']
    for page in pages:
        pin=pins[page]
        assert sha256((folder/pin['file']).read_bytes()).hexdigest()==pin['sha256'],page
    fields=next(templates((folder/pins['Puzzle box']['file']).read_text(),'Infobox Item'))
    tree={int(x) for x in fields['id2'].split(',')}
    reference=Path('C:/RSPS/reference/runelite/gameval/ItemID.java')
    raw=reference.read_bytes()
    symbols={m[0]:int(m[1]) for m in re.findall(r'public static final int (\w+) = (\d+);',raw.decode())}
    routes=[]
    for page in pages[1:3]:
        clue=int(next(templates((folder/pins[page]['file']).read_text(),'Infobox Item'))['id'])
        names=[k for k,v in symbols.items() if v==clue]
        assert len(names)==1
        box_name=names[0]+'_PUZZLEBOX'
        box=symbols[box_name]
        assert box in tree
        routes.append(dict(key=page,clue=clue,box=box,picture=2,clueSymbol=names[0],boxSymbol=box_name,
                           source=pins[page]))
    assert [r['box'] for r in routes]==[19887,23173]
    result=dict(routes=routes,sources={page:pins[page] for page in pages},
                identityReference=dict(file=str(reference),sha256=sha256(raw).hexdigest(),
                    note='Explicit symbolic companion names, not adjacent-number inference'),
                rawItemEvidence='puzzle-source-items.json')
    (ROOT/'docs/CLUE/cache/puzzle-routes.json').write_text(json.dumps(result,indent=2)+'\n')
    print('Two exact tree-picture routes, five Wiki hashes and explicit clue/box gameval symbols verified')

if __name__=='__main__':main()
