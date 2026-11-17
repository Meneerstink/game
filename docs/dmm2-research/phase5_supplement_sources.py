"""Read-only source probes for the phase5 testserver findings. Outputs evidence, never cache data."""
from pathlib import Path
import hashlib
import json
import re
import struct
import urllib.request
import urllib.parse
from probe_cache import Cache, SOURCE, group, decode_interface, items
from osrs_if3 import R

DOC = Path(__file__).resolve().parent
OUT = DOC / 'phase5-sources'
ROOT = DOC.parents[1]


def digest(b):
    return hashlib.sha256(b).hexdigest()


def full_interface(raw):
    """RuneLite InterfaceLoader.decodeIf3; strict to the four types present in these groups."""
    c = decode_interface(raw)
    r = R(raw)
    r.p = 19
    t = c['type']
    if t == 0: r.p += 5
    elif t == 3: r.p += 6
    elif t == 4:
        r.p += 2; r.string(); r.p += 8
    elif t == 5: r.p += 15
    else: raise ValueError(f'Unsupported full type {t}')
    c['clickMask'] = int.from_bytes(raw[r.p:r.p+3], 'big'); r.p += 3
    c['name'] = r.string()
    c['actions'] = [r.string() for _ in range(r.u8())]
    c['dragDeadZone'], c['dragDeadTime'], c['dragRenderBehavior'] = r.u8(), r.u8(), r.u8()
    c['targetVerb'] = r.string()
    def listener():
        out = []
        for _ in range(r.u8()):
            typ = r.u8()
            if typ not in (0, 1): raise ValueError(f'Listener type {typ}')
            out.append(r.i32() if typ == 0 else r.string())
        return out
    names = ['onLoad', 'onMouseOver', 'onMouseLeave', 'onTargetLeave', 'onTargetEnter', 'onVarTransmit',
             'onInvTransmit', 'onStatTransmit', 'onTimer', 'onOp', 'onMouseRepeat', 'onClick',
             'onClickRepeat', 'onRelease', 'onHold', 'onDrag', 'onDragComplete', 'onScrollWheel']
    for name in names:
        values = listener()
        if values: c[name] = values
    for name in ('varTransmitTriggers', 'invTransmitTriggers', 'statTransmitTriggers'):
        values = [r.i32() for _ in range(r.u8())]
        if values: c[name] = values
    assert r.p == len(raw), (r.p, len(raw), c)
    rename = dict(w='width', h='height', wmode='widthMode', hmode='heightMode', xmode='xMode', ymode='yMode',
                  scrollW='scrollWidth', scrollH='scrollHeight', color='colour', lineH='lineHeight',
                  xAlign='alignX', yAlign='alignY', shadowed='textShadow', rotation='angle')
    return {rename.get(k, k): (int(v, 16) if k == 'color' else v) for k, v in c.items()}


def script(raw):
    for footer in (12, 16):
        try:
            end = len(raw) - 2 - int.from_bytes(raw[-2:], 'big') - footer
            count = int.from_bytes(raw[end:end+4], 'big')
            r = R(raw); name = r.string(); out = []
            while r.p < end:
                op = r.u16()
                if op == 3: value = r.string()
                elif op == 54:
                    value = struct.unpack('>q', raw[r.p:r.p+8])[0]; r.p += 8
                elif op >= 100 or op in (21, 38, 39, 63): value = r.u8()
                else: value = r.i32()
                out.append((op, value))
            assert r.p == end and len(out) == count
            return dict(name=name, footer=footer, instructions=out)
        except (AssertionError, IndexError, ValueError, struct.error):
            continue
    raise ValueError('Neither supported ScriptLoader footer decoded exactly')


def scripts():
    source = Cache(str(SOURCE)); source.osrs = True
    out = {}
    todo = [1151, 1158, 703, 7140, 7142, 7246, 8168, 6791, 232]
    # Match the seven actual shop products in source enums/structs; none are invented UI choices.
    matches = {}
    for kind, gid in [('enum', 8), ('struct', 34)]:
        for fid, raw in group(source, 2, gid).items():
            if b'Deadman' in raw or b'deadman' in raw:
                matches[f'{kind}:{fid}'] = dict(sha256=digest(raw), hex=raw.hex(),
                                               strings=[s.decode('latin-1') for s in re.findall(rb'[ -~]{4,}', raw)])
    while todo and len(out) < 180:
        sid = todo.pop(0)
        if str(sid) in out: continue
        raw = source.file(12, sid, 0)
        d = script(raw); d['sha256'] = digest(raw); out[str(sid)] = d
        for op, operand in d['instructions']:
            if op == 40 and str(operand) not in out: todo.append(operand)
    (OUT / 'ui-scripts.json').write_text(json.dumps(out, indent=2), encoding='utf-8')
    (OUT / 'deadman-config-strings.json').write_text(json.dumps(matches, indent=2), encoding='utf-8')
    print('decoded scripts', len(out), 'remaining', todo)
    for key, d in out.items():
        strings = [v for op, v in d['instructions'] if op == 3]
        print(key, strings)


def shop():
    source = Cache(str(SOURCE)); source.osrs = True
    decoded = {str(g): {str(i): full_interface(b) for i, b in group(source, 3, g).items()} for g in (819, 806)}
    (OUT / 'omnishop.json').write_text(json.dumps(decoded, indent=2) + '\n', encoding='utf-8')
    rows = group(source, 2, 38)
    products = {}
    for row, item in [(1355, 29625), (3609, 29628), (3610, 29622), (3611, 29619), (9572, 33012), (9573, 33015), (9574, 33018)]:
        raw = rows[row]
        assert struct.pack('>i', item) in raw
        products[str(row)] = dict(item=item, raw_contains_item=True, sha256=digest(raw), hex=raw.hex())
    for row in (929, 1206):
        products[str(row)] = dict(sha256=digest(rows[row]), hex=rows[row].hex())
    (OUT / 'shop-reward-rows.json').write_text(json.dumps(products, indent=2) + '\n', encoding='utf-8')
    configs = {}
    for gid in source.reference(2):
        for fid, raw in group(source, 2, gid).items():
            if re.search(rb'Deadman Reward|Welcome to the Deadman|No filters available', raw, re.I):
                configs[f'{gid}:{fid}'] = dict(hex=raw.hex(), strings=[s.decode('latin-1') for s in re.findall(rb'[ -~]{4,}', raw)])
    (OUT / 'shop-config.json').write_text(json.dumps(configs, indent=2) + '\n', encoding='utf-8')
    ui = json.loads((OUT / 'interfaces-raw.json').read_text())
    cooked = {g: cs for g, cs in ui.items() if g != '645'}
    cooked.update(decoded)
    for cs in cooked.values():
        for c in cs.values(): c['events'] = {k: c.pop(k) for k in list(c) if k.startswith('on')}
    (DOC / 'dmm2-interfaces.json').write_text(json.dumps(cooked, indent=2) + '\n', encoding='utf-8')
    print('shop row929 + currency1206 + seven product rows verified; exact interface schema generated')


def fetch(params):
    req = urllib.request.Request('https://oldschool.runescape.wiki/api.php?' + urllib.parse.urlencode(params),
                                 headers={'User-Agent': 'rsps-research'})
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def wiki():
    titles = ['Amulet of fire', 'Fire ruby', 'Transcript:Fire ruby', 'Transcript:Archmage Sedridor',
              'Trinket of avarice', 'Deadman Reward Store', 'File:Creating amulet of fire.webp']
    data = fetch(dict(action='query', prop='revisions|imageinfo', rvprop='ids|timestamp|content',
                      rvslots='main', iiprop='url|timestamp|sha1|size', format='json', formatversion=2,
                      redirects=1, titles='|'.join(titles)))
    (OUT / 'wiki.json').write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding='utf-8')
    for page in data['query']['pages']:
        if page['title'] == 'File:Creating amulet of fire.webp' and 'imageinfo' in page:
            url = page['imageinfo'][0]['url']
            request = urllib.request.Request(url, headers={'User-Agent': 'rsps-research'})
            with urllib.request.urlopen(request, timeout=45) as response:
                raw = response.read()
            (OUT / 'creating-fire.webp').write_bytes(raw)
            print('creation visual', len(raw), digest(raw))
    print('wiki', [(p['title'], p.get('revisions', [{}])[0].get('revid', 'missing')) for p in data['query']['pages']])


def cache():
    source = Cache(str(SOURCE)); source.osrs = True
    local = Cache(str(ROOT / 'data/cache'))
    result = {}
    def namehash(s):
        h = 0
        for c in s.lower(): h = (h * 31 + ord(c)) & 0xffffffff
        return h if h < 2**31 else h - 2**32
    for c, label in ((source, 'source'), (local, 'local')):
        c.reference(8)
        fonts = {}
        for name in ('quill_oblique_large', 'p11_full', 'p12_full', 'b12_full', 'q8_full'):
            gid = c.names[8].get(namehash(name))
            fonts[name] = dict(group=gid, sprite_sha256=digest(c.file(8, gid, 0)) if gid is not None else None)
        result[label + '_fonts'] = fonts
    defs = group(source, 2, 10)
    for local_id, source_id in ((23823, 33044), (23822, 28564), (23824, 28570), (23825, 33047)):
        raw = local.file(19, local_id >> 8, local_id & 255)
        result[str(local_id)] = dict(source=source_id, local=items.decode(raw), local_sha256=digest(raw))
    # The reward-store group is supplied by the RuneLite symbolic name in the previous research.
    ui = {}
    for gid in (230, 232, 645):
        ui[str(gid)] = {str(fid): full_interface(raw) for fid, raw in group(source, 3, gid).items()}
    (OUT / 'interfaces-raw.json').write_text(json.dumps(ui, indent=2), encoding='utf-8')
    result['ui'] = {g: dict(components=len(cs), fonts=sorted({c['font'] for c in cs.values() if c.get('font', -1) >= 0}),
                           texts=[c['text'] for c in cs.values() if c.get('text')]) for g, cs in ui.items()}
    # Client scripts contain UI prompts, but not the server's level/ingredient refusal messages.
    pattern = re.compile(rb'avarice|minimum loot|fire ruby|amulet of fire|deadman reward|safe deposit', re.I)
    scripts = {}
    for gid in source.reference(12):
        raw = source.file(12, gid, 0)
        if raw and pattern.search(raw):
            scripts[str(gid)] = dict(sha256=digest(raw), strings=[s.decode('latin-1') for s in re.findall(rb'[ -~]{4,}', raw)])
    result['script_hits'] = scripts
    (OUT / 'cache-probe.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps({k: v for k, v in result.items() if k in ('source_fonts', 'local_fonts', 'ui')}, indent=2))
    print('script matches', list(scripts))
    source.dat.close(); local.dat.close()


if __name__ == '__main__':
    import sys
    OUT.mkdir(exist_ok=True)
    {'wiki': wiki, 'cache': cache, 'scripts': scripts, 'shop': shop}[sys.argv[1]]()
