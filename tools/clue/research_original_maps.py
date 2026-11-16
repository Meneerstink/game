"""Read-only research for deferred original maps, outside frozen runtime inputs.

Downloads the illustrations named by the pinned Wiki pages, ties their clue IDs
to cache DB rows, and exports source geometry for manual identification. No
interface/model numeric identity is accepted without that comparison.
"""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from pathlib import Path
from PIL import Image
import io
import json
import re
import subprocess
import urllib.request
from fetch_step_sources import query

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'build/clue/original-maps'
JAVA = 'C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'
CP = 'game/build/classes/kotlin/main;game/build/install/game/lib/*'
CACHE = 'C:/RSPS/import-source/openrs2-2686/cache'
ILLUSTRATIONS = {
    337: 'Falador statue crossroads', 338: 'small volcanoes', 339: 'Picnic tables',
    340: 'Miscellania', 341: 'Mort Myre', 342: 'Lava chaos altar',
    343: 'Lighthouse peninsula', 344: 'North Seers', 346: 'Champions guild',
    347: 'South-eastern Varrock Mine', 349: 'Ranging Guild', 352: 'hobgoblin peninsula',
    355: 'McGrubors', 357: 'West Ardougne', 358: 'observatory',
    359: "Dark Warriors' Fortress", 361: 'Clocktower',
}


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    archive = {r['key']: r for r in json.loads((ROOT/'docs/CLUE/steps-source.json').read_text())}
    audit = json.loads((ROOT/'docs/CLUE/ORIGINAL-POOL-AUDIT.json').read_text())
    db = json.loads((ROOT/'build/clue/db-rows.json').read_text())
    rows = []
    for old in audit['records']:
        if old['kind'] != 'MAP' or old['donorInterface'] not in ILLUSTRATIONS:
            continue
        source = archive[old['candidateSource']]
        images = re.findall(r'\[\[File:([^]|]+)', source['fields']['text'])
        image = 'Map clue '+ILLUSTRATIONS[old['donorInterface']]+'.png'
        assert not images or images == [image]
        guide = ROOT/'docs/CLUE/sources'/f"Treasure_Trails__Guide__Maps__{source['tier'].title()}.wiki"
        # Ranging Guild's individual page/cache row exists but the guide omits
        # its illustration. Keep that discrepancy visible for manual review.
        listed = image in guide.read_text()
        assert listed or old['donorInterface'] == 349
        ident = int(source['fields']['id'])
        matches = [(int(k), r) for k, r in db.items() if r.get('table') == 5 and
                   r.get('columns', {}).get('0', {}).get('values') == [ident]]
        assert len(matches) == 1, (source['key'], matches)
        row, definition = matches[0]
        rows.append(dict(key=source['key'], candidateGroup=old['donorInterface'], image=image,
            guideFile=guide.name, guideSha256=sha256(guide.read_bytes()).hexdigest(), guideImageListed=listed,
            sourceFile=source['sourceFile'], sourceSha256=old['sourceSha256'], sourceUrl=source['source'],
            clueId=ident, dbRow=row, dbSha256=definition['sha256'], point=source['points'],
            action='SEARCH' if 'where to search' in source['fields']['text'] else 'DIG'))
    assert len(rows) == 17
    metadata = query(dict(action='query', prop='imageinfo', titles='|'.join('File:'+r['image'] for r in rows),
                          iiprop='url|timestamp|sha1|size', formatversion=2))
    pages = {r['title']: r for r in metadata['query']['pages']}

    def download(row):
        info = pages['File:'+row['image']]['imageinfo'][0]
        req = urllib.request.Request(info['url'], headers={'User-Agent': '78legacy-ClueResearch/1.0'})
        with urllib.request.urlopen(req, timeout=40) as response:
            data = response.read()
        picture = Image.open(io.BytesIO(data))
        assert picture.size == (info['width'], info['height']), (row['image'], picture.size, info)
        (OUT/row['image']).write_bytes(data)
        row.update(imageUrl=info['url'], imageSha256=sha256(data).hexdigest(), imageTimestamp=info['timestamp'],
                   downloadedBytes=len(data), wikiBytes=info['size'], imageFormat=picture.format)

    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(download, rows))
    groups = ','.join(str(r['candidateGroup']) for r in rows)
    for probe, args in [
        ('ClueWidgetProbe.java', [CACHE, str(OUT/'interfaces.json'), groups]),
        ('ClueMapGeometryProbe.java', [CACHE, str(OUT/'interfaces.json'), str(OUT/'source-manifest.json'), str(OUT/'meshes.json')]),
    ]:
        subprocess.run([JAVA, '-Xmx512m', '-cp', CP, str(ROOT/'tools/clue'/probe)] + args,
                       cwd=ROOT, check=True, creationflags=subprocess.BELOW_NORMAL_PRIORITY_CLASS)
    (OUT/'candidates.json').write_text(json.dumps(rows, indent=2)+'\n')
    (OUT/'wiki-images.json').write_text(json.dumps(metadata, indent=2)+'\n')
    print('17 original-map candidates pinned. NOT activated or visually accepted; inspect actual source geometry/images.')


if __name__ == '__main__':
    main()
