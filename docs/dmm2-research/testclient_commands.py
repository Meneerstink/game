"""Print exact testclient item commands from Claude's REAL local mappings. Never contacts the game."""
from pathlib import Path
import argparse

GROUPS = {
    'starter': [22331,22333,22335,26149,13660,13317,13318,33063,13658],
    'trinkets': [28561,28564,28567,28570,33044,33047,33050],
    'weapons': [28531,28534,28537,28540,28543,28545,28547,28549,29599,29602,33041],
    'fire': [34422,34425],
    'rewards': [29625,29628,29622,29619,33012,33015,33018,33122],
    'blood': [13307,13317,13318,13319],
    'ancient': [34607,29613,29615,29617],
}
ALIASES = {28564:23822,28570:23824,33044:23823,33047:23825}

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--ids', type=Path, required=True, help='Real data/cfg/dmm2/ids.tsv from Claude import')
    p.add_argument('--group', choices=GROUPS, required=True)
    args = p.parse_args()
    mapping = {}
    for line in args.ids.read_text(encoding='utf-8-sig').splitlines():
        if not line.strip() or line.lstrip().startswith('#'): continue
        kind, source, local = line.split('\t')
        key = (kind, int(source)); value = int(local)
        if value <= 0 or key in mapping: raise ValueError('Invalid or duplicate mapping: ' + line)
        mapping[key] = value
    for source in GROUPS[args.group]:
        local = mapping.get(('item', source), ALIASES.get(source))
        if local is None: raise ValueError(f'Missing actual item mapping for OSRS {source}; no plan/source id substitution')
    for source in GROUPS[args.group]:
        local = mapping.get(('item', source), ALIASES.get(source))
        amount = 10 if source == 13307 else 100 if source == 13658 else 1
        print(f'# OSRS source {source}')
        print(f'::item {local} {amount}')

if __name__ == '__main__': main()
