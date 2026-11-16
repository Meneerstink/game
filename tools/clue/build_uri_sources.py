"""Audit existing Uri definitions and pin current dialogue; never writes a cache.

The three 667 variants already share one appearance; the five source variants
also share one appearance. Reuse that native actor for elite/78, rather than
allocating duplicate appearances. Actual client appearance remains a review gate.
"""
from pathlib import Path
from hashlib import sha256
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[2]


def main():
    sys.path.insert(0, 'C:/RSPS/tools/cache667')
    from js5 import Cache
    from local_npcs import decode
    manifest = json.loads((ROOT/'docs/CLUE/sources/manifest.json').read_text(encoding='utf8'))
    pins = {row['page']: row for row in manifest['pages']}
    sources = {}
    for page in ['Uri', 'Transcript:Uri']:
        pin = pins[page]
        raw = (ROOT/'docs/CLUE/sources'/pin['file']).read_bytes()
        assert sha256(raw).hexdigest() == pin['sha256']
        sources[page] = pin
    text = (ROOT/'docs/CLUE/sources'/pins['Transcript:Uri']['file']).read_text(encoding='utf8')
    standard = text.split('==Standard dialogue==')[1].split('== Speaking with')[0]
    lines = re.findall(r"\*\* '''Uri:''' (.+)", standard)
    assert len(lines) == len(set(lines)) == 25
    assert "* '''Player:''' What?" in standard
    refusal = re.search(r"== Speaking with.*?\n\* '''Uri:''' ([^\n]+)", text, re.S)[1]
    evidence = json.loads((ROOT/'docs/CLUE/cache/step-assets.json').read_text())['npcs']
    appearance = ['models', 'chatheads', 'size', 'stand', 'walk', 'ops', 'combat', 'recolorFind', 'recolorReplace']
    for source in [1775, 1776, 6584, 7311]:
        assert all(evidence['1774']['definition'][k] == evidence[str(source)]['definition'][k] for k in appearance)
    native = {}
    caches = [Cache(str(path)) for path in [Path('C:/RSPS/game/game/data/cache'), Path('C:/RSPS/file-server/cache')]]
    try:
        for local in [5141, 5142, 5143]:
            raw = [cache.file(18, local >> 7, local & 127) for cache in caches]
            assert raw[0] and raw[0] == raw[1]
            definition = decode(raw[0])
            assert not definition.get('stopped_at')
            assert definition['name'] == 'Uri' and definition['ops'] == ['Talk-to', None, None, None, None]
            assert len(definition['heads']) == 3 and definition['bas'] >= 0
            native[str(local)] = dict(sha256=sha256(raw[0]).hexdigest(), definition=definition)
        assert native['5141']['definition'] == native['5142']['definition'] == native['5143']['definition']
    finally:
        for cache in caches:
            cache.dat.close()
    result = dict(sources=sources, phrases=lines, player='What?', refusal=refusal,
        examine=re.search(r'(?m)^\|examine = (.+)$', (ROOT/'docs/CLUE/sources/Uri.wiki').read_text(encoding='utf8'))[1],
        tiers={tier: dict(source=source, local=local, sourceSha256=evidence[str(source)]['sha256'])
               for tier, source, local in [('easy',1774,5141),('medium',1775,5142),('hard',1776,5143),('elite',6584,5143),('master',7311,5143)]},
        native=native,
        unresolved=dict(lifetimeTicks='No pinned numeric source. Do not invent a timeout.',
                        spawnAnimation='No pinned Uri-specific spawn/vanish sequence; do not guess teleport IDs.'))
    output = ROOT/(sys.argv[1] if len(sys.argv)>1 else 'build/clue/next/uri-source.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2)+'\n', encoding='utf8')
    print('25 current phrases; 5 source / 3 existing native Uri variants verified read-only:', output)


if __name__ == '__main__':
    main()
