#!/usr/bin/env bash
# Captures the EXPLAIN (ANALYZE, BUFFERS) plan behind GET /api/v1/rankings.
#
# §9 Step 2 asks for the plan alongside the k6 numbers, and Step 5 asks you to
# defend the composite index's column order against the before/after plans. This
# is the "before". Run it from the repo root; output goes to stdout, so redirect
# it into docs/perf/ when you want to keep a copy.
set -euo pipefail

SEASON="${1:-2025}"

exec docker compose exec -T postgres psql -U fantasykai -d fantasykai <<SQL
\echo '=== rankings query, season ${SEASON}, all four scorable positions ==='
\echo '=== this is PlayerQueryRepository.findScorableRows verbatim ==='
EXPLAIN (ANALYZE, BUFFERS)
SELECT s.player_id, p.full_name, p.position, t.abbr AS team, s.week,
       s.pass_yd, s.pass_td, s.pass_int, s.pass_2pt,
       s.rush_yd, s.rush_td, s.rush_2pt,
       s.rec, s.rec_yd, s.rec_td, s.rec_2pt,
       s.fum_lost, s.ret_td
FROM player_game_stats s
JOIN players p ON p.id = s.player_id
JOIN games g ON g.id = s.game_id
LEFT JOIN teams t ON t.id = p.team_id
WHERE s.season = ${SEASON} AND g.season_type = 'REG'
  AND p.position IN ('QB', 'RB', 'WR', 'TE');

\echo ''
\echo '=== the invariant: exactly one index on player_game_stats ==='
SELECT indexname FROM pg_indexes WHERE tablename = 'player_game_stats';

\echo ''
\echo '=== table sizes ==='
SELECT relname, pg_size_pretty(pg_total_relation_size(relid)) AS total
FROM pg_catalog.pg_statio_user_tables
WHERE relname IN ('player_game_stats', 'players', 'games', 'teams')
ORDER BY pg_total_relation_size(relid) DESC;
SQL
