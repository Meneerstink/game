"""Final phase4 handoff audit. Checks own artifacts and reserved-file integrity; no cache writes or server access."""
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest
from phase4_round import verify, OUT, DOC, ROOT, sha


def main():
    verify()
    proposals=DOC/'voorstel'
    patches=sorted(proposals.glob('*.patch'))
    assert len(patches)==5
    subprocess.run(['git','apply','--check',*[str(p) for p in patches]],cwd=ROOT,check=True,capture_output=True)
    review=json.loads((proposals/'metadata-review.json').read_text())
    for path,digest in review['reserved_sha256'].items():
        assert hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==digest, f'Reserved input changed: {path}'
    reserved=['data/cfg/items.yml','data/world_edits.json',
              'game/plugins/src/main/kotlin/gg/rsmod/plugins/api/cfg/Items.kt',
              'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/DropSources.kt',
              'game/plugins/src/main/kotlin/gg/rsmod/plugins/content/quests/tab/CollectionLog*.kt']
    changed=subprocess.check_output(['git','diff','--name-only','58f14077d','--',*reserved],cwd=ROOT,text=True).strip()
    assert not changed,changed
    raw=(ROOT/'build/dmm2/phase4-review-run.txt').read_bytes()
    compiled=raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf-8-sig')
    assert 'both proposal copies and own break tests compiled' in compiled
    assert 'BUILD SUCCESSFUL' in compiled
    (DOC/'phase4-review-output/compile-run.txt').write_text(compiled,encoding='utf-8',newline='\n')
    breaking=(DOC/'phase4-review-output/break-tests.txt').read_text(encoding='utf-8-sig')
    assert 'OK (17 tests)' in breaking, breaking[-2000:]
    contract=(OUT/'batch-tests.txt').read_text(encoding='utf-8-sig')
    assert 'OK (3 tests)' in contract
    result=unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.discover(str(DOC),'test_dmm2_ids_from_apply.py'))
    assert result.wasSuccessful()
    subprocess.run(['C:/Program Files/Git/bin/bash.exe','-n',str(DOC/'DMM2-APPLY.sh')],check=True)
    (DOC/'PHASE4-VALIDATION.json').write_text(json.dumps(dict(
        native_plans=8,cache_files_unchanged=78,allocations_equal_to_phase3=True,
        junit_batch_contract_tests=3,junit_corrected_break_tests=17,python_apply_handoff_tests=result.testsRun,
        proposal_copies_compiled=True,patches=5,patch_check=True,reserved_paths_unchanged=True,
        plan_metadata_collisions=len(review['plan_metadata_collisions']),
        evidence_sha256={p.relative_to(DOC).as_posix():sha(p) for p in [OUT/'batch-tests.txt',DOC/'phase4-review-output/break-tests.txt']},
        limitations=['PLAN-only metadata IDs; regenerate from actual apply', 'No apply by GPT-A', 'No runtime/live evidence',
                     'UI230/font645, Nigel icon, ancient34607 and AV/POH require Claude integration']
    ),indent=2)+'\n',encoding='utf-8')
    print('PHASE4 audit green: 8 native PLANs / 78 immutable files / 26 own tests / 5 text-only proposals')


if __name__=='__main__':main()
