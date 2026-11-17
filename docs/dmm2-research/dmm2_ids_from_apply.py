"""Derive runtime IDs ONLY from successful native apply output; never substitute a source/PLAN ID."""
import argparse
from pathlib import Path
import re
import os

JOBS = ('items', 'npcs', 'seqs', 'spots', 'locs', 'ui232', 'wisp', 'wisp-sidecar')
REUSED = {28564: 23822, 28570: 23824, 33044: 23823, 33047: 23825}
PARENTS = (13307,13317,13318,13319,13660,22331,22333,22335,26149,28531,28534,28537,28540,
           28543,28545,28547,28549,28561,28567,29599,29602,29605,29607,29609,29611,29613,
           29615,29617,29619,29622,29625,29628,33012,33015,33018,33021,33023,33025,33027,
           33029,33031,33033,33035,33036,33041,33050,33063,33122,34422,34425,13658)


def verify_log(name, text):
    if re.search(r'(VERIFY_ERROR|Exception in thread|^ERROR|^DRY_RUN|^PLAN_ONLY|applied=false)', text, re.M):
        raise ValueError(f'{name}: unsuccessful or PLAN-only output')
    if name == 'items':
        match = re.search(r'^TRANSACTION (tx-\S+) applied=true finalized=\d+ recovered=\d+\s*$', text, re.M)
    else:
        match = re.search(r'^APPLIED transaction=(tx-\S+) (?:writes|applied)=\d+', text, re.M)
    if not match:
        raise ValueError(f'{name}: missing completed transaction')
    if name == 'wisp-sidecar' and 'VERIFY_OK both targets hold intended bytes and names' not in text:
        raise ValueError('Wisp sidecar verification missing')
    return match[1]


def wisp_args(text):
    match = re.search(r'^PLAN skeletal npc 15571 -> (\d+).*models=\[([^]]+)\].*seqs=\{([^}]+)\}', text, re.M)
    if not match:
        raise ValueError('No Wisp plan in completed native output')
    models = [int(v) for v in match[2].split(',')]
    pairs = dict((int(a), int(b)) for a, b in re.findall(r'(\d+)=(\d+)', match[3]))
    if models != [49222, 49218, 49221, 49224] or set(pairs) != {10230, 10233}:
        raise ValueError('Wisp source contract changed: inspect pinned cache before import')
    return [match[1], ','.join(map(str, models)), *[f'{local}={source}' for source, local in pairs.items()]]


def mappings(logs):
    out = {}

    def put(kind, source, local):
        key = kind, int(source)
        local = int(local)
        if min(key[1], local) <= 0 or max(key[1], local) > 2147483647:
            raise ValueError('ID outside runtime integer range')
        if key in out and out[key] != local:
            raise ValueError(f'Conflicting mapping for {key}: {out[key]} / {local}')
        out[key] = local

    for text in logs.values():
        for local, source in re.findall(r'^PLAN item (\d+) .* <- upstream_item:(\d+)\b', text, re.M):
            put('item', source, local)
        for kind, source, local in re.findall(r'^PLAN (?:skeletal )?(npc|seq|spotanim|loc|synth) (\d+)(?: [^\n]*?)? -> (\d+)\b', text, re.M):
            put(kind, source, local)
        for source, local in re.findall(r'^PLAN osrs (\d+) -> interface (\d+):', text, re.M):
            put('interface', source, local)
        for seqs in re.findall(r'^PLAN (?:skeletal )?npc .*seqs=\{([^}]+)\}', text, re.M):
            for source, local in re.findall(r'(\d+)=(\d+)', seqs):
                put('seq', source, local)
        for source, local in re.findall(r'^PLAN spotanim .* seq (\d+)->(\d+)', text, re.M):
            put('seq', source, local)
    for source, local in REUSED.items():
        put('item', source, local)
    required = {('item', s) for s in PARENTS} | {('npc', s) for s in (6516,6485,8391,8390,15569,15570,15571)} | {('interface',232)}
    required |= {('seq', s) for s in (11444,11810,11811,11812,11935,11936,11937,*range(13701,13715),14497)}
    required |= {('spotanim', s) for s in (3030,3031,3032,3033,*range(3565,3572),4020)}
    required |= {('loc', s) for s in (54114,54115,54116)}
    if required - out.keys():
        raise ValueError(f'Missing actual mappings: {sorted(required - out.keys())}')
    items = [v for (k, _), v in out.items() if k == 'item']
    if len(items) != len(set(items)):
        raise ValueError('Two item sources map to the same local item')
    return out


def generate(log_dir, output):
    logs = {job: (log_dir / (job + '.txt')).read_text(encoding='utf-8-sig') for job in JOBS}
    transactions = {job: verify_log(job, text) for job, text in logs.items()}
    if len(set(transactions.values())) != len(transactions):
        raise ValueError('Transaction IDs reused between apply steps; inspect journal before publishing IDs')
    table = mappings(logs)
    lines = ['# DMM2 actual native apply mappings; no source/PLAN fallback.']
    lines += [f'# {job}: {tx}' for job, tx in transactions.items()]
    lines += ['# Existing trinkets: verified phase1 local cache + Items.kt; their notes are NOT imported here.',
              '# Ancient Deadman cape34607 / UI230 / Nigel map icon require separate proven imports.']
    lines += [f'{kind}\t{source}\t{local}' for (kind, source), local in sorted(table.items())]
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_suffix(output.suffix + '.tmp')
    temp.write_text('\n'.join(lines) + '\n', encoding='utf-8')
    os.replace(temp, output)
    return len(table)


def main():
    p = argparse.ArgumentParser(description=__doc__)
    sub = p.add_subparsers(dest='mode', required=True)
    v = sub.add_parser('verify-log'); v.add_argument('job', choices=JOBS); v.add_argument('log', type=Path)
    w = sub.add_parser('wisp-args'); w.add_argument('log', type=Path)
    g = sub.add_parser('generate'); g.add_argument('--logs', type=Path, required=True); g.add_argument('--out', type=Path, required=True)
    args = p.parse_args()
    if args.mode == 'verify-log':
        print('transaction=' + verify_log(args.job, args.log.read_text(encoding='utf-8-sig')) + ' APPLIED_OK')
    elif args.mode == 'wisp-args':
        text = args.log.read_text(encoding='utf-8-sig'); verify_log('wisp', text)
        print('\n'.join(wisp_args(text)))
    else:
        print(f'Published {generate(args.logs, args.out)} real mappings to {args.out}')


if __name__ == '__main__':
    main()
