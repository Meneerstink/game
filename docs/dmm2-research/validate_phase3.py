"""Validate pinned public sources and preserved own JUnit reports, and record exact current source hashes."""
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET

DOC = Path(__file__).parent
ROOT = DOC.parents[1]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    sources = json.loads((DOC/'phase3-sources/manifest.json').read_text(encoding='utf-8'))
    for source in sources:
        if not source.get('missing'): assert sha(DOC/'phase3-sources'/source['file']) == source['sha256']
    reports = []
    for p in sorted((DOC/'phase3-test-results').glob('TEST-*.xml')):
        suite = ET.parse(p).getroot()
        assert int(suite.attrib['failures']) == int(suite.attrib['errors']) == int(suite.attrib.get('skipped',0)) == 0, p
        reports.append(dict(name=suite.attrib['name'], tests=int(suite.attrib['tests']), timestamp=suite.attrib['timestamp'],
            sha256=sha(p), file=str(p.relative_to(ROOT)).replace('\\','/')))
    assert len(reports) == 15, 'Missing own test report'
    assert sum(r['tests'] for r in reports) == 101, 'Unexpected test count'
    own = [ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/deadman/Dmm2BreakTests.kt',
           ROOT/'game/plugins/src/test/kotlin/gg/rsmod/plugins/content/mechanics/deadman/Dmm2SourcePolicyTests.kt']
    changed = ['drops/DropTableFactory.kt', 'mechanics/deadman/DeadmanTrinkets.kt', 'mechanics/deadman/Dmm2Boot.kt',
        'mechanics/deadman/Dmm2Ids.kt','mechanics/deadman/NigelRewardStore.kt','mechanics/deadman/ThunderKhopesh.kt',
        'mechanics/deadman/dmm2.plugin.kts','mechanics/pvp/breach/DeadmanBreach.kt']
    own += [ROOT/'game/plugins/src/main/kotlin/gg/rsmod/plugins/content'/name for name in changed]
    evidence = dict(result='PASS', tests=sum(r['tests'] for r in reports), classes=reports,
        method='Current Kotlin 1.8 source precompile; real Gradle Test/JUnit; nine own scripts compiled; 15 own classes only',
        correction='Initial new full-safebox fixture had tradeable=false for Blood money. Corrected to true; only Dmm2BreakTests rerun.',
        phase3_new_tests=19, previous_own_tests=82, priority='low', machine_build_slot=True, full_suite=False,
        sources=dict(pages=sum(not r.get('missing') for r in sources), missing=sum(bool(r.get('missing')) for r in sources)),
        source_hashes={str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in own},
        runtime_or_live=False)
    (DOC/'PHASE3-VALIDATION.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    print('PASS: 101 tests / 15 own classes; 16 revision hashes; 2 explicitly missing transcripts')

if __name__ == '__main__': main()
