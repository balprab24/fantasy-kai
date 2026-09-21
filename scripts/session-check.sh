#!/usr/bin/env bash
#
# What is actually true about this repo, right now, in about three seconds.
#
# Read-only. No Maven, no Docker build, no writes to anything but logs/.
# Runs at the top of every session via the SessionStart hook in
# .claude/settings.json, and by hand whenever you want the real numbers.
#
# THE RULE THAT MAKES IT WORTH TRUSTING: a check that cannot run prints "?",
# never "ok". Docker down, psql unreachable, gh unauthenticated -- all of those
# are unknown, and unknown is reported as unknown. This is CsvValues.shortValue
# one layer up: an absent reading and a passing reading must not look the same.
#
#     ok     verified true just now
#     warn   true but worth knowing
#     drift  a number in the docs disagrees with the tree -- fix both
#     FAIL   broken right now
#     ?      could not be checked, and the reason is printed
#
# Exit 0 when nothing is FAIL or drift, else 1 -- so it can become a CI or
# pre-commit gate later without being rewritten.
#
#     ./scripts/session-check.sh                 # everything
#     ./scripts/session-check.sh --no-network    # skip the two gh calls
#     ./scripts/session-check.sh --hook          # emit the SessionStart hook JSON

set -uo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/jar-state.sh
source "$repo/scripts/lib/jar-state.sh"
cd "$repo" || exit 70

# ─── Claims mirrored from the docs ──────────────────────────────────────────
#
# Every value below is ALSO written in prose somewhere. That duplication is
# allowed for exactly one reason: this script re-derives the real value and
# tells you when the two stop agreeing. When a row drifts, fix the doc AND this
# block in the same commit -- a number that lives in two places and is only
# corrected in one is worse than a number that lives in neither.
#
# Row counts are floors, not equalities: the season is running and stat rows
# grow every week. Fewer rows than recorded is a FAIL; more is fine and the
# surplus is printed.
EXPECT_STAT_ROWS=114479     # CLAUDE.md "Measured numbers" · docs/map.md §1 (re-measured 2026-09-21)
EXPECT_PLAYERS=25066        # CLAUDE.md "Measured numbers" · docs/map.md §1 (re-measured 2026-09-21)
EXPECT_GAMES=1965           # docs/map.md §1
EXPECT_TESTS=136            # CLAUDE.md "Current state" · docs/map.md §1 · README
EXPECT_ENDPOINTS=12         # docs/map.md §1 (5 public GET + 4 auth + 3 mutations)
EXPECT_MIGRATIONS=5         # docs/map.md §1 ("V1 … V5")
EXPECT_BACKEND_FILES=73     # docs/map.md §1
EXPECT_FRONTEND_FILES=21    # docs/map.md §1 (.ts/.tsx under frontend/src)
EXPECT_JDK=25               # CLAUDE.md "Commands" · README · pom.xml enforcer

# The deployed API's hostname, and it is deliberately empty until Phase 5d has
# actually landed one. Empty means the DEPLOY rows print "?" with that reason --
# never "ok", because "nothing is deployed" and "the deploy is fine" must not
# look the same. Override for a staging host with FK_DEPLOY_HOST=... .
DEPLOY_HOST="${FK_DEPLOY_HOST:-}"   # docs/north-star.md §5d · deploy/README.md

# Mirrors IngestFreshnessHealthIndicator.STALE_AFTER exactly. One missed 06:00
# is a laptop that slept; two is a stopped pipeline. If that constant moves,
# move this one -- they are the same claim about the same table.
STALE_AFTER_HOURS=36

no_network=0
hook_mode=0
for arg in "$@"; do
    case "$arg" in
        --no-network) no_network=1 ;;
        --hook) hook_mode=1 ;;
        -h|--help) sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "unknown argument: $arg" >&2; exit 64 ;;
    esac
done
[[ -n "${FK_CHECK_NO_NETWORK:-}" ]] && no_network=1

# Flyway's checksum is a CRC32 accumulated over each line's bytes with the line
# terminator excluded, stored signed. Reimplemented rather than inferred:
# verified against all five applied migrations on 2026-09-14, and the algorithm
# must be re-verified against an untouched migration before it is ever changed.
read -r -d '' PY_FLYWAY_CRC <<'PY'
import sys, zlib, os
migdir = sys.argv[1]
bad = []
for line in sys.stdin.read().splitlines():
    if not line.strip():
        continue
    version, script, recorded = line.split("|")
    path = os.path.join(migdir, script)
    if not os.path.exists(path):
        bad.append(f"{script} applied but missing from disk")
        continue
    crc = 0
    with open(path, "r", encoding="utf-8") as fh:
        for text in fh:
            crc = zlib.crc32(text.rstrip("\r\n").encode("utf-8"), crc)
    signed = crc - (1 << 32) if crc >= (1 << 31) else crc
    if str(signed) != recorded:
        bad.append(f"{script} edited since it was applied ({recorded} -> {signed})")
print("; ".join(bad))
PY

n_fail=0; n_drift=0; n_warn=0; n_unknown=0

row()     { printf '  %-5s %-11s %s\n' "$1" "$2" "$3"; }
ok()      { row "ok"    "$1" "$2"; }
warn()    { row "warn"  "$1" "$2"; n_warn=$((n_warn + 1)); }
drift()   { row "drift" "$1" "$2"; n_drift=$((n_drift + 1)); }
fail()    { row "FAIL"  "$1" "$2"; n_fail=$((n_fail + 1)); }
unknown() { row "?"     "$1" "$2"; n_unknown=$((n_unknown + 1)); }
group()   { printf '\n%s\n' "$1"; }

# "absent" and "running but unhealthy" are different problems with different
# fixes, so do not collapse them into one boolean.
state_of() {
    local name="$1" ps_out="$2" line
    line="$(grep "^$name " <<<"$ps_out")"
    if [[ -z "$line" ]]; then echo "absent"
    elif [[ "$line" == *"(healthy)"* ]]; then echo "healthy"
    elif [[ "$line" == *"(starting)"* ]]; then echo "still starting"
    elif [[ "$line" == *"(unhealthy)"* ]]; then echo "UNHEALTHY"
    else echo "up, no healthcheck result"; fi
}

commafy() { echo "$1" | sed -e :a -e 's/\(.*[0-9]\)\([0-9]\{3\}\)/\1,\2/;ta'; }

fmt_epoch() {
    # BSD date takes -r, GNU date takes -d @; try both rather than assume a platform.
    date -r "$1" '+%Y-%m-%d %H:%M' 2>/dev/null || date -d "@$1" '+%Y-%m-%d %H:%M' 2>/dev/null || echo "epoch $1"
}

human_age() {
    local s="$1" d h
    d=$((s / 86400)); h=$(((s % 86400) / 3600))
    if (( d > 0 )); then echo "${d}d ${h}h"; else echo "${h}h $(((s % 3600) / 60))m"; fi
}

# macOS ships no timeout(1) and this runs on a session-start hook, so a network
# call that hangs would hang the session. Bound it.
with_timeout() {
    local secs="$1"; shift
    "$@" &
    local pid=$! waited=0
    while kill -0 "$pid" 2>/dev/null; do
        if (( waited >= secs * 10 )); then
            kill -9 "$pid" 2>/dev/null; wait "$pid" 2>/dev/null; return 124
        fi
        sleep 0.1; waited=$((waited + 1))
    done
    wait "$pid"
}

db_up=0
psql_q() {
    docker compose -f "$repo/docker-compose.yml" exec -T postgres \
        psql -U fantasykai -d fantasykai -At -F'|' -c "$1" 2>/dev/null
}

# Exact count, not a match on "@Test" -- that would also catch @Testcontainers,
# @TestConfiguration and @TestPropertySource, and did, reporting 147 for 132.
# Each prints NOTHING when the tree it counts is absent, and a number -- zero
# included -- when it is present. A count of 0 from a directory that exists is
# a real answer and must read as drift, not as "could not check": if every test
# were deleted, "nothing counted, the tree may be incomplete" would be the
# reassuring version of a five-alarm fire.
count_tests()     { [[ -d "$repo/backend/src/test/java" ]] || return 0; grep -rhoE '@Test\b' "$repo/backend/src/test/java" 2>/dev/null | wc -l | tr -d ' '; }
count_endpoints() { [[ -d "$repo/backend/src/main/java" ]] || return 0; grep -rhoE '@(Get|Post|Put|Patch|Delete)Mapping' "$repo/backend/src/main/java" 2>/dev/null | wc -l | tr -d ' '; }
count_backend()   { [[ -d "$repo/backend/src/main/java" ]] || return 0; find "$repo/backend/src/main/java" -name '*.java' | wc -l | tr -d ' '; }
count_frontend()  { [[ -d "$repo/frontend/src" ]] || return 0; find "$repo/frontend/src" -type f \( -name '*.ts' -o -name '*.tsx' \) | wc -l | tr -d ' '; }
count_migrations(){ [[ -d "$repo/backend/src/main/resources/db/migration" ]] || return 0; find "$repo/backend/src/main/resources/db/migration" -name 'V*.sql' | wc -l | tr -d ' '; }

claim() {
    local label="$1" actual="$2" expect="$3" doc="$4"
    if [[ -z "$actual" ]]; then
        unknown "$label" "the tree this counts is not present -- not checked"
    elif [[ "$actual" == "$expect" ]]; then
        ok "$label" "$actual"
    else
        drift "$label" "tree says $actual, $doc says $expect"
    fi
}

check_toolchain() {
    group "TOOLCHAIN"

    local jh ver line
    jh="$(/usr/libexec/java_home -v "$EXPECT_JDK" 2>/dev/null)"
    if [[ -z "$jh" || ! -x "$jh/bin/java" ]]; then
        unknown "java" "java_home -v $EXPECT_JDK resolved nothing"
    else
        line="$("$jh/bin/java" -version 2>&1 | head -1)"
        ver="$(sed -E 's/.*"([0-9]+).*/\1/' <<<"$line")"
        # java_home returns the NEWEST jdk it has and exits 0 when the asked-for
        # version is absent, so the answer is in what java prints, never in $?.
        if [[ "$ver" == "$EXPECT_JDK" ]]; then
            ok "java" "$(sed -E 's/.*"([^"]+)".*/\1/' <<<"$line") at $(sed "s|$HOME|~|" <<<"$jh")"
        else
            fail "java" "java_home -v $EXPECT_JDK returned Java $ver -- the JDK 25 is invisible to it (it scans only /Library/Java/JavaVirtualMachines and ~/Library/...)"
        fi
    fi

    local ps_out
    ps_out="$(docker ps --format '{{.Names}} {{.Status}}' 2>/dev/null)"
    if [[ -z "$ps_out" ]] && ! docker info >/dev/null 2>&1; then
        unknown "docker" "daemon not reachable -- SCHEMA and DATA cannot be checked"
    else
        db_up=1
        local pg redis
        pg="$(state_of fantasykai-postgres "$ps_out")"
        redis="$(state_of fantasykai-redis "$ps_out")"
        if [[ "$pg" == "healthy" && "$redis" == "healthy" ]]; then
            ok "docker" "postgres healthy · redis healthy (:5433, :6379)"
        elif [[ "$pg" == "healthy" ]]; then
            warn "docker" "postgres healthy · redis $redis -- logins and rate limiting will fail, reads will not"
        else
            db_up=0
            fail "docker" "fantasykai-postgres is $pg -- run: docker compose up -d"
        fi
    fi

    local want have
    want="$(tr -d ' \n' <"$repo/frontend/.nvmrc" 2>/dev/null)"
    have="$(node -v 2>/dev/null | tr -d 'v')"
    if [[ -z "$want" ]]; then
        unknown "node" "no frontend/.nvmrc"
    elif [[ -z "$have" ]]; then
        unknown "node" "node is not on PATH"
    elif [[ "${have%%.*}" == "${want%%.*}" ]]; then
        ok "node" "v$have (.nvmrc wants $want)"
    else
        warn "node" "shell is v$have, .nvmrc wants $want -- run 'nvm use' in frontend/"
    fi

    if [[ ! -f "$repo/.env" ]]; then
        fail "env" ".env is missing -- cp .env.example .env; the app refuses to start without JWT_SECRET"
    else
        local secret
        secret="$(grep -E '^JWT_SECRET=' "$repo/.env" | head -1 | cut -d= -f2- | tr -d '"\047')"
        if [[ -z "$secret" ]]; then
            fail "env" ".env has no JWT_SECRET -- AuthProperties refuses to start"
        elif (( ${#secret} < 32 )); then
            fail "env" "JWT_SECRET is ${#secret} bytes; AuthProperties requires at least 32"
        else
            ok "env" ".env present · JWT_SECRET ${#secret} bytes"
        fi
    fi
}

check_repo() {
    group "REPO"

    local branch dirty
    branch="$(git -C "$repo" rev-parse --abbrev-ref HEAD 2>/dev/null)"
    dirty="$(git -C "$repo" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
    if [[ -z "$branch" ]]; then
        unknown "branch" "not a git repository?"
    elif [[ "$dirty" == "0" ]]; then
        ok "branch" "$branch, clean"
    else
        warn "branch" "$branch, $dirty uncommitted file(s)"
    fi

    local counts behind ahead
    counts="$(git -C "$repo" rev-list --left-right --count '@{u}...HEAD' 2>/dev/null)"
    if [[ -z "$counts" ]]; then
        warn "sync" "$branch has no upstream yet"
    else
        behind="${counts%%[[:space:]]*}"; ahead="${counts##*[[:space:]]}"
        if [[ "$behind" == "0" && "$ahead" == "0" ]]; then
            ok "sync" "level with origin/$branch"
        else
            warn "sync" "$ahead ahead, $behind behind origin/$branch"
        fi
    fi

    local merged
    merged="$(git -C "$repo" branch --merged origin/main 2>/dev/null \
        | grep -v '^\*' | sed 's/^[ ]*//' | grep -vx 'main' | tr '\n' ' ' | sed 's/ $//')"
    if [[ -n "$merged" ]]; then
        warn "branches" "merged into origin/main, safe to delete: $merged"
    else
        ok "branches" "no merged local branches left over"
    fi

    if (( no_network )); then
        unknown "github" "skipped (--no-network)"
    elif ! command -v gh >/dev/null 2>&1; then
        unknown "github" "gh is not installed"
    else
        local tmp prs conc
        tmp="$(mktemp)"
        if with_timeout 8 gh pr list --state open --json number --jq 'length' >"$tmp" 2>/dev/null; then
            prs="$(tr -d ' \n' <"$tmp")"
        else
            prs=""
        fi
        if with_timeout 8 gh run list --branch main --limit 1 \
                --json conclusion --jq '.[0].conclusion' >"$tmp" 2>/dev/null; then
            conc="$(tr -d ' \n' <"$tmp")"
        else
            conc=""
        fi
        rm -f "$tmp"
        if [[ -z "$prs" && -z "$conc" ]]; then
            unknown "github" "gh returned nothing -- unauthenticated, offline, or timed out"
        elif [[ -z "$conc" ]]; then
            # Half an answer is not an answer. The PR count came back; the CI
            # conclusion did not, and printing ok over that is the same sin as
            # printing ok for a check that never ran.
            unknown "github" "${prs:-?} open PR(s) · could not read the last CI run on main"
        elif [[ "$conc" == "success" ]]; then
            ok "github" "$prs open PR(s) · last CI on main: success"
        else
            fail "github" "${prs:-?} open PR(s) · last CI on main: $conc"
        fi
    fi
}

check_schema() {
    group "SCHEMA"

    local on_disk
    on_disk="$(count_migrations)"

    if (( ! db_up )); then
        unknown "migrations" "$on_disk file(s) on disk; the database could not be read"
        return
    fi

    local applied
    applied="$(psql_q "SELECT version || '|' || script || '|' || checksum FROM flyway_schema_history WHERE type = 'SQL' ORDER BY installed_rank;")"
    if [[ -z "$applied" ]]; then
        unknown "migrations" "$on_disk file(s) on disk; flyway_schema_history could not be read"
        return
    fi

    local applied_n
    applied_n="$(wc -l <<<"$applied" | tr -d ' ')"
    if [[ "$applied_n" != "$on_disk" ]]; then
        fail "migrations" "$on_disk file(s) on disk but $applied_n applied -- the database is behind or ahead"
        return
    fi

    if ! command -v python3 >/dev/null 2>&1; then
        unknown "checksums" "python3 absent; count matches ($applied_n) but checksums were not compared"
        ok "migrations" "V1..V$applied_n on disk and applied"
        return
    fi

    # Flyway's checksum is a CRC32 accumulated over each line's bytes with the
    # line terminator excluded, stored signed. Reimplemented rather than
    # inferred: verified against all five applied migrations on 2026-09-14.
    #
    # This is the one check CI structurally cannot make. Testcontainers always
    # starts from an empty database, so an edited applied migration is green in
    # CI and fatal on the next local startup -- which is exactly how V4 bit.
    local mismatch
    mismatch="$(python3 -c "$PY_FLYWAY_CRC" "$repo/backend/src/main/resources/db/migration" <<<"$applied")"
    if [[ -n "$mismatch" ]]; then
        fail "migrations" "$mismatch -- startup will fail Flyway validation; run 'flyway repair' or revert the edit"
    else
        ok "migrations" "V1..V$applied_n applied, all $applied_n checksums match the files on disk"
    fi
}

check_data() {
    group "DATA"

    if (( db_up )); then
        local counts stats players games
        counts="$(psql_q "SELECT (SELECT count(*) FROM player_game_stats) || '|' || (SELECT count(*) FROM players) || '|' || (SELECT count(*) FROM games);")"
        if [[ -z "$counts" ]]; then
            unknown "rows" "could not query the database"
        else
            IFS='|' read -r stats players games <<<"$counts"
            local note=""
            (( stats > EXPECT_STAT_ROWS )) && note=" (+$(commafy $((stats - EXPECT_STAT_ROWS))) since the docs)"
            if (( stats < EXPECT_STAT_ROWS || players < EXPECT_PLAYERS || games < EXPECT_GAMES )); then
                fail "rows" "stats $(commafy "$stats") · players $(commafy "$players") · games $(commafy "$games") -- FEWER than recorded ($(commafy "$EXPECT_STAT_ROWS")/$(commafy "$EXPECT_PLAYERS")/$(commafy "$EXPECT_GAMES")). Rows do not vanish on their own"
            else
                ok "rows" "stats $(commafy "$stats") · players $(commafy "$players") · games $(commafy "$games")$note"
            fi
        fi

        local ing age_s failed newest
        ing="$(psql_q "SELECT coalesce(extract(epoch FROM now() - max(started_at))::bigint, -1) || '|' || coalesce(extract(epoch FROM max(started_at))::bigint::text, '0') || '|' || (SELECT count(*) FROM (SELECT DISTINCT ON (source) status FROM ingest_runs ORDER BY source, started_at DESC) t WHERE status = 'FAILED') FROM ingest_runs;")"
        if [[ -z "$ing" ]]; then
            unknown "ingest" "could not read ingest_runs"
        else
            IFS='|' read -r age_s newest_epoch failed <<<"$ing"
            newest="$(fmt_epoch "$newest_epoch")"
            local month in_season=0
            month="$(date +%m)"
            [[ "$month" == "09" || "$month" == "10" || "$month" == "11" || "$month" == "12" || "$month" == "01" || "$month" == "02" ]] && in_season=1
            if [[ "$age_s" == "-1" ]]; then
                fail "ingest" "ingest_runs is empty -- the pipeline has never run"
            elif (( failed > 0 )); then
                fail "ingest" "$failed source(s) whose most recent run FAILED -- last run $newest"
            elif (( in_season && age_s > STALE_AFTER_HOURS * 3600 )); then
                fail "ingest" "last run $newest, $(human_age "$age_s") ago -- past the ${STALE_AFTER_HOURS}h in-season threshold"
            elif (( in_season )); then
                ok "ingest" "last run $newest, $(human_age "$age_s") ago (stale after ${STALE_AFTER_HOURS}h)"
            else
                ok "ingest" "last run $newest, $(human_age "$age_s") ago -- out of season, the pull is a no-op"
            fi
        fi
    else
        unknown "rows" "database unreachable"
        unknown "ingest" "database unreachable"
    fi

    local ld status
    ld="$(launchctl list 2>/dev/null | grep -E '[[:space:]]com\.fantasykai\.ingest$')"
    if [[ -z "$ld" ]]; then
        warn "launchd" "com.fantasykai.ingest is not loaded -- run ./scripts/install-ingest.sh"
    else
        status="$(awk '{print $2}' <<<"$ld")"
        if [[ "$status" == "0" ]]; then
            ok "launchd" "com.fantasykai.ingest loaded, last exit 0"
        else
            fail "launchd" "com.fantasykai.ingest last exited $status -- see logs/ingest.log. This is 36h louder than the freshness check"
        fi
    fi

    fk_jar_state "$repo"
    case "$FK_JAR_STATE" in
        fresh)
            if [[ "$FK_JAR_BASIS" == "mtime" ]]; then
                warn "jar" "$FK_JAR_REASON"
            else
                ok "jar" "$FK_JAR_REASON"
            fi
            ;;
        stale)   fail "jar" "$FK_JAR_REASON -- the daily ingest will refuse to run" ;;
        missing) fail "jar" "$FK_JAR_REASON" ;;
    esac
}

check_deploy() {
    group "DEPLOY"

    if [[ -z "$DEPLOY_HOST" ]]; then
        unknown "liveness" "no deploy host configured -- set DEPLOY_HOST in this script once Phase 5d lands, or FK_DEPLOY_HOST to point at one"
        return
    fi
    if (( no_network )); then
        unknown "liveness" "--no-network"
        return
    fi
    if ! command -v curl >/dev/null 2>&1; then
        unknown "liveness" "curl not found"
        return
    fi

    local base="https://$DEPLOY_HOST"

    # --max-time 4, not 10, and twice at most. This script's own header promises
    # "about three seconds" and it runs at every session start; two ten-second
    # network calls would quietly make that false the first time DNS was slow.
    # A slow deploy is reported as unreachable, which is honest: from here,
    # "took longer than the check is allowed to wait" and "cannot be reached"
    # are the same observation.
    local timeout=4

    # Liveness is ping + db only. It is the row that says "the JVM is alive and
    # can reach its database", which is also the row that says IngestScheduler
    # has a process to fire inside -- the whole reason this deploy exists.
    local code
    code="$(curl -sS --max-time "$timeout" -o /dev/null -w '%{http_code}' "$base/actuator/health/liveness" 2>/dev/null)"
    case "$code" in
        200) ok   "liveness" "$base is up" ;;
        000|"") unknown "liveness" "$base unreachable (DNS, TLS or timeout) -- not the same as down" ;;
        *)   fail "liveness" "$base/actuator/health/liveness returned $code" ;;
    esac

    # The aggregate. Anonymous callers get a status and no components
    # (show-details: when-authorized), so a DOWN here names no cause and this
    # must not pretend otherwise. It is still worth printing: DOWN with liveness
    # UP means something beyond ping/db is unhappy, and ingestFreshness is the
    # component that usually is.
    # Non-greedy by construction: take the FIRST status, which is the aggregate.
    # `.*status:` would be greedy and, the day show-details is ever widened or a
    # component list appears in the body, would silently report the LAST
    # component's status as though it were the overall one.
    local agg
    agg="$(curl -sS --max-time "$timeout" "$base/actuator/health" 2>/dev/null \
           | tr -d ' "' | sed -n 's/^[^s]*status:\([A-Z_]*\).*/\1/p' | head -1)"
    case "$agg" in
        UP)   ok   "health" "aggregate UP" ;;
        DOWN) warn "health" "aggregate DOWN with liveness up -- a component is degraded, but components are when-authorized so the cause is not visible from here" ;;
        "")   unknown "health" "no status in the response body" ;;
        *)    warn "health" "aggregate $agg" ;;
    esac
}

check_claims() {
    group "CLAIMS  (a number here disagreeing with the docs means one of them is wrong)"
    claim "tests"      "$(count_tests)"     "$EXPECT_TESTS"          "CLAUDE.md"
    claim "endpoints"  "$(count_endpoints)" "$EXPECT_ENDPOINTS"      "map.md §1"
    claim "backend"    "$(count_backend)"   "$EXPECT_BACKEND_FILES"  "map.md §1"
    claim "frontend"   "$(count_frontend)"  "$EXPECT_FRONTEND_FILES" "map.md §1"
    claim "migrations" "$(count_migrations)" "$EXPECT_MIGRATIONS"    "map.md §1"
}

run_all() {
    printf 'fantasy-kai — session check · %s\n' "$(date '+%Y-%m-%d %H:%M %Z')"
    check_toolchain
    check_repo
    check_schema
    check_data
    check_deploy
    check_claims
    printf '\n'
    printf 'not checked here: does the suite pass · does the frontend build\n'
    printf '  the gates for those are in CLAUDE.md under "Definition of done".\n'
    printf '\n'
    printf '%d FAIL · %d drift · %d warn · %d unknown\n' "$n_fail" "$n_drift" "$n_warn" "$n_unknown"
}

report="$(mktemp)"
run_all >"$report"

mkdir -p "$repo/logs"
{ cat "$report"; printf '\n'; } >>"$repo/logs/session-check.log"

if (( hook_mode )); then
    if command -v python3 >/dev/null 2>&1; then
        python3 - "$report" "$n_fail" "$n_drift" "$n_warn" "$n_unknown" <<'PY'
import json, sys
report = open(sys.argv[1]).read()
n_fail, n_drift, n_warn, n_unknown = (int(x) for x in sys.argv[2:6])
# The user gets the rows that need a person; the model gets everything.
attention = [ln for ln in report.splitlines()
             if ln.startswith(("  FAIL", "  drift", "  warn", "  ?"))]
head = f"session-check: {n_fail} FAIL · {n_drift} drift · {n_warn} warn · {n_unknown} unknown"
msg = head if not attention else head + "\n" + "\n".join(attention)
print(json.dumps({
    "systemMessage": msg,
    "suppressOutput": True,
    "hookSpecificOutput": {
        "hookEventName": "SessionStart",
        "additionalContext": (
            "Output of ./scripts/session-check.sh, run automatically at session start. "
            "These are re-derived facts about the working tree and database; where they "
            "disagree with CLAUDE.md or docs/, THIS wins and the doc is what needs "
            "fixing.\n\n" + report),
    },
}))
PY
    else
        # No python3: plain text still reaches the session, and saying so is
        # better than emitting JSON this shell would have to escape by hand.
        echo "session-check (python3 absent, plain output):"
        cat "$report"
    fi
    rm -f "$report"
    exit 0
fi

if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
    sed -E \
        -e 's/^(  )(FAIL )/\1'$'\033''[1;31m\2'$'\033''[0m/' \
        -e 's/^(  )(drift)/\1'$'\033''[1;33m\2'$'\033''[0m/' \
        -e 's/^(  )(warn )/\1'$'\033''[33m\2'$'\033''[0m/' \
        -e 's/^(  )(ok   )/\1'$'\033''[32m\2'$'\033''[0m/' \
        -e 's/^(  )(\?    )/\1'$'\033''[36m\2'$'\033''[0m/' \
        -e 's/^([A-Z][A-Z ]+.*)$/'$'\033''[1m\1'$'\033''[0m/' \
        "$report"
else
    cat "$report"
fi
rm -f "$report"

(( n_fail > 0 || n_drift > 0 )) && exit 1
exit 0
