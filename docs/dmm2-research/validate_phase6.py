"""Validate this phase-6 handoff against exact source files, mapping and fresh targeted JUnit reports."""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET

DOC = Path(__file__).resolve().parent
ROOT = DOC.parents[1]
EXPECTED = {
    "gg.rsmod.game.tools.importer.Dmm2UiPlanTests": ("game", 7),
    "gg.rsmod.plugins.content.mechanics.deadman.Dmm2WindowTests": ("game/plugins", 18),
    "gg.rsmod.plugins.content.mechanics.deadman.Dmm2InteractionTests": ("game/plugins", 2),
    "gg.rsmod.plugins.content.mechanics.deadman.SafeDepositBoxTests": ("game/plugins", 13),
    "gg.rsmod.plugins.content.mechanics.deadman.NigelRewardStoreTests": ("game/plugins", 8),
    "gg.rsmod.plugins.content.mechanics.deadman.Dmm2BootTests": ("game/plugins", 4),
    "gg.rsmod.plugins.content.mechanics.deadman.Dmm2BreakTests": ("game/plugins", 17),
    "gg.rsmod.plugins.content.mechanics.deadman.Dmm2WeaponPresentationTests": ("game/plugins", 4),
}

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def mapping(text):
    rows = {}
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        kind, source, local = line.split("\t")
        key = kind, int(source)
        assert key not in rows, key
        rows[key] = int(local)
    return rows

def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)

def verify(snapshot, since):
    old = mapping(git("show", "6dca58f78:data/cfg/dmm2/ids.tsv").decode())
    actual = mapping((ROOT / "data/cfg/dmm2/ids.tsv").read_text())
    assert len(old) == 143 and len(actual) == 151
    assert all(actual[key] == value for key, value in old.items())
    assert sha(ROOT / "data/cfg/dmm2/ids.tsv") == "aede3e0617d4a9068038d30b636d63a25e3f2f1ff862e3207cc2ab59564462fb"
    sources = ["dmm2-interfaces.json", "phase5-sources/shop-reward-rows.json", "phase5-sources/ui-scripts.json",
               "phase5-sources/omnishop-scripts.json", "phase6-sources/scripts.json", "phase6-sources/structs.json",
               "phase6-sources/button-sound.json", "phase6-sources/controls.json"]
    for filename in ("phase5-sources/shop-reward-rows.json", "phase6-sources/structs.json", "phase6-sources/controls.json"):
        for key, value in json.loads((DOC / filename).read_text()).items():
            if "hex" in value:
                assert hashlib.sha256(bytes.fromhex(value["hex"])).hexdigest() == value["sha256"], (filename, key)
    rows = json.loads((DOC / "phase5-sources/shop-reward-rows.json").read_text())
    assert b"Deadman Points" in bytes.fromhex(rows["1206"]["hex"])
    code = [
        "game/plugins/src/main/kotlin/gg/rsmod/plugins/content/mechanics/deadman/" + name
        for name in ("Dmm2Boot.kt", "Dmm2Interactions.kt", "Dmm2RewardUi.kt", "Dmm2SafeDepositUi.kt", "SafeDepositBox.kt", "NigelRewardStore.kt", "dmm2.plugin.kts", "Dmm2Ids.kt")
    ] + ["game/src/main/kotlin/gg/rsmod/game/tools/importer/" + name for name in ("OsrsInterfacePortTool.kt", "Dmm2WindowPort.kt")]
    for name, (project, _) in EXPECTED.items():
        code.append(project + "/src/test/kotlin/" + name.replace(".", "/") + ".kt")
    code += ["docs/dmm2-research/RUN-PHASE6-TESTS.ps1", "docs/dmm2-research/phase6-regression.gradle"]
    for filename in code:
        # Git's line-ending normalisation makes text equality the relevant snapshot identity check.
        assert git("show", snapshot + ":" + filename).decode().replace("\r\n", "\n") == (ROOT / filename).read_text(), filename
    assert "getInteractingOption()" not in (ROOT / code[6]).read_text()
    since = dt.datetime.fromisoformat(since.replace("Z", "+00:00"))
    out = DOC / "phase6-output/regression-final"
    out.mkdir(parents=True, exist_ok=True)
    reports = {}
    for name, (project, count) in EXPECTED.items():
        path = ROOT / project / "build/test-results/test" / ("TEST-" + name + ".xml")
        suite = ET.parse(path).getroot()
        assert suite.attrib["name"] == name
        assert int(suite.attrib["tests"]) == count, (name, suite.attrib)
        assert all(int(suite.attrib.get(field, 0)) == 0 for field in ("failures", "errors", "skipped")), (name, suite.attrib)
        stamp = dt.datetime.fromisoformat(suite.attrib["timestamp"].replace("Z", "+00:00"))
        if stamp.tzinfo is None:
            stamp = stamp.replace(tzinfo=dt.timezone.utc)
        assert stamp >= since, (name, stamp, since)
        target = out / path.name
        shutil.copyfile(path, target)
        reports[name] = dict(tests=count, failures=0, errors=0, skipped=0, timestamp=suite.attrib["timestamp"],
                             file=str(target.relative_to(ROOT)).replace("\\", "/"), sha256=sha(target))
    result = dict(status="PASS", snapshot=git("rev-parse", snapshot).decode().strip(),
                  total_tests=sum(row[1] for row in EXPECTED.values()), classes=len(EXPECTED),
                  mapping_count=len(actual), original_143_unchanged=True,
                  interface_mappings={str(n): actual[("interface", n)] for n in (230, 232, 819, 806)},
                  currency="Deadman Points (V3; source row1206)", reports=reports,
                  source_sha256={"docs/dmm2-research/" + f: sha(DOC / f) for f in sources},
                  code_sha256={f: sha(ROOT / f) for f in code},
                  command="powershell -NoProfile -ExecutionPolicy Bypass -File docs/dmm2-research/RUN-PHASE6-TESTS.ps1",
                  compile="Standard Gradle module dependencies + nine selected DMM2 scripts; no old class snapshot prepended.",
                  discovery="One core + seven plugin class-file includes and matching CLI --tests filters.",
                  client_or_live_pass=False, cache_apply_by_gpt_a=False,
                  claude_cache_evidence="Dossier15:4x: tx-20261117-144453,622writes,VERIFY_OK; reportcommit d1d87fbcb (not GPT-A evidence).",
                  superseded_run="22571: no green claim; excessive empty forks before explicit class-file discovery restriction.")
    (DOC / "PHASE6-VALIDATION.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: result[k] for k in ("status", "total_tests", "classes", "mapping_count", "snapshot")}, indent=2))

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--snapshot", required=True)
    parser.add_argument("--since", required=True, help="Earliest accepted JUnit UTC timestamp, e.g. 2026-11-17T15:28:00Z")
    args = parser.parse_args()
    verify(args.snapshot, args.since)
