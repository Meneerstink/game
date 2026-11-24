#!/usr/bin/env bash
# rgradle.sh - Gradle op de WERKLAPTOP draaien (eigenaar 17-11: werklaptop = bouw- en testmachine, geen AI-accounts daar).
# Regel: HOOFDLIJST sectie 2 'WERKLAPTOP = BOUWMACHINE'. Uitleg/herstel: tools\remote\LEESMIJ.txt.
#
# Gebruik (vanuit een git-werkmap: C:\RSPS\game\game, work\claude-dmm2, work\gpt-a, work\gpt-b ...):
#     bash /c/RSPS/tools/remote/rgradle.sh :game:plugins:test --tests '*NaamTests*'
#     bash /c/RSPS/tools/remote/rgradle.sh :game:installDist
#     (PowerShell/cmd: C:\RSPS\tools\remote\rgradle.cmd <zelfde argumenten>)
# Zelfde argumenten als gradlew. Wat het doet:
#   1. mag/kan de werklaptop? (niet in de werktijd van de eigenaar, bereikbaar, READY) - anders gewoon lokaal ./gradlew
#   2. momentopname van de werkmap (ook ongecommit + nieuwe bestanden) ZONDER geheimen en zonder git-geschiedenis naar de werklaptop
#   3. testcache daar gelijk houden met hier (alleen kopieren als hij hier gewijzigd is)
#   4. daar bouwen/testen in dezelfde map (C:\RSPS\...), uitvoer live terug
#   5. testrapporten (en bij installDist lib\*.jar) terughalen naar de lokale werkmap
#   Valt de werklaptop tijdens de build weg (ssh-fout), dan draait dezelfde opdracht alsnog lokaal.
# Schakelaars: RGRADLE_LOCAL=1 = altijd lokaal; RGRADLE_NOPULL=1 = niets terughalen; RGRADLE_NOFALLBACK=1 = nooit lokaal (exit 3);
#              RGRADLE_FORCE=1 = READY-bestand en werktijd overslaan (alleen Claude, bij instellen/testen).
set -u
HOST=rb
REMOTE_REPO='ssh://rb/C:/RSPS/rb/repo.git'
SSHO=(-o BatchMode=yes -o ConnectTimeout=5)
EXCLUDES=(':(exclude)file-server' ':(exclude)import-journal')
# Bestanden die NOOIT naar de werklaptop mogen (eigenaar: geen geheimen daar). game.yml gaat alleen ONTDAAN van waarden mee.
SECRET_RE='(^|/)data/rsa/|\.pem$|(^|/)id_(rsa|ed25519|ecdsa)|(^|/)\.ssh/|^data/saves/|^data/logs/|(^|/)\.env$|\.pfx$|\.p12$|\.jks$|\.keystore$'
SECRET_KEYS='token|secret|password|passwd|smtp-user|smtp-password|api-key|apikey|webhook|private-key'
ORIG=("$@")

say() { echo "[rgradle] $*"; }
local_build() {
    if [ "${RGRADLE_NOFALLBACK:-0}" = 1 ]; then say "$1 -> NIET lokaal (RGRADLE_NOFALLBACK)"; exit 3; fi
    say "$1 -> lokaal bouwen (zonder blijvende Gradle/Kotlin-daemon: RAM van deze laptop, WACHTEN-2311)"
    # 23-11 (eigenaar 'C'): a local build used to leave a Gradle daemon (-Xmx4g) + Kotlin daemon (-Xmx4g) for hours next to the test
    # server + client -> 23-11 07:26 'out of virtual memory', laptop frozen 6 h. --no-daemon + in-process Kotlin = all freed at the end.
    # (No 'gradlew --stop': that would also kill a build another agent runs in a different work dir.)
    exec ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process "${ORIG[@]}"
}

TOP=$(git rev-parse --show-toplevel 2>/dev/null) || { say "FOUT: dit is geen git-werkmap ($(pwd))"; exit 2; }
cd "$TOP" || exit 2

# ---- 0. VOORCONTROLE (eigenaar 24-11 18:5x 'voor altijd'): gewijzigde Kotlin-bestanden eerst in 1-3 min compileren (precheck.sh);
#         een compileerfout stopt hier, niet pas na ~17-20 min Gradle. Uit: RGRADLE_NOPRECHECK=1 (alleen als de voorcontrole zelf fout zit).
if [ "${RGRADLE_NOPRECHECK:-0}" != 1 ] && printf '%s\n' "${ORIG[@]}" | grep -qE 'installDist|test|build|compile|assemble|jar|run'; then
    bash "$(dirname "$0")/precheck.sh"
    PC=$?
    case "$PC" in
        0) ;;
        1) say "VOORCONTROLE BRONFOUT -> NIET gebouwd"; exit 1 ;;
        *) say "PRECHECK OVERGESLAGEN (exit $PC); volledige Gradle-compile beslist" ;;
    esac
fi
# ---- 0b. TESTS ERBIJ (eigenaar 24-11 23:4x 'voorcontrole kiest tests'): bij een GERICHTE testrun (test-taak met al een --tests) de
#          testklassen van de gewijzigde bestanden erbij zetten (kiestests.sh), direct achter de juiste test-taak. Uit: RGRADLE_NOAUTOTESTS=1.
if [ "${RGRADLE_NOAUTOTESTS:-0}" != 1 ] && printf '%s\n' "${ORIG[@]}" | grep -qx -- '--tests'; then
    PICK=$(bash "$(dirname "$0")/kiestests.sh")
    NEW=(); ADDED=""
    for a in "${ORIG[@]}"; do
        NEW+=("$a")
        case "$a" in
            :game:plugins:test|:game:test)
                mod=plugins; [ "$a" = ":game:test" ] && mod=game
                for c in $(echo "$PICK" | awk -v m="$mod" '$1==m {print $2}'); do
                    printf '%s\n' "${ORIG[@]}" | grep -q -- "$c" && continue
                    NEW+=(--tests "*.$c"); ADDED="$ADDED $c"
                done ;;
        esac
    done
    MISS=$(echo "$PICK" | awk '$1=="game" {print $2}' | tr '\n' ' ')
    if [ -n "$ADDED" ]; then say "tests erbij voor de gewijzigde bestanden:$ADDED"; set -- "${NEW[@]}"; ORIG=("$@"); fi
    printf '%s\n' "${ORIG[@]}" | grep -qx ':game:test' || { [ -n "${MISS// /}" ] && say "LET OP: game-module-tests horen erbij maar :game:test staat niet in de opdracht: $MISS"; }
fi

# Capture source hashes, resolved dependencies and test outputs in this SAME build.
# The init hook publishes only on success. No extra Gradle invocation.
INIT="$TOP/tools/remote/precheck-baseline.init.gradle"
if [ -f "$INIT" ]; then
    set -- "$@" -I tools/remote/precheck-baseline.init.gradle
    ORIG=("$@")
else
    say "PRECHECK OVERGESLAGEN (baseline exporter ontbreekt in deze branch)"
fi

# ---- 1. mag/kan de werklaptop? ----
[ "${RGRADLE_LOCAL:-0}" = 1 ] && local_build "RGRADLE_LOCAL=1"
if [ "${RGRADLE_FORCE:-0}" != 1 ]; then
    # Eigenaar werkt ma, di, do 07:00-14:00 op de werklaptop (17-11): dan er NIET aankomen. Marge: 06:30-14:15.
    DOW=$(date +%u); HM=$(( 10#$(date +%H) * 60 + 10#$(date +%M) ))
    case "$DOW" in 1|2|4) if [ "$HM" -ge 390 ] && [ "$HM" -lt 855 ]; then local_build "werktijd eigenaar (ma/di/do 07:00-14:00): werklaptop niet gebruiken"; fi ;; esac
fi
READYCHECK='test -f /c/RSPS/rb/READY'
[ "${RGRADLE_FORCE:-0}" = 1 ] && READYCHECK='true'
ssh "${SSHO[@]}" "$HOST" "$READYCHECK" </dev/null 2>/dev/null || local_build "werklaptop niet bereikbaar/klaar"

# Zelfde pad op de werklaptop als hier (C:/RSPS/...), in bash-vorm /c/RSPS/...
case "$TOP" in
    [A-Za-z]:/*) RDIR="/$(echo "${TOP:0:1}" | tr 'A-Z' 'a-z')${TOP:2}" ;;
    *)           RDIR="$TOP" ;;
esac
NAME=$(echo "$RDIR" | sed -e 's#^/c/RSPS/##' -e 's#[^A-Za-z0-9._-]#_#g')

# ---- 2. momentopname zonder geheimen, zonder de echte index/branch aan te raken, zonder geschiedenis ----
T0=$(date +%s)
IDX="$(git rev-parse --git-dir)/rb-index.$$"
cp "$(git rev-parse --git-path index)" "$IDX" 2>/dev/null
GIT_INDEX_FILE="$IDX" git add -A -- . "${EXCLUDES[@]}" 2>/dev/null
# geheime bestanden uit de momentopname halen
GIT_INDEX_FILE="$IDX" git ls-files | grep -i -E "$SECRET_RE" | while IFS= read -r f; do GIT_INDEX_FILE="$IDX" git rm -q --cached --ignore-unmatch -- "$f" 2>/dev/null; done
# game.yml: alleen met lege waarden voor geheime sleutels (de build/tests hebben het bestand soms nodig, de waarden nooit)
for y in $(GIT_INDEX_FILE="$IDX" git ls-files | grep -E '(^|/)game\.yml$'); do
    if [ -f "$y" ]; then
        BLOB=$(sed -E "s/^([[:space:]]*-?[[:space:]]*($SECRET_KEYS)[[:space:]]*:).*/\1 \"niet-op-de-werklaptop\"/I" "$y" | git hash-object -w --stdin)
        GIT_INDEX_FILE="$IDX" git update-index --cacheinfo "100644,$BLOB,$y"
    else
        GIT_INDEX_FILE="$IDX" git rm -q --cached --ignore-unmatch -- "$y" 2>/dev/null
    fi
done
TREE=$(GIT_INDEX_FILE="$IDX" git write-tree) || { rm -f "$IDX"; say "FOUT: momentopname mislukt"; exit 2; }
rm -f "$IDX"
# harde controle: zit er toch nog iets geheims in, dan gaat er NIETS naar de werklaptop
BAD=$(git ls-tree -r --name-only "$TREE" | grep -i -E "$SECRET_RE" | head -3)
if [ -z "$BAD" ]; then
    for y in $(git ls-tree -r --name-only "$TREE" | grep -E '(^|/)game\.yml$'); do
        git cat-file -p "$TREE:$y" | grep -i -E "^[[:space:]]*-?[[:space:]]*($SECRET_KEYS)[[:space:]]*:" | grep -v 'niet-op-de-werklaptop' | grep -q . && BAD="$y (waarde niet leeggemaakt)"
    done
fi
[ -n "$BAD" ] && local_build "GEHEIM in de momentopname ($BAD): niets verstuurd"
# commit ZONDER ouder: zo gaat alleen deze stand mee, niet de git-geschiedenis (daar zitten oude game.yml/sleutel-versies in)
SHA=$(git commit-tree "$TREE" -m "rb snapshot $NAME") || exit 2
git push -q --force "$REMOTE_REPO" "$SHA:refs/rb/$NAME" 2>/dev/null || local_build "versturen naar de werklaptop mislukt"
say "momentopname ${SHA:0:9} verstuurd in $(( $(date +%s) - T0 )) s -> $HOST:$RDIR"

# ---- 3. testcache gelijk houden (tests lezen C:\RSPS\game\game\data\cache en file-server\cache) ----
CSRC=/c/RSPS/file-server/cache
STAMP=/c/RSPS/tools/remote/.cache-synced
if [ -f "$CSRC/main_file_cache.dat2" ]; then
    FP=$(cd "$CSRC" && ls -l --time-style=+%s main_file_cache.* 2>/dev/null | awk '{print $5, $6, $7}' | sha1sum | cut -c1-40)
    if [ "$FP" != "$(cat "$STAMP" 2>/dev/null)" ]; then
        AGE=$(( $(date +%s) - $(stat -c %Y "$CSRC/main_file_cache.dat2") ))
        if [ "$AGE" -lt 90 ]; then
            say "cache wordt hier net gewijzigd (${AGE}s geleden) - nu niet gekopieerd; tests die de cache lezen kunnen daar de vorige stand zien"
        else
            TC=$(date +%s)
            if tar -cf - -C /c/RSPS/file-server cache 2>/dev/null | ssh "${SSHO[@]}" "$HOST" 'mkdir -p /c/RSPS/file-server /c/RSPS/game/game/data/cache && tar -xf - -C /c/RSPS/file-server && cp -p /c/RSPS/file-server/cache/main_file_cache.* /c/RSPS/game/game/data/cache/'; then
                echo "$FP" > "$STAMP"; say "testcache bijgewerkt op de werklaptop in $(( $(date +%s) - TC )) s"
            else
                say "WAARSCHUWING: cache kopieren mislukt - tests die de cache lezen kunnen daar een oude stand zien"
            fi
        fi
    fi
fi

# ---- 4. daar uitpakken + bouwen ----
ARGS=""
for a in "$RDIR" "$NAME" "$@"; do ARGS="$ARGS $(printf '%q' "$a")"; done
# 18-11 (Claude, wachtwerk 19): a local stop (TaskStop / Ctrl+C / kill) ended only the ssh client; rbuild.sh on the work laptop kept the build
# slot for up to 30 min (orphans 7608/16117/6779). On INT/TERM/HUP: stop OUR rbuild for this work dir there (Windows pid via /proc/<pid>/winpid,
# taskkill /T = gradle client too), then exit. SIGKILL cannot be trapped - use TaskStop (SIGTERM), not kill -9.
stop_remote() {
    say "gestopt: remote rbuild voor $NAME afsluiten"
    ssh "${SSHO[@]}" "$HOST" "for p in \$(ps -ef | grep -F 'rbuild.sh $RDIR $NAME' | grep -v grep | awk '{print \$2}'); do w=\$(cat /proc/\$p/winpid 2>/dev/null); [ -n \"\$w\" ] && taskkill //F //T //PID \$w >/dev/null 2>&1; done" </dev/null 2>/dev/null
    [ -n "${SSHPID:-}" ] && kill "$SSHPID" 2>/dev/null
    rm -f "${FIFO:-}" 2>/dev/null
    exit 130
}
# 23-11 (Claude, WACHTEN-2311): (1) a build that STARTS a new Gradle/Kotlin daemon on the work laptop keeps the ssh channel open after
# '[rbuild] exit N' until that daemon dies (idle 3 h) -> rgradle hung for hours. So read the output line by line and close the ssh client
# ourselves as soon as rbuild has printed its exit line (the daemon may keep running there, that is fine).
# (2) RC=$? used to come AFTER 'trap -' and was therefore always 0 (failed builds looked green, 255/97 fallback never fired).
SSHPID=; RC=
FIFO="${TMPDIR:-/tmp}/rgradle-fifo.$$"
rm -f "$FIFO"; mkfifo "$FIFO" || { say "FOUT: mkfifo mislukt"; exit 2; }
trap stop_remote INT TERM HUP
ssh "${SSHO[@]}" -o ServerAliveInterval=20 -o ServerAliveCountMax=6 "$HOST" "bash /c/RSPS/rb/rbuild.sh$ARGS" </dev/null >"$FIFO" &
SSHPID=$!
while IFS= read -r line; do
    line=${line%$'\r'}
    printf '%s\n' "$line"
    case "$line" in "[rbuild] exit "*) RC=${line#"[rbuild] exit "}; RC=${RC%%[!0-9]*}; break ;; esac
done <"$FIFO"
if [ -n "$RC" ]; then
    # rbuild is done; give ssh 5 s to close by itself, otherwise (fresh daemon holds the channel) close it
    for _ in 1 2 3 4 5; do kill -0 "$SSHPID" 2>/dev/null || break; sleep 1; done
    if kill -0 "$SSHPID" 2>/dev/null; then kill "$SSHPID" 2>/dev/null; say "ssh bleef open na '[rbuild] exit $RC' (nieuwe daemon daar) -> zelf gesloten"; fi
    wait "$SSHPID" 2>/dev/null
else
    wait "$SSHPID"; RC=$?
    [ "$RC" = 0 ] && RC=255   # ssh ended without an exit line from rbuild = connection lost
fi
SSHPID=
trap - INT TERM HUP
rm -f "$FIFO"
say "gradle klaar op de werklaptop: exit $RC, totaal $(( $(date +%s) - T0 )) s"
# 255 = ssh-verbinding weg; 97 = de werklaptop kon de build niet starten (geen Gradle-uitslag) -> dezelfde opdracht lokaal
if [ "$RC" = 255 ] || [ "$RC" = 97 ]; then local_build "werklaptop viel weg tijdens de build (exit $RC)"; fi

# ---- 5. resultaten terug ----
if [ "${RGRADLE_NOPULL:-0}" != 1 ]; then
    # testrapporten van deze run (xml + html)
    ssh "${SSHO[@]}" "$HOST" "cd '$RDIR' && find . -newer .git/rb-start -type f \( -path '*/build/test-results/*' -o -path '*/build/reports/tests/*' \) -print0 | tar -cf - --null -T -" </dev/null 2>/dev/null | tar -xf - -C "$TOP" 2>/dev/null
    case " $* " in
        *installDist*)
            # alleen lib/*.jar van elke build/install/<naam> (NIET bin/data: dat is een kopie van data\ met cache en logs)
            for inst in $(ssh "${SSHO[@]}" "$HOST" "cd '$RDIR' && find . -maxdepth 5 -type d -path '*/build/install/*/lib' -print" </dev/null); do
                mkdir -p "$TOP/$inst"
                R="$TOP/$inst/.rb-remote.sha"; L="$TOP/$inst/.rb-local.sha"; NEED="$TOP/$inst/.rb-need"
                # 18-11 (Claude): only jars THIS run wrote (newer than .git/rb-start). game_game's install/lib on the work laptop stayed at
                # 17-11 15:36 while build/libs was fresh; the stale jars came back over a fresh local installDist (test server boot crash
                # on loc opcode 242). A stale lib is reported, never copied.
                ssh "${SSHO[@]}" "$HOST" "cd '$RDIR/$inst' && find . -maxdepth 1 -name '*.jar' -newer '$RDIR/.git/rb-start' -printf '%f\n' | xargs -r sha1sum" </dev/null | sed 's/ \*/  /' > "$R"
                OLD=$(ssh "${SSHO[@]}" "$HOST" "cd '$RDIR/$inst' && find . -maxdepth 1 -name '*-0.0.4.jar' ! -newer '$RDIR/.git/rb-start' | wc -l" </dev/null)
                [ "${OLD:-0}" -gt 0 ] && say "WAARSCHUWING: $OLD projectjars (*-0.0.4.jar) in $inst op de werklaptop zijn NIET door deze run geschreven (install verouderd?) - die blijven lokaal staan"
                if [ ! -s "$R" ]; then say "WAARSCHUWING: geen jar-lijst van de werklaptop voor $inst - niets teruggehaald"; rm -f "$R"; continue; fi
                (cd "$TOP/$inst" && sha1sum *.jar 2>/dev/null | sed 's/ \*/  /') > "$L"
                awk 'NR==FNR { l[$2]=$1; next } !($2 in l) || l[$2] != $1 { print $2 }' "$L" "$R" > "$NEED"
                N=$(wc -l < "$NEED")
                if [ "$N" -gt 0 ]; then
                    ssh "${SSHO[@]}" "$HOST" "cd '$RDIR/$inst' && tar -cf - -T -" < "$NEED" | tar -xf - -C "$TOP/$inst" || say "WAARSCHUWING: terughalen van $inst mislukt"
                fi
                # lokale jars die daar niet meer bestaan weg (oude jars op het classpath = fout); alleen *.jar in lib
                [ "${OLD:-0}" -gt 0 ] || awk 'NR==FNR { r[$2]=1; next } !($2 in r) { print $2 }' "$R" "$L" | while read -r f; do
                    case "$f" in */*|"") ;; *.jar) rm -f "$TOP/$inst/$f"; say "oude jar weg: $inst/$f" ;; esac
                done
                rm -f "$R" "$L" "$NEED"
                say "installDist terug: $inst ($N gewijzigde jars)"
            done ;;
    esac
fi
# A baseline is an immutable package, never independent fresh jars + stale tests.
if [ "$RC" = 0 ] && [ "${RGRADLE_NOPULL:-0}" != 1 ]; then
    STAGE=$(mktemp -d "$TOP/build/precheck-pull.XXXXXX")
    if (set -o pipefail; ssh "${SSHO[@]}" "$HOST" "cd '$RDIR' && p=\$(cat build/precheck-baseline.path) && grep -q '\"snapshot\":\"$SHA\"' \"\$p/manifest.json\" && tar -cf - -C \"\$p\" ." </dev/null | tar -xf - -C "$STAGE"); then
        python "$(dirname "$0")/precheck.py" --publish-pulled "$STAGE" "$SHA" || say "PRECHECK OVERGESLAGEN (baselinepakket niet gepubliceerd)"
    else
        say "PRECHECK OVERGESLAGEN (geen volledig baselinepakket van deze geslaagde build teruggehaald)"
    fi
fi
exit $RC
