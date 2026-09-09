# The map

**Where everything is and what state it's in.** This file owns nothing. Every fact it
can't own, it links to — so when it disagrees with the doc that owns a fact, the doc wins
and this file gets fixed.

Last verified against the tree: **2026-09-08**, at the Phase 4 merge.

---

## 1. Status board

| | |
|---|---|
| Phases shipped | **0 → 4** |
| Currently next | **Phase 5** — auth + web shell |
| Backend | 54 files · 3,048 lines · Java 21 / Spring Boot 3.5.16 |
| Tests | 12 files · 1,849 lines · **80 tests**, all green · `./mvnw -B verify` ≈ 39s |
| HTTP endpoints | **5**, all `GET`, all unauthenticated (Phase 5 fixes that) |
| Migrations | `V1` … `V4` |
| Data loaded | 112,319 stat rows · 25,065 players · 1,965 games · 2020–2026 |
| Frontend | none yet — Phase 5c |

| # | Phase | State |
|---|---|---|
| 0 | Foundation — compose, Boot skeleton, Flyway `V1` | ✅ |
| 1 | Ingestion — nflverse + Sleeper, six-season backfill in ~19s | ✅ |
| 2 | Scoring engine — ruleset model, validator, dot-product evaluator | ✅ |
| 3 | Read API — `JdbcTemplate` reads, `StatKey`-generated SQL, query whitelists | ✅ |
| 3.5 | Close the baseline — k6 at 1/5/10/20 VUs; **the bottleneck is Postgres, not the scorer (88/12)** | ✅ |
| 4 | Vegas in the schema — `V4` widens `games` by 10 columns | ✅ |
| **5** | **Auth + web shell** — Argon2id, JWT, rotating refresh, Bucket4j, Next.js shell | ⬅ **next** |
| 6 | Projections — `SignalKey`, `ProjectionEngine`, `ExplainedScore`, published MAE | |
| 7 | League import — `LeagueProvider`, ESPN + Sleeper | |
| 8 | Roster tools — optimizer, simulator, trade evaluator, waivers | |
| 9 | Consensus board — FFC ADP + Sleeper rostered% → the logged-out top 100 | |
| 10 | iOS (Expo) | |
| 11 | Perf pass — cache → matview → indexes, measuring after each | |

Roadmap detail and the reasoning for the order: [`north-star.md` §10](north-star.md).
Phase status is also in [`../CLAUDE.md`](../CLAUDE.md) — if the two disagree, north-star wins.

---

## 2. The map

### `backend/src/main/java/com/fantasykai/`

`FantasyKaiApplication` — entry point. The only `@Bean` source outside `ingest`: supplies
the single `Clock`. No security config, no CORS config, no cache config yet.

#### `ingest/` — Phase 1 · 18 files

| Class | Does |
|---|---|
| `IngestService` | Orchestrator. Sequences the six sources, wraps each in an `ingest_runs` row |
| `NflverseClient` | One HTTP GET of a GitHub release asset + commons-csv parse |
| `TeamIngestor` | `teams_colors_logos.csv` → `teams`, including historical abbreviations |
| `PlayerIngestor` | `players.csv` → `players`; espn/pfr/nfl/esb ids as `jsonb` |
| `GameIngestor` | `schedules/games.csv` → `games`. 18 of the source's 46 columns; upsert generated from one ordered `List<Field>` |
| `StatIngestor` | `stats_player_week_{season}.csv` → `player_game_stats`. 47 stat columns from one `FIELDS` list |
| `SnapCountIngestor` | `snap_counts_{season}.csv` → `player_game_stats.snap_pct`, joined on pfr id |
| `SleeperCrosswalk` | Sleeper API → `players.external_ids.sleeper`, matched on gsis id |
| `CsvValues` | Null-safe typed accessors over a `CSVRecord`. **`shortValue` defaults to 0, `shortOrNull` doesn't** — the distinction matters |
| `IntegrityChecks` | Post-ingest invariant: `player_game_stats.season/week` vs the joined game |
| `IngestRunRecorder` | `start` / `succeed` / `skip` / `fail` writes to `ingest_runs` |
| `IngestProperties` | `@ConfigurationProperties` + the season-boundary maths |
| `IngestScheduler` | `@Scheduled` daily in-season pull |
| `BackfillRunner` | One-shot historical load, `backfill-on-startup=true` |
| `IngestOnceRunner` | One-shot current-season pull, `once=true`; exits 0 or 1 |
| `IngestResult` · `IngestException` · `AssetNotPublishedException` | Result record and the two exception types. The 404 → `SKIPPED` path lives here |

#### `scoring/` — Phase 2 · 11 files

| Class | Does |
|---|---|
| `StatKey` | **The 13 scorable stats.** Simultaneously the validation allowlist, the `double[]` index and the DB column name |
| `Ruleset` | Record: version, base rates, position overrides, bonuses. Owns `canonicalHash()` |
| `RulesetJson` | Reads/writes the `scoring_profiles.rules` JSONB. Rejects unknown keys rather than ignoring them |
| `RulesetValidator` | Bounds: rates ±10, bonus points ±20, ≤20 bonuses, threshold ≤1000 |
| `ResolvedRuleset` | Compiled form — a `double[]` per position. Version-dispatched `compile()` |
| `ScoringEngine` | Static and pure: one dot product + threshold bonuses. **Never rounds** except in `roundForDisplay` |
| `ScoringProfiles` | Loads a profile, validates, compiles, caches by id |
| `StatLine` · `Bonus` | Records: one player-week, one threshold bonus |
| `InvalidRulesetException` · `NoSuchProfileException` | Drive the 422-vs-404 split |

#### `query/` — Phase 3 · 10 files

| Class | Does |
|---|---|
| `PlayerQueryRepository` | Every read the API makes, as parameterized JDBC |
| `StatColumns` | Generates the `SELECT` list from `StatKey`; reads a `ResultSet` into `double[]` by name |
| `PlayerSort` · `RankingScope` · `ScoringPosition` | **The three whitelists.** Each resolves a request string to an enum constant or throws |
| `ScoringProfileQueryRepository` | Preset metadata only — never the `rules` column |
| `ScorableRow` · `PlayerRow` · `GamelogRow` | Row records |
| `InvalidQueryParameterException` | A name that isn't on a whitelist |

#### `api/` — Phase 3 · 14 files

| Class | Does |
|---|---|
| `RankingsController` · `PlayerController` · `ScoringProfileController` | The five endpoints |
| `RankingsService` | Scores every player-week in Java, aggregates per player, sorts, pages |
| `PlayerService` | Player list, detail, and the scored game log |
| `ApiExceptionHandler` | RFC 7807 `problem+json` over `ResponseEntityExceptionHandler` |
| `PageResponse` | Generic page envelope. `DEFAULT_SIZE=50`, `MAX_SIZE=200` |
| `RankingRow` · `PlayerSummary` · `PlayerDetail` · `GamelogWeek` · `GamelogResponse` · `ScoringProfileSummary` | Response records |
| `PlayerNotFoundException` | 404 |

### Everything else

```
backend/src/main/resources/
  application.yml                  datasource, flyway, ingest config
  db/migration/                    V1 schema · V2 ingestion support · V3 presets · V4 Vegas columns
backend/src/test/
  java/com/fantasykai/             10 test classes + ApiFixture, Presets
  resources/nflverse/              6 fixture files — real rows, never invented
docs/
  north-star.md                    scope, sequencing, product decisions
  fantasy-platform-handoff.md      engineering rationale (§1 and §11 superseded)
  perf/baseline.md                 the Phase 3.5 measurement
  map.md                           this file
perf/rankings.js                   k6 script — pins season=2025 on purpose
scripts/                           ingest-once.sh · launchd plist · perf-explain.sh
```

---

## 3. The two pipelines

**Ingest** — one pass, strictly sequential, deliberately not transactional. Each source
gets its own `ingest_runs` row, so "how many rows moved" is answerable per source.

```
teams ─→ players ─→ games ─→ ┌ stats(season) ─→ snaps(season) ┐ ─→ sleeper ─→ integrity check
                             └── once per season, 2020..now ──┘
```

The order is load-bearing: `snaps` needs `players.external_ids.pfr` *and*
`games.nflverse_game_id`; `stats` needs all three lookup tables. A 404 from an unpublished
asset records `SKIPPED` and carries on — a season that hasn't started is not an error.

**A request** — `GET /api/v1/rankings`:

```
controller          binds params, applies @Min/@Max
  ↓
whitelists          PlayerSort · RankingScope · ScoringPosition — resolve or throw
  ↓
ScoringProfiles     compiled ruleset, memoized per profile id
  ↓
repository          one seq scan; ~6,037 rows for a 2025 season
  ↓
ScoringEngine       per row, unrounded
  ↓
sum per player, sort, page   in Java — points do not exist in SQL
  ↓
roundForDisplay     once, here
```

`size` does not reduce the work: the sort is by computed points, so every row must be
scored before a page can be taken. That is the [§9 baseline](perf/baseline.md), not a defect.

---

## 4. Where do I look for X

| I want to… | Go to |
|---|---|
| Add a scorable stat | `StatKey` — then the migration, the ingest `FIELDS` list, and nothing else |
| Change how points are computed | `ScoringEngine` · bounds live in `RulesetValidator` |
| Add or change a scoring preset | `V3__seed_scoring_presets.sql` **and** the test-side `Presets.java` — a canonical-hash assertion holds them together |
| Add an endpoint | a controller in `api/`, a whitelist in `query/` for any new string param, and a handler in `ApiExceptionHandler` |
| Add a request filter or sort | a **new whitelist enum** in `query/`. Never concatenate into SQL |
| Change the schema | a new `V{n}` migration. Never edit an applied one. `ddl-auto` stays `validate` |
| Add an ingest source | a new `*Ingestor` + a line in `IngestService.ingest()`. Reuse `NflverseClient` and `CsvValues` |
| Understand a perf number | [`perf/baseline.md`](perf/baseline.md) · reproduce with `scripts/perf-explain.sh` and `perf/rankings.js` |
| Know why a type was chosen | the migration's own comment — every column carries its measured range |
| Run it | [`../README.md`](../README.md) for setup · [`../CLAUDE.md`](../CLAUDE.md) for the full command list |

---

## 5. Known gaps and open risks

Audited 2026-09-08. Each verified against the source, and tagged with the phase that
closes it. Nothing here is a surprise to the docs unless marked **new**.

### Closes with Phase 5

| Risk | Detail |
|---|---|
| **No auth at all** | Five endpoints world-readable. `/rankings` is a CPU amplifier whose cost is independent of `size`, so `MAX_SIZE` protects nothing. `actuator/health` runs `show-details: always` |
| **`ScoringProfiles.byId` has no ownership check** — **new** | `profileId` is a required param on `/rankings` and `/gamelog`. Harmless while only presets exist; an IDOR the day the first user profile is written. The check belongs in `byId`, and the Phase 5 brief doesn't name it |
| **`ScoringProfiles.evict(long)` has zero callers** | The profile cache is unbounded and never invalidated. A "edit my ruleset" endpoint will serve stale rules until restart |
| **`canonicalHash()` breaks its own invariant** — **new** | It emits only the keys *present* in the ruleset, but `compile` treats an absent key and an explicit `0` identically. So two logically identical rulesets can hash differently — the one thing the hash exists to prevent. Latent today (all four presets spell out all 13 keys), live the moment a user authors one |
| `roundForDisplay` uses binary float rounding | `Math.round(x*100)/100` disagrees with decimal rounding at e.g. `0.145` and `1.005`, and is asymmetric on negatives. Low reachability while every preset rate is ≤2dp; higher once custom rates land |

### Operational, and unscheduled

| Risk | Detail |
|---|---|
| **The daily ingest runs nowhere** — **new** | The launchd job is documented but not loaded, `logs/` is empty, and `ingest_runs` shows activity on two days only. The in-app `@Scheduled` path needs a continuously running app, and nothing runs one |
| **`ingest_runs` is written and never read** | No health indicator, no metric, no alert. A stopped pipeline is invisible. A freshness `HealthIndicator` is ~25 lines |
| **A renamed upstream column zeroes a stat and reports SUCCESS** | `CsvValues` returns null for a missing column and `shortValue` maps null → 0. Row counts still match; `IntegrityChecks` only compares season/week. A header assertion is cheap insurance |
| `NflverseClient` leaks the response body on the 404 path | Throws before the try-with-resources. That branch runs every off-season day |
| `StatIngestor` holds a whole season in memory | Identity mapper into a `List<CSVRecord>`, then a second full-size batch, under a JVM with no `-Xmx` |

### Housekeeping

- **`ddl-auto: validate` validates nothing** — there are zero `@Entity` classes, so
  CLAUDE.md's invariant is vacuous as written. JPA is on the classpath only as a carrier
  for `JdbcTemplate`, and boots a Hibernate `EntityManagerFactory` every run.
- **Redis runs in compose and is connected to nothing** — no client, no `@Cacheable`.
- **Phase numbering is dual-tracked in the older docs** — **new**. `baseline.md` and
  handoff §11 use "Phase 6" for the perf pass, which is now Phase 11; under the live
  roadmap Phase 6 is Projections. Read any pre-north-star phase number with care.
- **handoff §9 still teaches the disproven hypothesis in place**, while §12 Q4 says the
  opposite. The banner above §9 is the correction; the body wasn't updated.
- **handoff §5's `games` DDL is stale** — 7 columns, no `nflverse_game_id`. Reality is 18.
- Owed and missing: `perf/results.md` (Phase 11), `perf/projection-accuracy.md` (Phase 6,
  and north-star calls the whole projection model *"a hypothesis until that file exists"*),
  and the site attribution footer, owed since Phase 0.

No `TODO` or `FIXME` exists anywhere in the repo.

---

## 6. Who owns what

Four documents, and they do not overlap. **When this file disagrees with any of them, they
win and this file gets fixed.**

| Doc | Owns |
|---|---|
| [`north-star.md`](north-star.md) | **Scope, sequencing, product decisions.** What we're building, for whom, in what order, and what we're deliberately not building |
| [`fantasy-platform-handoff.md`](fantasy-platform-handoff.md) | **Engineering rationale** — §5 schema, §6 scoring, §8 security, §9 performance. **§1 and §11 are superseded** by the north star |
| [`../CLAUDE.md`](../CLAUDE.md) | **Operational memory** — invariants, measured numbers, the traps in the source data, the commands |
| **this file** | **Navigation.** Where things are, what state they're in, where to look next. No facts of its own |

The working agreement, which governs all four: **measure before asserting, and prove a
constraint by trying to violate it.**
