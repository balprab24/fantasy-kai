# `TargetWeekResolver`'s query, measured (2026-11-18)

**What this is:** the one measurement behind `CurrentTargetWeek`'s cache (Slice 5, step 5.2, decision D1(a)).
**What it is not:** a production measurement, or a benchmark. One `EXPLAIN (ANALYZE, BUFFERS)` run, warm cache,
on the laptop's Docker Postgres 16 mirror.

## Setup

- Local dev database (`fantasykai-postgres`), ingested through 2026 week 10 on 2026-11-17: 122,755 rows in
  `player_game_stats`, 1,965 games, season 2026 schedule loaded (272 games, 123 of them still candidates).
- The resolver's `NEXT_WEEK` query, with `now()` standing in for the bound instant:

```sql
EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)
SELECT min(g.week) FROM games g
 WHERE g.season = 2026 AND g.season_type = 'REG' AND g.delisted_at IS NULL
   AND NOT EXISTS (SELECT 1 FROM player_game_stats s WHERE s.game_id = g.id)
   AND (g.kickoff_at IS NULL OR g.kickoff_at > now() - interval '48 hours');
```

## Result, verbatim

```
Aggregate (actual time=195.531..195.532 rows=1 loops=1)
  Buffers: shared hit=108259 read=11
  ->  Nested Loop Anti Join (actual time=96.869..195.516 rows=122 loops=1)
        Buffers: shared hit=108259 read=11
        ->  Bitmap Heap Scan on games g (actual time=0.041..0.220 rows=123 loops=1)
              Recheck Cond: (season = 2026)
              Filter: ((delisted_at IS NULL) AND ((season_type)::text = 'REG'::text) AND ((kickoff_at IS NULL) OR (kickoff_at > (now() - '48:00:00'::interval))))
              Rows Removed by Filter: 149
              Heap Blocks: exact=21
              Buffers: shared hit=28
              ->  Bitmap Index Scan on uq_games_matchup (actual time=0.026..0.026 rows=272 loops=1)
                    Index Cond: (season = 2026)
                    Buffers: shared hit=7
        ->  Index Only Scan using pk_player_game_stats on player_game_stats s (actual time=1.587..1.587 rows=0 loops=123)
              Index Cond: (game_id = g.id)
              Heap Fetches: 1
              Buffers: shared hit=108231 read=11
Planning Time: 1.707 ms
Execution Time: 195.592 ms
```

## Reading it

- **Why it is slow.** `player_game_stats`' primary key is `(player_id, game_id)`, so a lookup by `game_id` alone cannot
  seek: each of the 123 candidate games walks the key's index (about 880 buffers each, 108,231 in all). The table must
  not get an index on `game_id` (architecture section 9), which is why the answer is cached instead.
- **What the number depends on.** The candidate games, so the games left in the season: about 272 in week 1 (2.2 times
  as many; ~430 ms by the same per-game cost, an extrapolation and not a measurement) and about 16 in week 18.
  Per-node timing under `EXPLAIN ANALYZE` also inflates a nested loop, so the real call may be somewhat faster.
- **What it does not show.** Production's number. Plan section 9 made D1(b) (a `projection_run` table) the fallback
  "if it proves slow on production-sized data". Production's stat table holds the same seasons, but this has not been
  run there; the R3 rehearsal on a restored production copy (plan section 7) is where it gets measured on real data.

## Decision

Per request the resolver would add about 200 ms to both projection endpoints. `CurrentTargetWeek` calls it at most
once per `fantasykai.projections.target-cache-seconds` (60 in `application.yml`) per process. No migration, no index,
no change to the job.
