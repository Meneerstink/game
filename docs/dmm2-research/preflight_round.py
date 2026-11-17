"""Prepare two physical copies of the worktree cache; prove plans leave both byte-identical.

Only this worktree's build/dmm2/cache-plan and research evidence are writable.
No servers, saves, config secrets or original import journals are opened.
"""
import csv
import hashlib
import json
from pathlib import Path
import shutil
import sys
import re

ROOT = Path(__file__).resolve().parents[2]
DOC = Path(__file__).resolve().parent
BOX = ROOT / 'build/dmm2/cache-plan'
OUT = DOC / 'preflight-output'

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as f:
        for b in iter(lambda: f.read(1024*1024), b''): h.update(b)
    return h.hexdigest()

def fingerprints():
    return {str(p.relative_to(BOX)): sha(p) for sub in ('game/game/data/cache', 'file-server/cache')
            for p in sorted((BOX/sub).glob('main_file_cache.*'))}

def write(name, value):
    OUT.mkdir(exist_ok=True)
    (OUT/name).write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')

def bundles(commands, derive_sidecar=False):
    # Individual diagnostics are followed by a coherent namespace allocation for each WHOLE tool batch.
    for kind in ('item', 'npc', 'seq', 'spotanim', 'loc'):
        source = [s['ids'] for s in commands if s['kind'] == kind and not s['name'].startswith('bundle-')]
        if source and not any(s['name'] == 'bundle-'+kind for s in commands):
            commands.append(dict(name='bundle-'+kind, kind=kind, ids=','.join(source)))
    if not any(s['name'] == 'interface-232' for s in commands):
        commands.append(dict(name='interface-232', kind='interface', ids='232'))
    if not any(s['name'] == 'npc-skeletal-15571' for s in commands):
        commands.append(dict(name='npc-skeletal-15571', kind='npc-skeletal', ids='15571'))
    if not any(s['name'] == 'bundle-npc-classic' for s in commands):
        classic = [s['ids'] for s in commands if s['kind'] == 'npc' and not s['name'].startswith('bundle-') and s['ids'] != '15571']
        commands.append(dict(name='bundle-npc-classic', kind='npc', ids=','.join(classic)))
    # Derive temporary owner/sequence IDs from the REAL native plan; never from a guessed local mapping.
    wisp = OUT/'npc-skeletal-15571.txt'
    if derive_sidecar and wisp.is_file() and not any(s['name'] == 'wisp-sidecar' for s in commands):
        match = re.search(r'PLAN skeletal npc 15571 -> (\d+).*models=\[([^]]+)\].*seqs=\{([^}]+)\}', wisp.read_text(encoding='utf-8'))
        if match:
            args = [int(match[1])] + [int(x) for x in match[2].split(',')]
            assert len(args) == 5, 'Wisp model list changed; inspect before planning'
            for source, local in re.findall(r'(\d+)=(\d+)', match[3]): args.extend((int(local), int(source)))
            commands.append(dict(name='wisp-sidecar', kind='npc-sidecar', ids=','.join(map(str,args))))
    return commands

def exact_commands(commands):
    jars = sorted((ROOT/'game/build/install/game/lib').glob('*.jar'))
    cp = ';'.join(str(p) for p in [ROOT/'build/dmm2/cache-plan-runner', ROOT/'game/build/classes/kotlin/main', *jars])
    java = shutil.which('java')
    if not java: raise RuntimeError('Java executable not found')
    return [dict(s, argv=[java, '-Xmx768m', f'-Drsps.root={BOX}', f'-Drsps.data={BOX}/game/game/data',
            '-cp', cp, 'Dmm2PreflightRunner', s['kind'], s['ids'], str(BOX)]) for s in commands]

def script(commands):
    base = '''#!/usr/bin/env bash
# PLAN ONLY, one machine-wide Gradle slot. No flags can reach an importer.
set -euo pipefail
cd "$(dirname "$0")/../.."
if (($#)); then echo 'No arguments accepted: this round is dry-run only.' >&2; exit 2; fi
python docs/dmm2-research/preflight_round.py prepare
./gradlew.bat dmm2CachePlans --priority low -I docs/dmm2-research/preflight-round.gradle
python docs/dmm2-research/preflight_round.py verify
# Exact Java calls made sequentially INSIDE dmm2CachePlans; the block is documentation, not a second execution.
# The runner injects the listed IDs into existing importers' in-memory batch maps. No tool source is edited.
: <<'DMM2_EXACT_PLAN_INVOCATIONS'
'''
    exact = exact_commands(commands)
    cp = exact[0]['argv'][5]
    # Bash double quotes are safe here: generated paths contain no shell expansion characters.
    if any(c in cp for c in '$`"'): raise RuntimeError('Unexpected shell metacharacter in classpath')
    base += 'export RSPS_OSRS_SOURCE_CACHE="C:/RSPS/import-source/openrs2-2686/cache"\n'
    base += 'export RSPS_OSRS_SOURCE_TAG=""\nDMM2_PLAN_CP="' + cp.replace('\\', '/') + '"\n'
    native = dict(item='OsrsItemImportTool', npc='OsrsNpcImportTool', seq='OsrsFxImportTool.SEQ_BATCHES', **{'npc-skeletal':'OsrsPetImportTool skeletal-npc', 'npc-sidecar':'SkeletalSidecarExportTool cache plan'},
                  spotanim='OsrsFxImportTool.BATCHES', loc='OsrsLocImportTool', interface='OsrsInterfacePortTool')
    for s in exact:
        base += f'# {s["name"]}: {native[s["kind"]]} - source IDs {s["ids"]}; no --apply\n'
        argv = s['argv']
        base += ' '.join('"$DMM2_PLAN_CP"' if i == 5 else '"'+v.replace('\\', '/')+'"' for i,v in enumerate(argv))+'\n'
    (DOC/'PREFLIGHT-RONDE.sh').write_text(base+'DMM2_EXACT_PLAN_INVOCATIONS\n', encoding='utf-8')

def prepare():
    BOX.mkdir(parents=True, exist_ok=True)
    assert BOX.resolve().is_relative_to(ROOT/'build/dmm2')
    for sub in ('game/game/data/cache', 'file-server/cache'):
        dst = BOX/sub; dst.mkdir(parents=True, exist_ok=True)
        for p in (ROOT/'data/cache').glob('main_file_cache.*'): shutil.copy2(p, dst/p.name)
    # Empty isolated journal: orchestrator's recovery cannot inspect/finalize a real import.
    (BOX/'import-journal').mkdir(exist_ok=True)
    if any((BOX/'import-journal').iterdir()): raise RuntimeError('Sandbox journal must stay empty')
    ledger = Path('C:/RSPS/OSRS_IMPORT_MASTER.yml')
    shutil.copy2(ledger, BOX/'OSRS_IMPORT_MASTER.yml')
    # Existing item/NPC batch registries lazily load this public roster even for a custom batch.
    roster = BOX/'game/game/data/cfg/pets/osrs_pets.tsv'
    roster.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT/'data/cfg/pets/osrs_pets.tsv', roster)
    rows = list(csv.DictReader((DOC/'PREFLIGHT-DMM2-NIGHT.tsv').open(encoding='utf-8'), delimiter='\t'))
    defs = json.loads((DOC/'cache2686-items.json').read_text(encoding='utf-8'))
    commands, coverage = [], []
    item_ids = []
    for row in rows:
        kind, src = row['kind'], int(row['source'])
        reason = ''
        if kind == 'item':
            d = defs.get(str(src), {}); fields = d.get('fields', {})
            if row['status'].startswith('EXISTING'): reason = 'REUSE: ' + row['status']
            elif '149' in fields: reason = 'NO_IMPORT: OSRS bank placeholder; 667 importer has no placeholder conversion'
            elif '98' in fields: reason = 'AUTO_NOTE: imported with parent Spec(noted=true)'
            elif 13308 <= src <= 13316: reason = 'AUTO_COUNT: imported by parent Blood money 13307'
            else: item_ids.append(src); reason = 'item'
        elif kind == 'sprite': reason = 'INTERFACE_DEPENDENCY' if src in (170,828,829,830,1041,1042) else 'NO_IMPORT: standalone icon candidate; semantic mapping unproven, dump is not import'
        else: reason = kind
        coverage.append(dict(row, handling=reason))
    # Per-item steps continue past an unsupported copy/opcode; each actual tool outcome is retained.
    for src in item_ids: commands.append(dict(name=f'item-{src}', kind='item', ids=str(src)))
    for kind in ('npc', 'seq', 'spotanim', 'loc'):
        # Per asset prevents one unsupported skeleton from hiding every other asset.
        for row in rows:
            if row['kind'] == kind: commands.append(dict(name=f'{kind}-{row["source"]}', kind=kind, ids=row['source']))
    commands.append(dict(name='interfaces-230-232', kind='interface', ids='230,232'))
    bundles(commands)
    # Explicit decoder-schema adapter: retain the raw proof and only rename matching fields.
    raw = json.loads((DOC/'cache2686-safebox-interfaces.json').read_text(encoding='utf-8'))
    rename = dict(w='width', h='height', wmode='widthMode', hmode='heightMode', xmode='xMode', ymode='yMode', color='colour', ops='actions')
    cooked = {g:{i:{rename.get(k,k):v for k,v in c.items()} for i,c in group.items()} for g,group in raw.items()}
    dest = BOX/'team/dossiers/N462-notities/toa/cache/interfaces.json'; dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(json.dumps(cooked), encoding='utf-8')
    (BOX/'commands.json').write_text(json.dumps(commands), encoding='utf-8')
    script(commands)
    write('coverage.json', coverage)
    write('before.json', fingerprints())
    write('provenance.json', dict(cache=str(ROOT/'data/cache'), cache_note='Two identical physical snapshots of own worktree cache; NOT current live/distribution cache.',
        ledger=str(ledger), ledger_sha256=sha(ledger), source='OpenRS2 2686', rows=len(rows), steps=len(commands),
        commands=exact_commands(commands)))
    print(f'Prepared {len(rows)} rows / {len(commands)} exact importer plans on TWO CACHE COPIES')

def verify():
    before = json.loads((OUT/'before.json').read_text())
    after = fingerprints()
    changed = [k for k in before.keys() | after.keys() if before.get(k) != after.get(k)]
    write('after.json', after)
    write('unchanged.json', dict(files=len(before), changed=changed, pass_=not changed))
    if changed: raise RuntimeError('Cache copy changed during dry-run: ' + repr(changed))
    commands = json.loads((BOX/'commands.json').read_text(encoding='utf-8'))
    script(commands)
    write('exact-commands.json', exact_commands(commands))
    provenance = json.loads((OUT/'provenance.json').read_text(encoding='utf-8'))
    provenance.update(steps=len(commands), commands=exact_commands(commands))
    write('provenance.json', provenance)
    print(f'VERIFIED {len(before)} cache files unchanged after every plan; no apply.')

def add_bundles():
    # Resume the same already-fingerprinted snapshot without copying caches or resetting evidence.
    before = json.loads((OUT/'before.json').read_text())
    if before != fingerprints(): raise RuntimeError('Snapshot changed; cannot resume')
    manifest = BOX/'commands.json'
    commands = bundles(json.loads(manifest.read_text(encoding='utf-8')), derive_sidecar=True)
    manifest.write_text(json.dumps(commands), encoding='utf-8')
    script(commands)
    print('Added coherent tool-batch plans to existing snapshot; no cache writes')

if __name__ == '__main__':
    {'prepare':prepare, 'verify':verify, 'add-bundles':add_bundles}[sys.argv[1]]()
