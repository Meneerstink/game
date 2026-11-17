#!/usr/bin/env bash
# CLAUDE ONLY. GPT-A supplies this file; GPT-A never executes it.
# Prerequisites: merge phase4, compile the six current tools, stop TEST server + fileserver.
# Run INSIDE one Gradle build slot (--priority low); do not run kc_tool.sh concurrently.
# Example variable shapes (supply real transactional workspace paths and a freshly built classpath):
# DMM2_ROOT=C:/RSPS DMM2_DATA=C:/RSPS/work/claude-dmm2/data
# DMM2_CP='C:/RSPS/work/claude-dmm2/game/build/classes/kotlin/main;C:/RSPS/work/claude-dmm2/game/build/install/game/lib/*'
# DMM2_APPLY_OUT=C:/RSPS/work/claude-dmm2/build/dmm2-apply-YYYYMMDD-HHMMSS
# DMM2_JAVA='C:/Program Files/Eclipse Adoptium/jdk-11.0.32.101-hotspot/bin/java.exe'
# bash docs/dmm2-research/DMM2-APPLY.sh
# Tools target BOTH $DMM2_DATA/cache and $DMM2_ROOT/file-server/cache through CacheTransaction.
# UI230 is excluded (font645 unproven); UI232 is only the inventory side panel, NOT the full Safe Deposit Box UI.
set -euo pipefail
cd "$(dirname "$0")/../.."
if [[ ${DMM2_IN_BUILDSLOT:-0} != 1 ]]; then
    exec ./gradlew.bat dmm2ClaudeApply --priority low -I docs/dmm2-research/phase4-apply.gradle
fi
: "${DMM2_ROOT:?Supply transactional workspace root (ledger and file-server/cache)}"
: "${DMM2_DATA:?Supply exact target game data directory}"
: "${DMM2_CP:?Supply current phase4 compiled tools first in Java classpath}"
: "${DMM2_APPLY_OUT:?Supply a NEW empty directory for this single apply round}"
: "${DMM2_JAVA:?Supply Java11 executable path}"
export RSPS_OSRS_SOURCE_CACHE='C:/RSPS/import-source/openrs2-2686/cache'
export RSPS_OSRS_SOURCE_TAG=''
# Read-only process gate. This script never starts/stops another agent's server.
powershell.exe -NoProfile -Command '$ports = 50015,50016,50215; $busy = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -in $ports }; if ($busy) { Write-Error "Stop test server and fileserver before DMM2 apply"; exit 1 }' || exit 1
python - "$DMM2_ROOT" "$DMM2_DATA" "$DMM2_APPLY_OUT" <<'PY'
from pathlib import Path
import sys, shutil
root, data, out = map(Path, sys.argv[1:])
for cache in [data/'cache', root/'file-server/cache']:
    if not (cache/'main_file_cache.dat2').is_file(): raise SystemExit(f'Missing intended cache: {cache}')
if out.exists(): raise SystemExit('Use a NEW output directory; never mix apply rounds')
if not (root/'OSRS_IMPORT_MASTER.yml').is_file(): raise SystemExit('Missing authoritative ledger')
out.mkdir(parents=True)
dest = root/'docs/dmm2-research/dmm2-interfaces.json'
dest.parent.mkdir(parents=True, exist_ok=True)
src = Path('docs/dmm2-research/dmm2-interfaces.json').resolve()
if src != dest.resolve(): shutil.copy2(src, dest)
PY
native() {
    local job="$1" main="$2"; shift 2
    "$DMM2_JAVA" -Xmx768m "-Drsps.root=$DMM2_ROOT" "-Drsps.data=$DMM2_DATA" -cp "$DMM2_CP" \
        "gg.rsmod.game.tools.importer.$main" "$@" 2>&1 | tee "$DMM2_APPLY_OUT/$job.txt"
    # pipeline failure stops immediately; even exit0 needs an explicit completed transaction.
    python docs/dmm2-research/dmm2_ids_from_apply.py verify-log "$job" "$DMM2_APPLY_OUT/$job.txt"
}
# Sequential, fresh census/ledger per tool: later tools reuse dependencies and allocate around previous writes.
# IDs WILL differ from isolated PLANs; the generator uses this apply output only.
native items OsrsItemImportTool dmm2-items --apply "--yml-out=$DMM2_APPLY_OUT/items.generated.yml"
native npcs OsrsNpcImportTool dmm2-npcs --apply
native seqs OsrsFxImportTool dmm2-seqs --apply
native spots OsrsFxImportTool dmm2-spots --apply
native locs OsrsLocImportTool dmm2-locs --apply
native ui232 OsrsInterfacePortTool dmm2 --apply --reuse
native wisp OsrsPetImportTool skeletal-npc dmm2-wisp --apply
# Use Wisp's ACTUAL owner/seq IDs and pinned source models. Do not paste the phase3 temporary IDs.
python docs/dmm2-research/dmm2_ids_from_apply.py wisp-args "$DMM2_APPLY_OUT/wisp.txt" > "$DMM2_APPLY_OUT/wisp.args"
mapfile -t wisp < "$DMM2_APPLY_OUT/wisp.args"
native wisp-sidecar SkeletalSidecarExportTool cache apply "${wisp[@]}"
python docs/dmm2-research/dmm2_ids_from_apply.py generate --logs "$DMM2_APPLY_OUT" --out "$DMM2_DATA/cfg/dmm2/ids.tsv"
echo 'DMM2 cache transactions and ids.tsv completed. Review generated items.yml and proposal patches, wire UI/AV/POH, then run TESTCLIENT-CHECKLIST.md.'
# Stop at any failure. Earlier transactions stay journaled; inspect/rollback via the existing CacheJournalRestoreTool.
# Never rerun the whole script blindly; retain this round as evidence and create an explicit recovery plan.
