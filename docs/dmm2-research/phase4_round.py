"""Prepare/verify isolated named-batch PLAN evidence. Never runs apply or opens game configuration/saves."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

ROOT=Path(__file__).resolve().parents[2]
DOC=Path(__file__).resolve().parent
BOX=ROOT/'build/dmm2/phase4-plan'
OUT=DOC/'phase4-output'
SUBS=('game/game/data/cache','file-server/cache')
PAIRS={'items':'bundle-item','npcs':'bundle-npc-classic','seqs':'bundle-seq','spots':'bundle-spotanim',
       'locs':'bundle-loc','ui232':'interface-232','wisp':'npc-skeletal-15571','wisp-sidecar':'wisp-sidecar'}


def sha(p):
    h=hashlib.sha256()
    with p.open('rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''):h.update(chunk)
    return h.hexdigest()


def hashes():
    return {p.relative_to(BOX).as_posix():sha(p) for sub in SUBS for p in sorted((BOX/sub).glob('main_file_cache.*'))}


def write(file,data):
    OUT.mkdir(exist_ok=True);(OUT/file).write_text(json.dumps(data,indent=2)+'\n',encoding='utf-8')


def prepare():
    if BOX.exists():raise ValueError('Snapshot already exists: verify and use -Pdmm2Resume; never overwrite evidence')
    assert BOX.resolve().is_relative_to(ROOT/'build/dmm2')
    earlier=ROOT/'build/dmm2/cache-plan'
    if not (earlier/'OSRS_IMPORT_MASTER.yml').is_file():raise ValueError('Recreate phase3 immutable snapshot first; no silent current-cache substitution')
    for sub in SUBS:
        (BOX/sub).mkdir(parents=True)
        for p in (earlier/sub).glob('main_file_cache.*'):shutil.copy2(p,BOX/sub/p.name)
    for sub in ['OSRS_IMPORT_MASTER.yml','game/game/data/cfg/pets/osrs_pets.tsv']:
        (BOX/sub).parent.mkdir(parents=True,exist_ok=True);shutil.copy2(earlier/sub,BOX/sub)
    (BOX/'import-journal').mkdir()
    (BOX/'docs/dmm2-research').mkdir(parents=True)
    shutil.copy2(DOC/'dmm2-interfaces.json',BOX/'docs/dmm2-research/dmm2-interfaces.json')
    before=hashes()
    expected={k.replace('\\','/'):v for k,v in json.loads((DOC/'preflight-output/before.json').read_text()).items()}
    if before!=expected:raise ValueError('Phase3 snapshot no longer matches its source proof')
    write('before.json',before)
    print(f'Prepared {len(before)} immutable cache-copy files; original caches are not targets')


def verify():
    before=json.loads((OUT/'before.json').read_text());after=hashes()
    changed=[k for k in before.keys()|after.keys() if before.get(k)!=after.get(k)]
    write('after.json',after)
    journal=list((BOX/'import-journal').iterdir())
    comparison={}
    for job,old in PAIRS.items():
        command=json.loads((OUT/(job+'-command.json')).read_text())
        if command['exit']!=0:raise ValueError(f'Native PLAN failed: {job}')
        args=command['argv']
        if '--apply' in args or ('SkeletalSidecarExportTool' in ' '.join(args) and 'apply' in args):raise ValueError('Apply flag in PLAN evidence')
        actual=(OUT/(job+'.txt')).read_text(encoding='utf-8')
        expected=(DOC/'preflight-output'/(old+'.txt')).read_text(encoding='utf-8')
        pick=lambda s:[line for line in s.splitlines() if line.startswith(('PLAN ', 'CENSUS '))]
        got,want=pick(actual),pick(expected)
        comparison[job]=dict(exit=0,same_allocations=got==want,allocation_lines=len(got),expected=old+'.txt',
                             added=[x for x in got if x not in want],removed=[x for x in want if x not in got],
                             output_sha256=sha(OUT/(job+'.txt')))
    write('comparison.json',comparison)
    result=dict(cache_files=len(before),changed=changed,journal_entries=len(journal),
                same_allocations=all(v['same_allocations'] for v in comparison.values()),
                native_plans=len(comparison),passed=not changed and not journal and all(v['same_allocations'] for v in comparison.values()))
    write('validation.json',result)
    print(json.dumps(result))
    if not result['passed']:raise ValueError('Inspect phase4-output/comparison.json or cache/journal differences')


def snapshot():
    before=json.loads((OUT/'before.json').read_text())
    if before!=hashes() or any((BOX/'import-journal').iterdir()):raise ValueError('Snapshot changed; cannot resume')
    print('Unchanged snapshot verified; completed native steps may be resumed')


if __name__=='__main__':{'prepare':prepare,'verify':verify,'snapshot':snapshot}[sys.argv[1]]()
