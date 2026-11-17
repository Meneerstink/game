"""DMM2 research evidence only. Never writes game data, caches or player saves."""
import hashlib
import json
from pathlib import Path
import re
import urllib.parse
from fetch_wiki import fetch, ROOT


def save(name, value):
    (ROOT / name).write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def supplement_wiki():
    titles = ['Corrupted weapons', 'Trinket (disambiguation)', 'Deadman Points', 'Safe Deposit Box',
              'Template:DeadmanAnnihilationBreachDropTable', 'Infernal Mage',
              'Corrupted Scythe of Vitur', "Corrupted tumeken's shadow"]
    manifest = json.loads((ROOT / 'wiki-manifest.json').read_text(encoding='utf-8'))
    responses = []
    for offset in range(0, len(titles), 4):
        response = fetch(titles[offset:offset + 4])
        responses.append(response)
        for page in response['query']['pages']:
            if not page.get('revisions'):
                continue
            rev = page['revisions'][0]
            content = rev['slots']['main']['content']
            filename = urllib.parse.quote(page['title'].replace(' ', '_'), safe="_-'()") + '.wiki.txt'
            (ROOT / 'wiki' / filename).write_text(content, encoding='utf-8')
            manifest = [x for x in manifest if x['title'] != page['title']]
            manifest.append(dict(title=page['title'], revid=rev['revid'], timestamp=rev['timestamp'], file=filename,
                                 sha256=hashlib.sha256(content.encode()).hexdigest(),
                                 url='https://oldschool.runescape.wiki/w/Special:PermanentLink/' + str(rev['revid'])))
    save('wiki/supplement.json', responses)
    # Preserve missing aliases as history; canonical entries and redirects resolve them explicitly.
    save('wiki-manifest.json', manifest)


def inventory():
    groups = {
        'currency': r'BreachPoints|breach_points|breach point|Breach Trader|givebreach|breachpoints',
        'items-and-npcs': r'trinket|corrupted (?:voidwaker|dragon claws|armadyl|twisted|scythe|tumeken|volatile|dark bow)|khopesh|amulet.of.fire|blood.money|financial.wizard|financial.seer|\bnigel\b',
        'starter': r'NewPlayerStart|PvpRespawnChest|starterChest|starter_pack|STARTER_(?:BOW|STAFF|SWORD)|2233[01357]|2855[579]',
        'deaths-and-persistence': r'ValidPkKill|safe.deposit|safe.box|register_container_key|toPersistentContainers|repairWrapped|containers.values|forcedGrave|alwaysGrave',
        'combat-family': r'ScytheOfVitur|Scythe|shadowMultiplier|class PoweredStaves|object PoweredStaves|VOLATILE_NIGHTMARE|TWISTED_BOW|AMULET_OF_MAGIC',
    }
    compiled = {k: re.compile(v, re.I) for k, v in groups.items()}
    results = []
    roots = [Path('game/plugins/src/main/kotlin'), Path('game/src/main/kotlin'),
             Path('game/plugins/src/test/kotlin'), Path('game/src/test/kotlin')]
    paths = sorted({p for root in roots for p in root.rglob('*') if p.suffix in ('.kt', '.kts')})
    paths += [Path('data/cfg/commands.yml'), Path('data/cfg/new_player.yml')]
    for path in paths:
        if not path.exists():
            continue
        raw = path.read_bytes()
        lines = raw.decode('utf-8-sig').splitlines()
        hits = []
        for number, line in enumerate(lines, 1):
            matched = [key for key, pat in compiled.items() if pat.search(line)]
            if matched:
                hits.append(dict(line=number, groups=matched, text=line))
        if hits:
            results.append(dict(path=path.as_posix(), sha256=hashlib.sha256(raw).hexdigest(), lines=len(lines), hits=hits))
    save('inventory-supplement.json', results)
    out = ['# DMM2 exhaustive lexical inventory supplement', '',
           'Scope: main and test Kotlin, commands.yml and new_player.yml only. Includes comments; a lexical hit is not a call edge.',
           'No .codegraph directory in this worktree; no graph generated or stale main-worktree graph used.', '']
    for record in results:
        out += ['## ' + record['path'], 'SHA256 ' + record['sha256'], '']
        out += [str(hit['line']) + ' [' + ', '.join(hit['groups']) + '] ' + hit['text'] for hit in record['hits']]
        out.append('')
    (ROOT / 'inventory-supplement.md').write_text('\n'.join(out), encoding='utf-8')


def tables():
    source = json.loads((ROOT / 'cache2686-items.json').read_text())
    local = json.loads((ROOT / 'local-all-source-name-matches.json').read_text())
    out = ['# Source item definitions and existing local items', '',
           'Source IDs are OSRS IDs, never assigned local import IDs. Model opcodes: 44 inventory, 45 male wear, 46/47 extra male, 48 female wear, 49/50 extra female; 90-93 heads. Notes/placeholders retain their source links.', '',
           '|Source ID|Name|Models (opcode:id)|Note link|Placeholder|Local name match|', '|---|---|---|---|---|---|']
    for iid, d in sorted(source.items(), key=lambda x: int(x[0])):
        if not d.get('name'):
            continue
        matches = [lid for lid, ld in local.items() if ld.get('name', '').casefold() == d['name'].casefold()]
        out.append('|'+ '|'.join([iid, d['name'], ', '.join(str(k)+':'+str(v) for k,v in d['models'].items()),
                                 str(d['fields'].get('97','-')), str(d['fields'].get('148','-')), ', '.join(matches) or 'absent in local name scan']) + '|')
    (ROOT / 'cache-item-matrix.md').write_text('\n'.join(out)+'\n', encoding='utf-8')
    manifest = json.loads((ROOT / 'wiki-manifest.json').read_text(encoding='utf-8'))
    unique = {x['title']: x for x in manifest if not x.get('missing')}
    out = ['# Wiki revision index', '', 'Retrieved through public MediaWiki API with rsps-research User-Agent on 2026-11-16.', '',
           '|Page|Revision|UTC revision date|Saved source|', '|---|---|---|---|']
    for title, entry in sorted(unique.items()):
        out.append(f"|[{title}]({entry['url']})|{entry['revid']}|{entry['timestamp']}|wiki/{entry['file']}|")
    out += ['', 'Aliases: Corrupted weapons -> Trinket of advanced weaponry; Deadman Points -> Deadman Reward Store; Safe Deposit Box -> Deadman Mode#Bank raiding and insurance. API redirects saved in wiki/supplement.json.',
            'Missing transcript pages for undead, fairies and avarice are evidence gaps, not successful fetches. Case-sensitive scythe and shadow aliases resolved to canonical pages listed above.']
    (ROOT / 'wiki-revisions.md').write_text('\n'.join(out)+'\n', encoding='utf-8')


def validate():
    checks = []
    for entry in json.loads((ROOT / 'wiki-manifest.json').read_text(encoding='utf-8')):
        if entry.get('missing'):
            continue
        content = (ROOT / 'wiki' / entry['file']).read_text(encoding='utf-8')
        checks.append(dict(kind='wiki-hash', title=entry['title'], ok=hashlib.sha256(content.encode()).hexdigest() == entry['sha256']))
    for entry in json.loads((ROOT / 'inventory-supplement.json').read_text(encoding='utf-8')):
        checks.append(dict(kind='current-code-hash', path=entry['path'], ok=hashlib.sha256(Path(entry['path']).read_bytes()).hexdigest() == entry['sha256']))
    for file in ['cache2686-item-errors.json', 'cache2686-interface-errors.json', 'local-item-errors.json']:
        checks.append(dict(kind='decoder-errors-empty', path=file, ok=not json.loads((ROOT/file).read_text())))
    npc = json.loads((ROOT/'cache2686-npcs.json').read_text())
    checks.append(dict(kind='npc-decode', ok=not any('error' in x for x in npc.values())))
    for asset in json.loads((ROOT/'source-assets-presence.json').read_text()):
        checks.append(dict(kind='source-asset-present', type=asset['kind'], id=asset['id'], ok=asset['present']))
    checks.append(dict(kind='local-dependency-errors-empty', ok=not json.loads((ROOT/'local-dependency-errors.json').read_text())))
    save('validation.json', dict(checks=checks, passed=sum(x['ok'] for x in checks), failed=sum(not x['ok'] for x in checks), known_gaps=dict(missing_source_item_candidates=json.loads((ROOT/'source-missing-item-candidates.json').read_text()), unresolved=['Nigel icon exact binding', 'Reward Store interface root', 'POH full style dependencies', 'AV semantic bindings', 'weapon/charge/death source conflicts documented in REPORT V8/V12'])))
    print('Research integrity:', len(checks), 'checks;', sum(not x['ok'] for x in checks), 'failed')


if __name__ == '__main__':
    import sys
    if '--wiki' in sys.argv:
        supplement_wiki()
    inventory()
    tables()
    validate()
