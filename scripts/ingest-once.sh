#!/usr/bin/env bash
#
# One current-season nflverse + Sleeper pull, then exit. This is what the daily
# launchd job runs; it is also fine to run by hand.
#
# Two things worth knowing before you trust the schedule:
#
#   1. launchd fires on the machine's LOCAL time, not US Eastern. Only the
#      in-app @Scheduled job honours America/New_York. If this Mac is not on
#      ET, "6am" means 6am wherever it is -- which is fine for a data pull
#      that has no deadline, but do not describe it as "6am ET".
#   2. A sleeping Mac does not run the job at 06:00; launchd runs it on wake.
#      Expect gaps, and expect ingest_runs to show them honestly.
#
# Until nflverse publishes the current season's stat file (after week 1),
# stats_player_week and snap_counts record SKIPPED rather than failing. That is
# correct: a season that has not started yet is not an error.
#
# Exit code is the app's: 0 on success, 1 if any source failed, 2 if this script
# refused to start.

set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/jar-state.sh
source "$repo/scripts/lib/jar-state.sh"

if [[ -f "$repo/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$repo/.env"
    set +a
fi

# A stale jar is worse than a missing one: the job runs, reports SUCCESS, and
# silently ingests with last week's code. Refuse rather than lie.
#
# "Stale" used to mean "some file under backend/src has a newer mtime", and that
# cost two days of ingest on 2026-09-13/14: a git operation rewrote 42 files'
# mtimes at 23:49 on the 12th, 14 minutes after the jar was packaged, without
# changing a byte of any of them. The guard refused a jar built from exactly the
# source it was comparing against, twice, and the only symptom was exit 2 in a
# log nobody reads. See scripts/lib/jar-state.sh -- staleness is now a question
# about content.
set +e
fk_jar_state "$repo"
jar_rc=$?
set -e

case "$jar_rc" in
    0) ;;
    2) echo "$FK_JAR_REASON" >&2; exit 2 ;;
    *) echo "refusing to ingest with a stale jar: $FK_JAR_REASON" >&2; exit 2 ;;
esac

if [[ "$FK_JAR_BASIS" == "mtime" ]]; then
    echo "note: no source hash beside the jar, so freshness was checked by mtime," >&2
    echo "      which is the weaker test. Rebuild with ./scripts/package.sh." >&2
fi

mkdir -p "$repo/logs"

exec java -jar "$FK_JAR" \
    --fantasykai.ingest.once=true \
    --fantasykai.ingest.scheduled-enabled=false \
    --spring.main.web-application-type=none
