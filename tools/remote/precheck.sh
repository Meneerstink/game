#!/usr/bin/env bash
# 0 = checked, 1 = proven source error, 2 = skipped: Gradle decides.
set -u
SCRIPT=$(cd "$(dirname "$0")" && pwd)
exec python "$SCRIPT/precheck.py" "$@"
