#!/usr/bin/env bash
#
# Phase 6 Slice 1: the projection backtest. Research code -- it reads the local database
# and writes a report; it builds no jar, touches no table and serves nothing.
#
#     ./scripts/backtest.sh validate      # 2021-23 train, 2024 makes every choice,
#                                         # writes backend/src/test/resources/projection/frozen-config.json
#     ./scripts/backtest.sh test          # the frozen choices, refit on 2021-24, 2025 read once
#     ./scripts/backtest.sh prospective   # the frozen choices, refit on 2021-25, 2026 so far
#
# Experiment v2 (air yards + league environment, on a corrected dataset):
#
#     ./scripts/backtest.sh v2-validate     # 2024 picks lambda and the candidate,
#                                           # writes backend/src/test/resources/projection/frozen-config-v2.json
#     ./scripts/backtest.sh v2-diagnostic   # frozen; 2025 shown, never chosen on
#     ./scripts/backtest.sh v2-prospective  # frozen; 2026's finished weeks -- the acceptance test
#
# v2 reads two nflverse files per season from .cache/nflverse/ (gitignored), downloading
# any that are missing exactly once. A cached file is never refreshed behind your back: the
# reports print every file's sha256, and deleting the cache is how you choose new data.
#
# Output lands in backend/target/backtest/ (gitignored): <stage>-results.md,
# <stage>-predictions.csv, and features.csv from validate.
#
# The test and prospective stages refuse to run until frozen-config.json is committed and
# unmodified. That is the pre-registration: the commit that froze the choices is older than
# any number the test season produced, and the report names it.
#
# Needs Docker Postgres (docker compose up -d) and Java 25. Nothing else.

set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
stage="${1:-validate}"
case "$stage" in
    validate|test|prospective|v2-validate|v2-diagnostic|v2-prospective) ;;
    *) echo "usage: $0 [validate|test|prospective|v2-validate|v2-diagnostic|v2-prospective]" >&2; exit 2 ;;
esac

frozen="backend/src/test/resources/projection/frozen-config.json"
if [[ "$stage" == v2-* ]]; then
    frozen="backend/src/test/resources/projection/frozen-config-v2.json"
fi
frozen_commit=""
if [[ "$stage" != validate && "$stage" != v2-validate ]]; then
    if ! git -C "$repo" ls-files --error-unmatch "$frozen" >/dev/null 2>&1; then
        echo "refusing the $stage stage: $frozen is not committed." >&2
        echo "run validate, commit its frozen-config.json, then run $stage." >&2
        exit 1
    fi
    if ! git -C "$repo" diff --quiet HEAD -- "$frozen"; then
        echo "refusing the $stage stage: $frozen differs from its committed version." >&2
        exit 1
    fi
    frozen_commit="$(git -C "$repo" log -1 --format=%H -- "$frozen")"
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    JAVA_HOME="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    export JAVA_HOME
fi
# java_home exits 0 and returns some other JDK when 25 is absent: read what java PRINTS.
java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/')"
if [[ "$java_version" != "25" ]]; then
    echo "JAVA_HOME is Java $java_version, not 25: $JAVA_HOME" >&2
    exit 1
fi

if [[ -f "$repo/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$repo/.env"
    set +a
fi
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5433/fantasykai}"
export DB_USERNAME="${DB_USERNAME:-fantasykai}"
export DB_PASSWORD="${DB_PASSWORD:-fantasykai}"

# Name exactly what is uncommitted rather than flagging "changes": a whitespace edit to an
# unrelated file and an edit to the model are not the same claim, and the report is evidence.
head="\`$(git -C "$repo" rev-parse --short HEAD)\`"
dirty="$(git -C "$repo" status --porcelain -- backend/src backend/pom.xml scripts/backtest.sh \
    | awk '{print $2}' | paste -sd ',' - | sed 's/,/, /g')"
if [[ -n "$dirty" ]]; then
    head="$head + uncommitted: $dirty"
fi
export BACKTEST_HEAD="$head"

if [[ "$stage" == v2-* ]]; then
    cache="$repo/.cache/nflverse"
    mkdir -p "$cache"
    base="https://github.com/nflverse/nflverse-data/releases/download"
    last=2026
    [[ "$stage" == v2-validate ]] && last=2024     # validation never even downloads later seasons
    [[ "$stage" == v2-diagnostic ]] && last=2025
    for season in $(seq 2020 "$last"); do
        for asset in "stats_player/stats_player_week_$season.csv" "snap_counts/snap_counts_$season.csv"; do
            file="$cache/$(basename "$asset")"
            if [[ ! -s "$file" ]]; then
                echo "downloading $(basename "$asset") into the cache" >&2
                curl -fsSL --retry 3 -o "$file.part" "$base/$asset"
                mv "$file.part" "$file"
            fi
        done
    done
    export NFLVERSE_CACHE="$cache"
fi
if [[ -n "$frozen_commit" ]]; then
    since="$(git -C "$repo" log --format=%h "$frozen_commit"..HEAD -- \
        backend/src/test/java/com/fantasykai/projection scripts/backtest.sh | paste -sd ' ' -)"
    export BACKTEST_SINCE_FREEZE="${since:-none}"
fi

cd "$repo/backend"
./mvnw -q -B -DskipTests test-compile dependency:build-classpath \
    -Dmdep.outputFile=target/backtest.classpath -Dmdep.includeScope=test
"$JAVA_HOME/bin/java" -cp "target/test-classes:target/classes:$(cat target/backtest.classpath)" \
    com.fantasykai.projection.Backtest "$stage" "$frozen_commit"
