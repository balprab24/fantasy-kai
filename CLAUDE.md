# fantasy-kai — project brain

NFL fantasy analytics platform. Java 21 / Spring Boot 3.5.16 · PostgreSQL 16 · Redis 7 · Next.js 15 (Phase 5).

**Two docs sit above this file, and they own different things.**

- **[`docs/north-star.md`](docs/north-star.md)** — source of truth for **scope, sequencing and product decisions**. What we are building, for whom, in what order, and the list of things we are deliberately not building.
- **[`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md)** — source of truth for **engineering rationale**: §5 schema, §6 scoring, §8 security, §9 performance. Its §1 (product definition) and §11 (build plan) are **superseded** by the north star.

This file is the operational memory that sits alongside both. When it disagrees with either, the doc wins — and fix this file.

**[`docs/map.md`](docs/map.md) is the front door** — the status board, every package's classes, the two pipelines, and where to look for anything. It owns no facts; it links to whichever of these three does.

## The one idea

**Store raw stat lines, never fantasy points. Compute points on demand against a ruleset.**

Full PPR, half PPR, standard and TE premium stop being three code paths and become three rows in a table. Everything else in the design serves this. If a change would persist a computed point value, it is the wrong change.

## Invariants — do not break these

| Invariant | Why | Expires |
|---|---|---|
| `player_game_stats` carries **no index past its primary key** | §9's whole performance story is a measured before/after. An index added early destroys the baseline and there is no way to recover it without re-measuring from scratch. | Phase 11, as a numbered migration |
| Never store `fantasy_points` / `fantasy_points_ppr` | The source ships both. A stored point value is correct for exactly one ruleset. Persisting them reintroduces the thing the architecture exists to avoid. | never |
| Flyway owns the schema; `ddl-auto` stays `validate` | Versioned schema from commit 1. `validate` fails fast the moment a JPA entity drifts from a migration. It must never become `update`. | never |
| `.env` is gitignored, `.env.example` is committed | No secret in `application.yml`. JWT secret comes from env (Phase 5). | never |
| No string concatenation into SQL — including dynamic sort/filter | Whitelist sortable columns by name. §8. | never |
| `StatKey` is the only place a scorable stat is named | It is the validation allowlist, the dot-product array index, and the `player_game_stats` column name at once. Adding a stat anywhere else breaks one of the three. | never |
| Round points once, at the API boundary | `ScoringEngine` never rounds. A season total must be the rounded sum of weeks, not the sum of rounded weeks. | never |
| A ranking scores **each game, then sums** — never one score over summed stats | Threshold bonuses make `ScoringEngine` non-linear, so a 100-yard bonus belongs to a game. It also keeps §9 Step 4's matview meaningful: if Phase 3 pre-aggregated, the perf pass would have nothing left to collapse. | never |
| Ingest every position, filter at query time | v1 scores QB/RB/WR/TE only, but storing only those rows would cut `player_game_stats` from 112K to 37K and gut the §9 baseline — and K/DST in v2 would then need a backfill after all. | never |
| **Project the stat line, never the points** | A projection row carries the same 13 `StatKey` columns and is scored by the unmodified `ScoringEngine`. A projected point value is right for exactly one ruleset — the `fantasy_points` mistake, one layer up. north-star §3.1 | never |
| **Store signal components, never a blended rank** | A rank is a function of (signals × recipe × league). Storing it freezes the recipe and turns a weight change into a backfill. north-star §3.2 | never |
| `SignalKey` is the only place a blendable signal is named | Same three-jobs trick as `StatKey`, one level up: recipe allowlist, dot-product index, signals-table column name. north-star §3.3 | never |
| **Every ranked number carries its parts** — `ExplainedScore`, not `double` | "Why is he ranked here" *is* the product. Explanation bolted on afterwards ends up absent or wrong. | never |
| Every external signal is optional; a ranking computes without it | $0 budget, unlicensed sources, and ESPN will break mid-season. Degrade the explanation, never 500 the endpoint. | never |
| A trade is valued in **expected wins**, never a per-player number | A context-free player value is exactly what every basic calculator gets wrong. north-star §7 | never |
| Third-party league data is fetched **as the user, with their own credentials**, never redistributed or aggregated | The line that separates legitimate league import from scraping. It is what makes ESPN import defensible. | never |
| **No expert rankings ingested, ever** — consensus means *market* consensus | Real drafts (FFC), real roster rates (Sleeper), real lines (nflverse). Handoff §0. | never |
| No paywall, no ads, no sportsbook links, no affiliate | The product promise — and it keeps Apple guideline 5.3 out of scope entirely. | never |

## Where things are

```
backend/src/main/java/com/fantasykai/
  ingest/          Phase 1 — nflverse + Sleeper pipeline (18 classes)
  scoring/         Phase 2 — ruleset model, validator, dot-product evaluator
  query/           Phase 3 — JdbcTemplate reads, StatKey-generated SQL, the §8 whitelists
  api/             Phase 3 — controllers, DTOs, RFC 7807 advice
backend/src/main/resources/db/migration/   Flyway. V1 schema, V2 ingestion support,
                                           V3 presets, V4 Vegas columns
backend/src/test/resources/nflverse/       Real 2024 rows as fixtures — not invented
docs/map.md                                Front door — status board, class map, pipelines
docs/north-star.md                         Scope, roadmap, product invariants
docs/fantasy-platform-handoff.md           Engineering rationale (§1/§11 superseded)
docs/perf/                                 baseline.md only so far; projection-accuracy.md (Phase 6)
                                           and results.md (Phase 11) are owed
perf/rankings.js                           k6 load script — pins season=2025 on purpose
scripts/                                   One-shot ingest, launchd plist, perf-explain.sh
```

## Current state

| Phase | Status |
|---|---|
| 0 — Foundation | ✅ `921e21a`, `8fdad10`, `d06f133` |
| 1 — Ingestion | ✅ `6c591e5` — six-season backfill in 22.8s |
| 2 — Scoring engine | ✅ `com.fantasykai.scoring` + V3 presets, 47 tests |
| 3 — Read API | ✅ `ec5a8e9` — `com.fantasykai.api` + `.query`, 25 tests (72 in the suite) |
| 3.5 — k6 baseline | ✅ 1/5/10/20 VUs measured — p95 **21.4 ms → 125.0 ms**, throughput saturates at ~256 req/s. **The bottleneck is Postgres, not the Java scorer (88/12).** See below. |
| 4 — Vegas in the schema | ✅ `V4` widens `games` by 10 columns; `GameIngestor` reads 18 of the source's 46 and generates its upsert from one ordered list. 8 new tests (80 in the suite) |
| 5 — Auth + web shell | ⬅ **next** · 6 — Projections · 7 — League import (ESPN + Sleeper) · 8 — Roster tools |
| 9–11 | consensus board · iOS (Expo) · perf pass |

Full roadmap and the reasoning for the order: [`docs/north-star.md`](docs/north-star.md) §10.

2026 season opens **Sept 10**. The 2026 schedule is loaded (272 games); nflverse has not published 2026 stat lines yet, so those runs correctly record `SKIPPED`.

## Measured numbers — do not re-derive or estimate these

From the loaded database, 2020–2025:

| | |
|---|---|
| `player_game_stats` rows stored | **112,319** (112,450 read; 131 dropped for blank `player_id`) |
| Stat rows, QB/RB/WR/TE | 36,567 |
| Distinct players, all positions | 4,061 |
| Distinct players, QB/RB/WR/TE | **1,243** across six seasons — **578–633 in any one season** |
| `snap_pct` coverage | 99.9% (112,245 / 112,319) |
| Sleeper ids attached | 883 of 25,065 players |
| Rows with fractional `def_sacks` | 1,660 |
| Backfill wall time | 22.8s |

**nflverse `games.csv`, probed 2026-09-08** (7,548 rows, seasons 1999–2026). These are what V4's
column types are justified by — do not re-derive them either:

| | |
|---|---|
| Spreads / totals carrying a **half point** | **3,321** of 7,388 · 3,681 of 7,388 — never more than 1 dp |
| Most extreme moneyline in 27 seasons | **−5,000** — `SMALLINT` would hold it 6.5× over |
| `temp` range · rows with no reading | −6 … 109 · 2,342 |
| `wind` = 0 (calm, *not* missing), 2020–2026 | **29** |
| Distinct `roof` / `surface` values (longest) | 4 (`outdoors`, 8) / 8 (`matrixturf`, 10) |
| 2026 games with a line | **112 of 272** — they land ~a week ahead of kickoff |
| Cost of V4 to `games` | 19 → 30 heap pages, +88 kB; +11 buffers of ~2,640 in the rankings plan |

**"1,243 skill players" is a six-season union.** No single season clears 700. Say "across six seasons" or the claim breaks the moment someone asks whether it is one year.

**k6 baseline, 2026-09-07** (`docs/perf/baseline.md`, four 60s passes, one warm JVM):

| | 1 VU | 5 VUs | 10 VUs | 20 VUs |
|---|---|---|---|---|
| p50 | 12.38 ms | 18.11 ms | 34.71 ms | 71.92 ms |
| p95 | **21.41 ms** | 33.03 ms | 69.18 ms | **124.99 ms** |
| Throughput | 72.9 req/s | 246.4 req/s | 259.0 req/s | 256.4 req/s |
| JVM CPU (of 800%) | 16% | 59% | 74% | 73% |

**The measurement contradicts handoff §9 and you need to know this before quoting it.**
§9 asserts the bottleneck is Java recomputation. Measured at 20 VUs: **Postgres
486–587% CPU (~5.3 cores, 20.7 ms/req) against the JVM's 73% (0.73 cores,
2.9 ms/req) — an 88/12 split.** §9 is right that it isn't disk (4,044 buffer
hits, zero reads) and wrong about which CPU. Throughput saturates at ~256 req/s
from 10 VUs on while latency doubles 10→20 — queueing at a resource at capacity.
Phase 11's *order* survives (a cache hit skips both), but the matview and index
should be worth **more** than §9 predicts, and §12 Q4's stock answer is wrong as
written. Machine had 1.7 of 8 cores free, so this is the endpoint, not the laptop.

Secondary ceiling: Hikari is at Spring Boot's **default 10 connections** (no
config in `application.yml`); 10 × ~39 ms occupancy ≈ the 256 req/s observed.
Raising it without making the query cheaper moves the queue, it does not remove it.

**Daily ingest volume:** ~28,500 records before week 1, rising to ~74,000 by week 18. ~25K of that is the player master. Say the real number and its composition, not "10K+".

## Traps in the source data — each of these cost real time

- **131 stat rows have a blank `player_id`.** They cannot resolve to a player and are dropped. This is why read ≠ stored.
- **`def_sacks` is fractional.** A shared sack credits 0.5; 1,660 rows are non-integer. The column is `NUMERIC(4,1)`. `SMALLINT` would round 4.5 to 5 and silently corrupt the data.
- **Nullable FK columns inside a `UNIQUE` constraint do not constrain.** Postgres treats NULLs as distinct, so `uq_games_matchup` admitted exact duplicate games until `home_team_id`/`away_team_id` were made `NOT NULL`. Proven by inserting a duplicate, not by reasoning.
- **`snap_counts.offense_pct` is a fraction (0.94), not a percent.** The column is a percentage; multiply by 100.
- **The Rams abbreviate as `LA`, not `LAR`,** in some nflverse files.
- **Sleeper pads `gsis_id` with a leading space:** `" 00-0035057"`. Trim before matching.
- **nflverse `players.csv` has no Sleeper id at all** — only espn, pfr, nfl, esb. The crosswalk has to come from Sleeper's own API, matching on gsis.
- **pgjdbc maps `smallint` to `Integer`, not `Short`.** Test assertions must use ints.
- **`JdbcTemplate` reads a jsonb `?` operator as a bind placeholder.** `external_ids ? 'pfr'` will not work; use `external_ids ->> 'pfr' IS NOT NULL`.
- **nflverse's `fantasy_points` penalises only *offensive* fumbles.** We score `fumbles_lost_total`, which also counts a muffed punt or kickoff — 39 rows of 19,422 in 2025, each worth exactly 2 points. This is a deliberate disagreement (real leagues penalise any fumble the roster player loses), pinned exactly in `NflverseOracleTests` rather than hidden behind a tolerance. Do not "fix" it toward nflverse.
- **`players.full_name` is not unique.** 832 names are shared, 24 of them between players who both have stat lines — `Josh Allen` is a quarterback (id 344) and a center (id 343). Never key a lookup on a name; the API returns `id` everywhere and treats the name as display text.
- **Weeks run to 22, not 18.** Weeks 19–22 are `season_type = 'POST'`. Rankings join `games` and filter to `REG`, because fantasy leagues do not score the playoffs and `last4` would otherwise mean "the postseason". The game log deliberately does *not* filter — it is a record of what a player did.
- **`@Validated` on a controller turns a 400 into a 500.** It proxies the class so Bean Validation throws `ConstraintViolationException`, which no Spring MVC handler knows about. Without it, Spring 6.1+ validates constrained parameters itself and raises `HandlerMethodValidationException`, which `ResponseEntityExceptionHandler` renders as `problem+json`. Found by sending `?size=5000`, not by reading the docs.
- **A blank `temp` does not mean "dome".** 297 *outdoor* games in 2020–2026 have no temperature
  either, and one `closed`-roof game does have one. Blank means not recorded. `wind = 0` is likewise a
  real reading (29 rows), which is why both columns take `CsvValues.shortOrNull` and not `shortValue`
  — the latter defaults a missing value to 0, correct for a box score and wrong for a fact.
- **`games.result` and `games.total` are not stored, and that is deliberate.** Across all 7,276 played
  games, with zero exceptions, `total` = `home_score + away_score` and `result` = `home_score −
  away_score`. They are the `implied_team_total` mistake one layer down. Derive on read.
- **A backfill leaves dead tuples, and they wreck a perf comparison.** Straight after one,
  `player_game_stats` reads 4,346 buffers instead of 2,774 — on a table nothing changed. `VACUUM
  (FULL, ANALYZE)` before comparing against `docs/perf/baseline.md`.
- **Editing an applied migration breaks your local database while CI stays green.** Flyway
  checksums every applied file; changing one — even only its comments — fails startup with
  `Validate failed: Migrations have failed validation`. CI never catches it, because
  Testcontainers always starts from an empty database. Hit for real on V4. The fix is
  `flyway repair`, or recompute the CRC32 (over each line, terminators excluded) and
  `UPDATE flyway_schema_history SET checksum = ? WHERE version = ?` — verify the algorithm
  against an untouched migration first.
- **nflverse release assets 404 until published.** `AssetNotPublishedException` → `ingest_runs.status = 'SKIPPED'`. A future season must not fail the run.

## Scoring — how it fits together

```
scoring_profiles.rules (JSONB)
   -> RulesetJson.read      parse; reject unknown keys rather than ignoring them
   -> RulesetValidator      bound rates to [-10,10], bonuses to 20
   -> ResolvedRuleset       version dispatch, then position overrides folded into double[]
   -> ScoringEngine.score   one dot-product + threshold bonuses. no rounding.
```

`ResolvedRuleset.hash()` is the §9 cache key: logically identical rulesets hash identically, so two users with the same league settings share one entry. Verify a change to `Ruleset.canonicalHash()` against `RulesetHashTests` before trusting it.

Presets are seeded by `V3__seed_scoring_presets.sql`, duplicated in the test helper `Presets.java`, and the two are held together by a canonical-hash assertion in `ScoringProfileTests` — change one and that test names the other.

## Read API — how it fits together

```
GET /api/v1/rankings?profileId=&season=&position=&scope=&page=&size=
   -> ScoringProfiles.byId        compiled ruleset, memoized per profile id
   -> PlayerQueryRepository       one seq scan; 6,037 rows for a 2025 season
   -> ScoringEngine.score         per row, unrounded
   -> sum per player, sort, page  in Java — points do not exist in SQL
   -> roundForDisplay             once, here
```

`size` does not reduce the work: the sort is by computed points, so every row has
to be scored before a page can be taken. That is the §9 baseline, not a defect.

Three whitelists stand between a request and the SQL — `PlayerSort`,
`RankingScope`, `ScoringPosition`. Each resolves a request string to an enum
constant or throws; `QuerySafetyTests` proves it by sending `DROP TABLE` through
each one and then checking the table is still there.

`/api/v1/scoring-profiles` serves presets only (`user_id IS NULL`). Phase 5 adds
`OR user_id = ?` bound to the JWT subject, **in the query, not the service**.

## Commands

```bash
docker compose up -d                      # Postgres on :5433 (not 5432), Redis on :6379
cd backend && ./mvnw -B verify            # needs Docker — Testcontainers boots a real PG 16

# psql
docker compose exec postgres psql -U fantasykai -d fantasykai

# one-shot historical backfill
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true

# one-shot current-season pull (what launchd runs daily)
./scripts/ingest-once.sh
```

**Postgres is on 5433** because a Homebrew `postgresql@16` launchd service owns 5432 on the dev Mac and wins the connection. Symptom when this bites: `role "fantasykai" does not exist`.

## Working agreement

**Measure before asserting. Prove a constraint by trying to violate it.**

This project exists to be defended out loud in an interview, so any type, constraint or number that was picked by default becomes something its owner has to justify. Before a migration, pull the real values rather than reasoning about what the type should be — that habit has already caught the fractional sacks and the NULL-defeated unique constraint. Before quoting a number, run the query. Fixing a 200-line schema is free; fixing it under 112K rows is not.

Report design gaps you are *not* fixing explicitly rather than staying quiet about them.
