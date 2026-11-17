"""Pin phase-three open questions to public MediaWiki revisions (research only)."""
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

OUT = Path(__file__).parent / 'phase3-sources'
TITLES = [
    'Thunder khopesh (Deadman Mode)', 'Transcript:Thunder khopesh',
    'Trinket of avarice', 'Transcript:Trinket of avarice',
    'Deadman starter pack', 'Tuna (Deadman starter pack)',
    'Update:Deadman: Annihilation Tweaks, Shellbane Gryphon CAs & More!',
    'Transcript:Financial Wizard', 'Transcript:Financial Seer',
    'Imbued ancient cape', 'Armageddon cape fabric', 'Salarin the twisted',
    'Scythe of Vitur', 'Corrupted Scythe of Vitur',
    'Deadman Reward Store', 'Trinket of vengeance',
    'Imbued ancient cape (Deadman)', 'Thunder khopesh',
]

def main():
    OUT.mkdir(exist_ok=True)
    manifest = []
    for start in range(0, len(TITLES), 4):
        params = dict(action='query', prop='revisions', rvprop='ids|timestamp|content',
                      rvslots='main', format='json', formatversion=2, redirects=1,
                      titles='|'.join(TITLES[start:start+4]))
        url = 'https://oldschool.runescape.wiki/api.php?' + urllib.parse.urlencode(params)
        req = urllib.request.Request(url, headers={'User-Agent': 'rsps-research'})
        with urllib.request.urlopen(req, timeout=45) as response:
            raw = response.read()
        (OUT/f'batch-{start//4}.json').write_bytes(raw)
        data = json.loads(raw)
        for page in data['query']['pages']:
            if 'revisions' not in page:
                manifest.append(dict(page=page['title'], missing=True))
                print(page['title'], 'MISSING')
                continue
            rev = page['revisions'][0]
            body = rev['slots']['main']['content'].encode('utf-8')
            filename = urllib.parse.quote(page['title'].replace(' ', '_'), safe="_-'()") + '.wiki.txt'
            (OUT/filename).write_bytes(body)
            manifest.append(dict(page=page['title'], revision=rev['revid'], timestamp=rev['timestamp'],
                file=filename, sha256=hashlib.sha256(body).hexdigest(),
                url='https://oldschool.runescape.wiki/w/Special:PermanentLink/'+str(rev['revid'])))
            print(page['title'], rev['revid'], rev['timestamp'])
    (OUT/'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False)+'\n', encoding='utf-8')

if __name__ == '__main__':
    main()
