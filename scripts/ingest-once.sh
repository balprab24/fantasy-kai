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
# Exit code is the app's: 0 on success, 1 if any source failed.

set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
jar="$repo/backend/target/backend-0.0.1-SNAPSHOT.jar"

if [[ -f "$repo/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$repo/.env"
    set +a
fi

if [[ ! -f "$jar" ]]; then
    echo "no jar at $jar -- run: cd backend && ./mvnw -B package" >&2
    exit 2
fi

mkdir -p "$repo/logs"

exec java -jar "$jar" \
    --fantasykai.ingest.once=true \
    --fantasykai.ingest.scheduled-enabled=false \
    --spring.main.web-application-type=none
