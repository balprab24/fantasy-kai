#!/usr/bin/env bash
#
# Copy the loaded local database to Neon (or any empty Postgres 16) for Phase 5d.
#
# Why a dump rather than re-running the backfill against Neon: the six-season
# backfill is 22.8s against a container on localhost and a great deal longer
# against a managed database an internet away, and it re-fetches ~180 MB from
# nflverse to reproduce rows that already exist. Measured database size is 33 MB
# after VACUUM FULL, which is inside every free tier worth using.
#
# Why a FULL dump and not --data-only: a data-only restore checks foreign keys
# row by row as it loads, so it depends on table ordering and wants
# --disable-triggers, which needs a superuser Neon does not hand out. A full
# dump creates every constraint AFTER the data lands, and it carries
# flyway_schema_history with it -- so the app boots, Flyway validates, finds
# version 5 already applied and does nothing. Migrations stay the schema's
# owner; this just skips re-deriving what is already derived.
#
# Usage:
#   ./scripts/neon-restore.sh 'postgresql://user:pass@host/db?sslmode=require'
#
# The target must be EMPTY. This refuses a database that already has tables
# rather than half-merging into one.

set -euo pipefail

TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
    echo "usage: $0 <target-postgres-url>" >&2
    echo "example: $0 'postgresql://user:pass@ep-x.aws.neon.tech/fantasykai?sslmode=require'" >&2
    exit 2
fi

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dump="$repo/.tmp/fantasykai-$(date +%Y%m%d-%H%M%S).sql"
mkdir -p "$repo/.tmp"

# The source is the compose container, which is on 5433 because a Homebrew
# postgresql@16 owns 5432 on this machine.
src_psql=(docker compose -f "$repo/docker-compose.yml" exec -T postgres psql -U fantasykai -d fantasykai)
src_dump=(docker compose -f "$repo/docker-compose.yml" exec -T postgres pg_dump -U fantasykai -d fantasykai)

echo "==> refusing to continue unless the target is empty"
existing=$(psql "$TARGET" -tAc \
    "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public'")
if [[ "$existing" != "0" ]]; then
    echo "target already has $existing table(s) in public. Refusing." >&2
    echo "Drop them first, or point at a fresh database." >&2
    exit 1
fi

echo "==> recording source row counts (these are what the restore is checked against)"
counts_before=$("${src_psql[@]}" -tAF',' -c "
    SELECT 'player_game_stats', count(*) FROM player_game_stats
    UNION ALL SELECT 'players', count(*) FROM players
    UNION ALL SELECT 'games', count(*) FROM games
    UNION ALL SELECT 'teams', count(*) FROM teams
    UNION ALL SELECT 'scoring_profiles', count(*) FROM scoring_profiles
    ORDER BY 1")
echo "$counts_before"

echo "==> dumping"
"${src_dump[@]}" --no-owner --no-privileges > "$dump"
echo "    $(du -h "$dump" | cut -f1) -> $dump"

echo "==> restoring into target"
psql "$TARGET" -v ON_ERROR_STOP=1 --quiet -f "$dump"

echo "==> verifying row counts match the source"
counts_after=$(psql "$TARGET" -tAF',' -c "
    SELECT 'player_game_stats', count(*) FROM player_game_stats
    UNION ALL SELECT 'players', count(*) FROM players
    UNION ALL SELECT 'games', count(*) FROM games
    UNION ALL SELECT 'teams', count(*) FROM teams
    UNION ALL SELECT 'scoring_profiles', count(*) FROM scoring_profiles
    ORDER BY 1")
echo "$counts_after"

if [[ "$counts_before" != "$counts_after" ]]; then
    echo "ROW COUNTS DIFFER between source and target. Do not deploy against this." >&2
    diff <(echo "$counts_before") <(echo "$counts_after") >&2 || true
    exit 1
fi

echo "==> flyway state on the target (the app must find v5 and do nothing)"
psql "$TARGET" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"

echo
echo "OK. Row counts match. Set DB_URL/DB_USERNAME/DB_PASSWORD as Fly secrets:"
echo "  fly secrets set DB_URL='jdbc:postgresql://...' DB_USERNAME=... DB_PASSWORD=..."
