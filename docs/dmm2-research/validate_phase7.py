"""Validate fresh, explicitly selected JUnit evidence and the executed source snapshot. No runtime/cache writes."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
DOC = Path(__file__).resolve().parent
CORE = ['Dmm2SkeletalExtraSeqTests', 'Dmm2ImportBatchTests', 'Dmm2UiPlanTests']
PLUGINS = ['Dmm2BootTests','Dmm2BreakTests','Dmm2CosmeticsTests','Dmm2CurrencyTests',
           'Dmm2InteractionTests','Dmm2ItemServicesTests','Dmm2SourcePolicyTests',
           'Dmm2WeaponPresentationTests','Dmm2WindowTests','StarterWeaponCombatTests',
           'StarterPackInteractionsTests','DeadmanStarterPackTests','NigelHouseRewardsTests',
           'NigelRewardStoreTests','SafeDepositBoxTests','DeadmanExperienceExtensionTests',
           'HomeTeleportDeadmanGateTests','PvpDeathBreakablesTests',
           'UntradeableDeathProtectionTests','PvpLootTimesTests',
           'FairyRingTravelLogTests','SpiritTreeMenuTests','N503PetDeathTests']
MISSING_CACHE_CLASSES = {'HomeTeleportDeadmanGateTests', 'PvpDeathBreakablesTests',
                         'UntradeableDeathProtectionTests'}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def validate(snapshot, since):
    target = DOC / 'phase7-output/regression'
    target.mkdir(exist_ok=True)
    suites = []
    for module, names in [('game', CORE), ('game/plugins', PLUGINS)]:
        reports = ROOT / module / 'build/test-results/test'
        for name in names:
            matches = list(reports.glob('TEST-*.' + name + '.xml'))
            if len(matches) != 1:
                raise ValueError(f'{name}: expected one XML, found {matches}')
            file = matches[0]
            suite = ET.parse(file).getroot()
            stamp = datetime.fromisoformat(suite.attrib['timestamp'].replace('Z', '+00:00'))
            if stamp.tzinfo is None:
                stamp = stamp.replace(tzinfo=timezone.utc)
            if stamp < since:
                raise ValueError(f'{name}: stale JUnit XML from {stamp}')
            failures = [dict(test=test.attrib.get('name'), type=f.attrib.get('type'),
                             message=f.attrib.get('message'), text=f.text)
                        for test in suite.findall('testcase') for f in test
                        if f.tag in ('failure', 'error')]
            row = dict(name=name, timestamp=suite.attrib['timestamp'],
                       tests=int(suite.attrib['tests']), failures=int(suite.attrib['failures']),
                       errors=int(suite.attrib['errors']), skipped=int(suite.attrib['skipped']),
                       sha256=sha(file.read_bytes()), details=failures)
            row['known_environment_failure'] = bool(failures) and name in MISSING_CACHE_CLASSES and all(
                'FileNotFoundException' in (f['message'] or '') and 'main_file_cache.dat2' in (f['message'] or '')
                for f in failures)
            if row['tests'] == 0 or row['skipped']:
                raise ValueError(f'{name}: empty/skipped test execution')
            suites.append(row)
            shutil.copy2(file, target / file.name)
    source = []
    # Exact changed implementation/test files, excluding unrelated followed secrets/configuration.
    files = subprocess.check_output(['git','diff','--name-only','f6cb45131','--','game'], cwd=ROOT,
                                    text=True).splitlines()
    # New unit tests may still be untracked while the snapshot was created.
    files += ['game/src/test/kotlin/gg/rsmod/game/tools/importer/Dmm2SkeletalExtraSeqTests.kt']
    for name in sorted(set(files)):
        if not name.endswith(('.kt', '.kts')):
            continue
        current = (ROOT / name).read_bytes().replace(b'\r\n', b'\n')
        executed = subprocess.check_output(['git','show',snapshot + ':' + name], cwd=ROOT).replace(b'\r\n', b'\n')
        if current != executed:
            raise ValueError(f'Source differs from executed snapshot: {name}')
        source.append(dict(path=name, sha256_lf=sha(current)))
    totals = {key: sum(s[key] for s in suites) for key in ('tests','failures','errors','skipped')}
    unexpected = [s['name'] for s in suites if (s['failures'] or s['errors']) and not s['known_environment_failure']]
    result = dict(snapshot=snapshot, since=since.isoformat(), suites=suites, totals=totals,
                  executed_sources=source, all_green=not (totals['failures'] or totals['errors']),
                  unexpected_failure_classes=unexpected,
                  note='Known missing-cache failures remain failures; they are not counted as green or skipped.')
    (DOC / 'PHASE7-VALIDATION.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(dict(snapshot=snapshot, suites=len(suites), **totals, all_green=result['all_green'])))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', required=True)
    parser.add_argument('--since', required=True, help='UTC ISO timestamp, recorded before starting rgradle')
    args = parser.parse_args()
    result = validate(args.snapshot, datetime.fromisoformat(args.since.replace('Z', '+00:00')))
    raise SystemExit(0 if result['all_green'] else 1)
