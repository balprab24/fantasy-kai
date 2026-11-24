# `TargetWeekResolver`'s query, measured (2026-11-18)

**What this is:** the measurement behind `CurrentTargetWeek`'s cache (Slice 5, step 5.2, decision D1(a)).
**What it is not:** a production measurement, or a benchmark. `EXPLAIN (ANALYZE, BUFFERS)` runs on the laptop's Docker
Postgres 16 mirror, seven in all.

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

## Result

Seven runs of the query above, in order (the first is the output reproduced below):

| Run | Execution time | Buffers | Note |
|---:|---:|---|---|
| 1 | 195.6 ms | hit 108,259, **read 11** | the first; not fully warm (11 pages came from disk) |
| 2 | 142.7 ms | hit 108,270 | |
| 3 | 107.8 ms | hit 108,270 | |
| 4 | 107.0 ms | hit 108,270 | |
| 5 | 102.3 ms | hit 108,270 | |
| 6 | 104.3 ms | hit 108,270 | |
| 7 | 102.1 ms | hit 108,270 | |

**Warm, the call takes about 105 ms** (102 to 108 over five runs), which is about 0.83 to 0.88 ms per candidate game.
The 196 ms I first quoted is run 1. No `JIT:` block appeared in run 1's output (the full output of the other runs was not kept, only their timing and buffer lines). Run 1, verbatim:

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
  not get an index on `game_id` before Phase 11 (architecture section 9), which is why the answer is cached instead.
- **What the number depends on.** The candidate games, so the games left in the season: about 272 in week 1 (2.2 times
  as many; about 230 ms at the warm per-game cost of 0.85 ms, an extrapolation and not a measurement) and about 16 in
  week 18. Per-node timing under `EXPLAIN ANALYZE` also inflates a nested loop, so the real call may be somewhat faster.
- **What was not separated.** The first run's extra ~90 ms came with 11 disk reads; whether that is cold pages alone I
  did not establish. The doc measured `now()` where the code binds an instant, so a generic plan after the driver's
  prepare threshold could differ.
- **What it does not show.** Production's number. Plan section 9 made D1(b) (a `projection_run` table) the fallback
  "if it proves slow on production-sized data". Production's stat table holds the same seasons, but this has not been
  run there; the R3 rehearsal on a restored production copy (plan section 7) is where it gets measured on real data.

## Decision

Per request the resolver would add about 100 ms (warm) to both projection endpoints. `CurrentTargetWeek` calls it at most
once per `fantasykai.projections.target-cache-seconds` (60 in `application.yml`) per process. No migration, no index,
no change to the job.
