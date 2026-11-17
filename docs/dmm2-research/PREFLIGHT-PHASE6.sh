#!/usr/bin/env bash
# Native existing importer, PLAN only; both preserved physical phase4 cache copies.
set -euo pipefail
cd "$(dirname "$0")/../.."
if (($#)); then echo 'No caller arguments accepted; this script never applies.' >&2; exit 2; fi
python docs/dmm2-research/phase6_plan_round.py prepare
./gradlew.bat dmm2Phase6UiPlans --priority low --no-parallel -I docs/dmm2-research/phase6-ui-plans.gradle
python docs/dmm2-research/phase6_plan_round.py verify
