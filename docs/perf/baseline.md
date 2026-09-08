# §9 Step 2 — Baseline

**Status: partially captured.** The database-side measurements below are real and
reproducible. The k6 load numbers are **not yet taken** — every cell marked `TBD`
is waiting on a run, and nothing in this file is estimated or inferred. §9 exists
because "you cannot claim an improvement you didn't measure"; a placeholder that
looks like a number would defeat the whole point of the file.

Captured against the loaded development database, 2026-09-04.
Commit: Phase 3, before any Phase 6 work.

---

## What is being measured

`GET /api/v1/rankings` as Phase 3 ships it — §9 Step 1's "slow and naive, on
purpose":

- **No cache.** No Redis client is even on the classpath yet.
- **No index past the primary keys.** `player_game_stats` carries exactly
  `pk_player_game_stats (player_id, game_id)`, verified below and enforced by
  `ScoringProfileTests.theRankingsTableStillCarriesNothingButItsPrimaryKey`.
- **Scoring recomputed per request**, in Java, for every player-week, under the
  caller's ruleset — then sorted, then paginated.

A request for `season=2025` with no position filter reads **6,037 player-week
rows** and scores every one of them to produce a ranking of **610 players**.
`size` does not reduce that work: points do not exist in SQL, so the page cannot
be taken until after the scoring and the sort.

---

## Environment

| | |
|---|---|
| Host | Apple M2, macOS 15 (Darwin 25.5.0) |
| JDK | Temurin 21.0.11 |
| Spring Boot | 3.5.16, embedded Tomcat, default thread pool |
| Postgres | 16 (alpine) in Docker, host port 5433 |
| Dataset | 112,319 stat rows, 25,065 players, 1,965 games (2020–2025 + 2026 schedule) |
| Season under test | **2025** — pinned, see below |

**2025 is pinned deliberately.** The API defaults `season` to the current NFL
season, and 2026 has its schedule loaded but no stat lines published yet. A load
test against the default would score zero rows and report an excellent,
meaningless p95. `perf/rankings.js` pins the season and asserts the ranking is
non-empty on every iteration so this cannot happen silently.

---

## Database side — measured

`EXPLAIN (ANALYZE, BUFFERS)` on the exact query
`PlayerQueryRepository.findScorableRows` issues. Reproduce with
`./scripts/perf-explain.sh 2025`.

```
Hash Left Join  (actual time=22.289..29.109 rows=6037 loops=1)
  Buffers: shared hit=4044
  ->  Hash Join  (actual time=21.697..26.960 rows=6321 loops=1)
        ->  Seq Scan on player_game_stats s  (actual time=12.746..15.546 rows=19400 loops=1)
              Filter: (season = 2025)
              Rows Removed by Filter: 92919
              Buffers: shared hit=2774
        ->  Seq Scan on players p  (actual time=0.007..7.568 rows=8376 loops=1)
              Filter: (("position")::text = ANY ('{QB,RB,WR,TE}'::text[]))
              Rows Removed by Filter: 16689
              Buffers: shared hit=1246
  ->  Seq Scan on games g  (actual time=0.005..0.345 rows=1887 loops=1)
              Filter: ((season_type)::text = 'REG'::text)
              Buffers: shared hit=23
Planning Time: 3.632 ms
Execution Time: 29.538 ms
```

| | |
|---|---|
| Execution time (warm, single connection) | **29.5 ms** |
| Planning time | 3.6 ms |
| Shared buffers touched | **4,044** (~31.6 MB) |
| Rows discarded by the `player_game_stats` scan | 92,919 of 112,319 |
| Rows discarded by the `players` scan | 16,689 of 25,065 |
| Rows returned to Java for scoring | **6,037** |

**Three sequential scans, not one.** The no-index invariant is written about
`player_game_stats`, but `players.position` is equally unindexed and accounts for
1,246 of the 4,044 buffers — nearly a third. Worth remembering in Phase 6 Step 5,
where the temptation is to index only the big table.

Other query shapes, same conditions:

| Query | Time | Buffers | Plan |
|---|---|---|---|
| Rankings, one season, four positions | 29.5 ms | 4,044 | three seq scans |
| Weekly leaders (`season=2025, week=5`) | 20.8 ms | 4,021 | seq scan, `week` gives no help |
| Player game log by id | 0.7 ms | 245 | Bitmap Index Scan on the PK — already fast |

The game log is the control: it hits the primary key's leading column, so it is
already fast and Phase 6 should not move it. If a "performance improvement" later
shows a large win here, something else changed.

| Table | Size |
|---|---|
| `player_game_stats` | 27 MB |
| `players` | 13 MB |
| `games` | 464 kB |
| `teams` | 40 kB |

### Invariant, verified at capture time

```
=== exactly one index on player_game_stats ===
      indexname
----------------------
 pk_player_game_stats
(1 row)
```

---

## HTTP side — single request, warm

Not a load test. Ten sequential requests for the heaviest ranking
(`profileId=3&season=2025&scope=season&size=50`, no position filter), taken to
sanity-check the endpoint before loading it:

| | |
|---|---|
| min | 28 ms |
| median | **38 ms** |
| max | 83 ms |

So roughly 30 ms of Postgres and a few milliseconds of Java at one request at a
time. **The interesting question is what that ratio does under concurrency**, and
that is what the k6 runs below are for — the scan is shared through the buffer
cache, the scoring is not.

---

## k6 load — TBD

Not yet run. k6 is not installed on the capture machine.

```bash
brew install k6

# terminal 1
cd backend && ./mvnw spring-boot:run

# terminal 2 — both passes, same script
k6 run --vus 1  --duration 60s perf/rankings.js
k6 run --vus 20 --duration 60s perf/rankings.js
```

| Metric | 1 VU | 20 VUs |
|---|---|---|
| p50 | TBD | TBD |
| p95 | TBD | TBD |
| p99 | TBD | TBD |
| Throughput (req/s) | TBD | TBD |
| Failed checks | TBD | TBD |
| Peak CPU during run | TBD | TBD |

**The pair is the measurement, not either number.** §9 and §12 Q4 both turn on
it: a scan-bound endpoint degrades gently from 1 to 20 VUs because the rows are
already in shared buffers, while a compute-bound one degrades close to linearly
because every virtual user redoes the same scoring from scratch. Whichever curve
appears is the honest answer to "how did you know the bottleneck was
recomputation and not the query?" — and if the p95 barely moves, that finding
goes in this file too and the Phase 6 ordering gets revisited.

`perf/rankings.js` varies profile, position and scope across the four seeded
presets rather than hammering one URL. Hitting a single ruleset would hand Phase
6's cache a 100% hit rate on one key and flatter the delta; four presets over
five position filters and three scopes is 60 distinct cache keys, which is both a
more realistic load and still small enough to demonstrate §9's actual claim —
that hashing the ruleset rather than the profile id collapses every user with
identical league settings onto one entry.

---

## Next

Fill in the table above from a real run, then Phase 6 in §9's order — cache,
matview, indexes — re-running this same script unchanged after each step and
recording the deltas in `docs/perf/results.md`.

**One correction to carry into Phase 6.** §9 Step 4 says the `player_season_agg`
matview collapses "~19K player-game rows per season into ~600 player-season rows
… a ~30× reduction". Those two numbers come from different populations: 19,400 is
*all* positions, while 613 is *skill* players. The rankings query filters to
QB/RB/WR/TE, so it reads 6,037 rows, and the real reduction is **~10×**. Still
worth doing, still mostly a compute win — but quote 10×, because that is the one
that survives someone running the count.

**And one correctness gap.** Step 4's matview pre-aggregates season totals and
would score them once, which pays a threshold bonus at most once per season
instead of once per qualifying game. All four seeded presets are bonus-free so
nothing is wrong today, but `Bonus` is part of the ruleset model and Phase 5 ships
custom profiles. Phase 6 must either restrict the matview path to rulesets where
`bonuses().isEmpty()` or materialize per-game bonus counts alongside the sums.
`RankingsTests.paysAThresholdBonusOncePerQualifyingGameNotOncePerSeason` pins the
behaviour the matview would have to preserve.
