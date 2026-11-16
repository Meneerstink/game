"""Research only: preserve public wiki revision evidence; never changes game data."""
import concurrent.futures
import hashlib
import json
from pathlib import Path
import urllib.parse
import urllib.request

ROOT = Path(__file__).parent
TITLES = [
    'Corrupted weapons', 'Trinket of advanced weaponry',
    'Corrupted armadyl godsword', 'Corrupted dragon claws',
    'Corrupted scythe of Vitur', "Corrupted Tumeken's shadow",
    'Corrupted twisted bow', 'Corrupted voidwaker', 'Corrupted dark bow',
    'Corrupted volatile nightmare staff', 'Trinket of undead',
    'Trinket of fortuity', 'Trinket of fairies', 'Trinket of avarice',
    'Thunder khopesh', 'Amulet of fire', 'Deadman starter pack',
    'Blood money', 'Nigel', 'Deadman Reward Store', 'Transcript:Nigel',
    'Financial Wizard', 'Financial Seer', 'Transcript:Financial Wizard',
    'Transcript:Financial Seer', 'Deadman Mode', 'Deadman: Annihilation',
    'Deadman Points', 'Deadman armour', 'Starter sword', 'Starter bow',
    'Starter staff', "Deadman's skull", "Deadman's chest (scenery)",
    'Armageddon weapon scroll', 'Armageddon cape fabric',
    'Armageddon teleport scroll', 'Armageddon rug',
    'Annihilation weapon scroll', 'Annihilation blueprints',
    'Annihilation teleport scroll', 'File:Deadman Tutor icon.png',
    'Transcript:Trinket of fortuity', 'Transcript:Trinket of advanced weaponry',
    'Transcript:Trinket of undead', 'Transcript:Trinket of fairies',
    'Transcript:Trinket of avarice', 'Fire ruby',
]

def fetch(batch):
    query = urllib.parse.urlencode(dict(action='query', prop='revisions',
        rvprop='ids|timestamp|content', rvslots='main', format='json',
        formatversion=2, redirects=1, titles='|'.join(batch)))
    request = urllib.request.Request('https://oldschool.runescape.wiki/api.php?' + query,
        headers={'User-Agent': 'rsps-research'})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)

if __name__ == '__main__':
    destination = ROOT / 'wiki'
    destination.mkdir(parents=True, exist_ok=True)
    manifest = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        for index, data in enumerate(pool.map(fetch, [TITLES[i:i+8] for i in range(0, len(TITLES), 8)])):
            (destination / f'batch-{index}.json').write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')
            for page in data['query']['pages']:
                if 'revisions' not in page:
                    manifest.append({'title': page['title'], 'missing': True})
                    continue
                rev = page['revisions'][0]
                content = rev['slots']['main']['content']
                filename = urllib.parse.quote(page['title'].replace(' ', '_'), safe="_-'()") + '.wiki.txt'
                (destination / filename).write_text(content, encoding='utf-8')
                manifest.append(dict(title=page['title'], revid=rev['revid'], timestamp=rev['timestamp'], file=filename,
                    sha256=hashlib.sha256(content.encode('utf-8')).hexdigest(),
                    url='https://oldschool.runescape.wiki/w/Special:PermanentLink/'+str(rev['revid'])))
    (ROOT / 'wiki-manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
    for item in manifest:
        print(item['title'], item.get('revid', 'MISSING'), item.get('timestamp', ''))
