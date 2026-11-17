#!/usr/bin/env bash
# Only PLAN, on the two preserved phase4 cache copies. No caller flags reach an importer.
set -euo pipefail
cd "$(dirname "$0")/../.."
if (($#)); then echo 'This round accepts no arguments and never runs apply.' >&2; exit 2; fi
python docs/dmm2-research/phase5_plan_round.py prepare
./gradlew.bat dmm2Phase5UiPlans --priority low --no-parallel -I docs/dmm2-research/phase5-ui-plans.gradle
python docs/dmm2-research/phase5_plan_round.py verify
# Exact native java commands/classpaths and outcomes are recorded by the task in:
# phase5-ui-output/{interfaces,weapon-seqs,fire-spots}-command.json
# Existing tools / exact selectors (without --apply):
# OsrsInterfacePortTool dmm2-ui    -> source230,232,819,806
# OsrsFxImportTool dmm2-weapon-av -> source seq721,8056,8057
# OsrsFxImportTool dmm2-fire-av   -> source spot116 + its real dependencies
