# §9 Step 2 — Baseline

**Status: captured.** Database side first measured 2026-09-04 and re-captured on the
vacuumed database 2026-09-07 — the 4,044-buffer plan below is the 09-07 one; k6 load measured
2026-09-07. Nothing here is estimated or inferred.

Commit: Phase 3 (`ec5a8e9`), before any Phase 11 work.

> **The headline is not the one §9 predicted.** The endpoint is CPU-bound, as §9
> says — but **88% of that CPU is Postgres, not the Java scorer** (20.7 ms vs
> 2.9 ms per request). §9's framing, *"the bottleneck is recomputation, not
> I/O"*, is half right: right that it isn't disk (zero disk reads, every block a
> buffer hit), wrong about which CPU is busy. See *The reading* below — this
> changes what Phase 11 should expect from each step, though not their order.

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
| Shared buffers touched | **4,044** (~31.6 MB) — as captured; see the note below |
| Rows discarded by the `player_game_stats` scan | 92,919 of 112,319 |
| Rows discarded by the `players` scan | 16,689 of 25,065 |
| Rows returned to Java for scoring | **6,037** |

> **Which number Phase 11 compares against.** The 4,044 above is what this database
> actually did on 2026-09-07, and it is the honest capture. It also carried bloat that had
> never been reclaimed: a later `VACUUM (FULL, ANALYZE)` took the same plan to **2,640**
> buffers — `player_game_stats` 2,774 → 2,160 and `players` 1,246 → 449 — without any
> schema change. **Phase 11 measures against 2,640, on a vacuumed database**, because a
> comparison that credits the perf pass for reclaiming dead tuples is not a measurement of
> the perf pass. Vacuum first, then compare. Detail in *Since capture: what V4 cost* below.

**Three sequential scans, not one.** The no-index invariant is written about
`player_game_stats`, but `players.position` is equally unindexed and accounts for
1,246 of the 4,044 buffers — nearly a third. Worth remembering in Phase 11 Step 5,
where the temptation is to index only the big table.

Other query shapes, same conditions:

| Query | Time | Buffers | Plan |
|---|---|---|---|
| Rankings, one season, four positions | 29.5 ms | 4,044 | three seq scans |
| Weekly leaders (`season=2025, week=5`) | 20.8 ms | 4,021 | seq scan, `week` gives no help |
| Player game log by id | 0.7 ms | 245 | Bitmap Index Scan on the PK — already fast |

The game log is the control: it hits the primary key's leading column, so it is
already fast and Phase 11 should not move it. If a "performance improvement" later
shows a large win here, something else changed.

| Table | Size |
|---|---|
| `player_game_stats` | 27 MB |
| `players` | 13 MB |
| `games` | 464 kB |
| `teams` | 40 kB |

### Since capture: what V4 cost

`V4__games_betting_and_results.sql` widened `games` by ten columns on 2026-09-08.
Measured rather than assumed, by materialising the pre-V4 and post-V4 column sets
from the same 1,965 rows and vacuuming both:

| `games` heap | Pages | Size |
|---|---|---|
| pre-V4, 8 columns | 19 | 152 kB |
| post-V4, 18 columns | 30 | 240 kB |

**+11 pages, 88 kB.** The rankings plan's `games` seq scan reads exactly those 30
buffers, so the widening costs 11 buffers against a post-vacuum plan total of
2,640 — 0.4%. The shape is unchanged: still three sequential scans, still zero
disk reads.

Two things make a naive re-run of `scripts/perf-explain.sh` look alarming, and
neither is V4:

1. **A backfill leaves dead tuples.** Every row it touches is rewritten. Straight
   after this one, `player_game_stats` read **4,346** buffers against the 2,774
   captured above — on a table V4 does not touch.
2. **The captured baseline was itself carrying bloat.** `VACUUM (FULL, ANALYZE)`
   took `player_game_stats` to **2,160** buffers and `players` from 1,246 to
   **449** — below the numbers at the top of this file. That is space this project
   had never reclaimed, not a Phase 4 effect.

So vacuum before comparing, and compare the `games` scan specifically. Anything
else moves for reasons that have nothing to do with this migration.

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

## k6 load — measured

k6 v2.2.0, 2026-09-07. Four passes at 1 / 5 / 10 / 20 VUs — §9 asks for 1 and
20, but two points cannot distinguish linear degradation from flattening, which
is the entire distinction the argument rests on.

### Method

```bash
cd backend && ./mvnw -B package -DskipTests
java -jar target/backend-0.0.1-SNAPSHOT.jar          # one clean JVM to sample

k6 run --vus 5 --duration 30s perf/rankings.js       # WARMUP, discarded
for v in 1 5 10 20; do k6 run --vus $v --duration 60s perf/rankings.js; done
```

Three things make the numbers comparable, and Phase 11's re-runs must repeat
them:

- **A discarded 30s warmup pass.** Without it the 1-VU p95 carries cold-JIT
  compilation and the curve starts somewhere that isn't the steady state.
- **One JVM for all four passes, no restart between them.** Restarting would
  re-confound warmup with concurrency, and isolating concurrency is the point.
- **`perf/rankings.js` run unchanged**, so the Phase 11 comparison is valid.

**CPU is measured from cumulative CPU time, not `ps %cpu`.** macOS reports
`%cpu` as a decayed average since process start, which understates a 60-second
burst. Δ`cputime` ÷ Δwall-clock is exact. It is an **average over the run** —
"peak" is a number `ps` cannot honestly give here. Ceiling is 800% (M2, 8 cores).

### Results

| Metric | 1 VU | 5 VUs | 10 VUs | 20 VUs |
|---|---|---|---|---|
| p50 | 12.38 ms | 18.11 ms | 34.71 ms | **71.92 ms** |
| p95 | **21.41 ms** | 33.03 ms | 69.18 ms | **124.99 ms** |
| p99 | 24.43 ms | 43.78 ms | 86.62 ms | 152.56 ms |
| max | 52.41 ms | 87.87 ms | 131.31 ms | 225.55 ms |
| Throughput | 72.9 req/s | 246.4 req/s | **259.0 req/s** | **256.4 req/s** |
| Failed checks | 0 / 8,754 | 0 / 29,578 | 0 / 31,092 | 0 / 30,798 |
| JVM CPU (avg, of 800%) | 16% | 59% | 74% | **73%** |

100% of checks passed at every level, including `ranking is not empty` — so no
part of this measured the latency of an empty result set.

### The reading

**Throughput saturates at ~256 req/s from 10 VUs on, and past that point latency
grows exactly linearly with concurrency** — 10→20 VUs doubles the median
(34.71 → 71.92 ms) while throughput moves 259 → 256. That is textbook queueing
at a resource already at capacity, and it settles the compute-vs-scan question
in the direction §9 did not expect.

**Which resource, measured directly under 20 VUs:**

| | CPU | per request | share |
|---|---|---|---|
| **Postgres** | 486–587% (~5.3 cores) | **20.7 ms** | **88%** |
| JVM (the scorer) | 73% (0.73 cores) | 2.9 ms | 12% |
| k6 itself | ~30% (0.3 cores) | — | — |
| **Machine total** | **~6.3 of 8 cores** | | |

The 1.7 cores of headroom matter: the laptop is *not* saturated, so these are
the endpoint's numbers rather than the load generator's. The JVM flatlines at
0.73 cores — **less than one core out of eight** — from 10 VUs onward, and no
amount of added concurrency moves it.

**So §9's premise is half right.** It is right that this is not I/O: `EXPLAIN`
shows 4,044 shared buffer *hits* and zero disk reads. It is wrong about which
CPU is busy. The cost is Postgres executing three sequential scans — discarding
92,919 of 112,319 `player_game_stats` rows and 16,689 of 25,065 `players` rows —
**256 times a second**. The Java dot-product over the surviving 6,037 rows is
about a seventh of that.

**A secondary ceiling, worth naming so Phase 11 doesn't mistake it for a fix.**
`application.yml` sets no Hikari config, so the pool is Spring Boot's default of
**10 connections**; `pg_stat_activity` showed 5–9 active backends during the run.
Ten connections at ~39 ms of occupancy each is ~256 req/s, which is precisely
where throughput lands. Raising the pool without making the query cheaper would
buy a little throughput and spend it on latency, because Postgres CPU is the
real constraint — it would move the queue, not remove it.

### What this changes for Phase 11

The **order stays**: cache → matview → indexes. A cache hit skips the scan *and*
the scoring, so it remains the largest single win, and the argument for hashing
the ruleset rather than the profile id is untouched.

The **expected magnitudes flip.** §9 treats the matview and the index as
supporting evidence for a story about recomputation. On this measurement they
attack the dominant cost directly, so they should be worth **more** than §9
predicts, not less — and the `player_season_agg` matview looks like the strongest
fix for the cache-miss path, because it removes the scan rather than just
shrinking what Java receives.

**§12 Q4's scripted answer is now wrong and needs rewriting.** *"The p95 curve
proves the bottleneck was recomputation"* does not follow from these numbers.
What the curve proves is that the endpoint is compute-bound rather than
disk-bound; what identifies *which* compute is the 88/12 CPU split, which takes a
second measurement the question's stock answer never mentions. The better answer
is that one measurement narrowed it and a second one located it — and that the
second one contradicted the hypothesis.

`perf/rankings.js` varies profile, position and scope across the four seeded
presets rather than hammering one URL. Hitting a single ruleset would hand Phase
11's cache a 100% hit rate on one key and flatter the delta; four presets over
five position filters and three scopes is 60 distinct cache keys, which is both a
more realistic load and still small enough to demonstrate §9's actual claim —
that hashing the ruleset rather than the profile id collapses every user with
identical league settings onto one entry.

---

## Next

Phase 11 in §9's order — cache, matview, indexes — re-running
`perf/rankings.js` unchanged after each step, with the same warmup and the same
CPU method, and recording the deltas in `docs/perf/results.md`. Sample Postgres
CPU alongside the JVM's at every step: on this baseline it is the number that
actually moves, and a results file that tracks only the JVM would miss the win.

**One correction to carry into Phase 11.** §9 Step 4 says the `player_season_agg`
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
custom profiles. Phase 11 must either restrict the matview path to rulesets where
`bonuses().isEmpty()` or materialize per-game bonus counts alongside the sums.
`RankingsTests.paysAThresholdBonusOncePerQualifyingGameNotOncePerSeason` pins the
behaviour the matview would have to preserve.
