# fantasy-kai — project brain

NFL fantasy analytics platform. Java 25 / Spring Boot 3.5.16 · PostgreSQL 16 · Redis 7 · Next.js 16 / React 19 / TypeScript 6 (Phase 5c).

**Two docs sit above this file, and they own different things.**

- **[`docs/north-star.md`](docs/north-star.md)** — source of truth for **scope, sequencing and product decisions**. What we are building, for whom, in what order, and the list of things we are deliberately not building.
- **[`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md)** — source of truth for **engineering rationale**: §5 schema, §6 scoring, §8 security, §9 performance. Its §1 (product definition) and §11 (build plan) are **superseded** by the north star.

This file is the operational memory that sits alongside both. When it disagrees with either, the doc wins — and fix this file.

**[`docs/map.md`](docs/map.md) is the front door** — the status board, every package's classes, the two pipelines, and where to look for anything. It owns no facts; it links to whichever of these three does.

**[`docs/orientation.md`](docs/orientation.md) is the plain-English door** — what this is, how a
request really works, real-vs-planned, and a glossary of every term the other three assume. It
owns explanation, not facts.

## Every session starts here

```bash
./scripts/session-check.sh        # ~3s, read-only
```

It runs automatically at session start (the `SessionStart` hook in `.claude/settings.json`), and
by hand any time. It re-derives from the tree and the database what these docs only *claim*:
toolchain, container health, git and PR state, Flyway checksums against the applied migrations,
row counts, ingest freshness, jar staleness, and the file/test/endpoint counts written in prose.

**Where the check and a doc disagree, the check wins and the doc is what gets fixed** — before
the session moves on to anything else. A `drift` row is not noise; it is a doc that has started
lying, which in a project whose whole argument is *measure before asserting* is the most
expensive kind of bug there is.

**A check that cannot run prints `?`, never `ok`.** Docker down, `gh` unauthenticated, no
`python3` — each reports unknown with its reason. This is `CsvValues.shortValue` one layer up:
the moment "could not read it" and "read it, it was fine" print the same thing, the check is
worse than no check.

It does **not** run the test suite, build the frontend, or check a deployment. Those are the
definition of done at the other end of the session — see the last section of this file.

## The one idea

**Store raw stat lines, never fantasy points. Compute points on demand against a ruleset.**

Full PPR, half PPR, standard and TE premium stop being three code paths and become three rows in a table. Everything else in the design serves this. If a change would persist a computed point value, it is the wrong change.

## Invariants — do not break these

| Invariant | Why | Expires |
|---|---|---|
| `player_game_stats` carries **no index past its primary key** | §9's whole performance story is a measured before/after. An index added early destroys the baseline and there is no way to recover it without re-measuring from scratch. | Phase 11, as a numbered migration |
| Never store `fantasy_points` / `fantasy_points_ppr` | The source ships both. A stored point value is correct for exactly one ruleset. Persisting them reintroduces the thing the architecture exists to avoid. | never |
| Flyway owns the schema; `ddl-auto` stays `validate` | Versioned schema from commit 1. **It must never become `update`** — that is the whole guarantee today. The drift check this setting is famous for is *vacuous here*: there are zero `@Entity` classes, so it validates nothing, and Phase 5's decision to stay on `JdbcTemplate` keeps it that way. Say the real guarantee, not the advertised one. | never |
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
  ingest/          Phase 1 — nflverse + Sleeper pipeline (19 classes)
  scoring/         Phase 2 — ruleset model, validator, dot-product evaluator
  query/           Phase 3 — JdbcTemplate reads, StatKey-generated SQL, the §8 whitelists
  api/             Phase 3 — controllers, DTOs, RFC 7807 advice; Phase 5 write endpoints
  auth/            Phase 5 — filter chain, JWT, rotating refresh, Argon2id, rate limit
backend/src/main/resources/db/migration/   Flyway. V1 schema, V2 ingestion support,
                                           V3 presets, V4 Vegas columns, V5 refresh_tokens
backend/Dockerfile                         Phase 5d. Temurin 25 JRE on Alpine, arm64 and amd64
deploy/                                    Phase 5d. compose.prod.yml + Caddyfile + README.md
                                           (the runbook, the firewall trap, 9 acceptance checks)
frontend/        Phase 5c — Next.js 16 App Router. api.ts holds the only access token, in memory
backend/src/test/resources/nflverse/       Real 2024 rows as fixtures — not invented
docs/map.md                                Front door — status board, class map, pipelines
docs/orientation.md                        Plain-English door — glossary, real-vs-planned
docs/north-star.md                         Scope, roadmap, product invariants
docs/fantasy-platform-handoff.md           Engineering rationale (§1/§11 superseded)
docs/perf/                                 baseline.md only so far; projection-accuracy.md (Phase 6)
                                           and results.md (Phase 11) are owed
perf/rankings.js                           k6 load script — pins season=2025 on purpose
scripts/                                   session-check.sh (runs at every session start),
                                           package.sh + lib/jar-state.sh, install-ingest.sh,
                                           launchd plist, ingest-once.sh, perf-explain.sh,
                                           db-restore.sh (was neon-restore.sh)
.claude/                                   SessionStart hook + the /review-pass command
```

## Current state

| Phase | Status |
|---|---|
| 0 — Foundation | ✅ `921e21a`, `8fdad10`, `d06f133` |
| 1 — Ingestion | ✅ `6c591e5` — six-season backfill in 22.8s |
| 2 — Scoring engine | ✅ `com.fantasykai.scoring` + V3 presets, 47 tests |
| 3 — Read API | ✅ `ec5a8e9` — `com.fantasykai.api` + `.query`, 25 tests (72 in the suite) |
| 3.5 — k6 baseline | ✅ 1/5/10/20 VUs measured — p95 **21.4 ms → 125.0 ms**, throughput saturates at ~256 req/s. **The bottleneck is Postgres, not the Java scorer (88/12).** See below. |
| 4 — Vegas in the schema | ✅ `73a0842` — `V4` widens `games` by 10 columns; `GameIngestor` reads 18 of the source's 46 and generates its upsert from one ordered list. 8 new tests (80 in the suite) |
| 4.5 — pre-Phase-5 fixes | ✅ `f478233` daily ingest installed + freshness health + CSV header assertion · `5d5b8b4` canonical hash over the resolved form + decimal rounding. **102 in the suite** |
| 5a/5b — Auth + tenant isolation | ✅ `com.fantasykai.auth` (16 classes) + `V5`. Default-deny chain, Argon2id, HS256 JWT, rotating refresh with family revocation, Bucket4j on Redis. **130 in the suite** |
| 4.75 — toolchain recovery | ✅ 2026-09-12 — JDK 25 found, enforcer rule, jjwt/bucket4j/bcprov bumped, **the headless-context bug the suite could not see** fixed. **132 in the suite** |
| 5c — Web shell | ✅ Next.js 16 App Router, 21 `.ts`/`.tsx` files (23 under `frontend/src`). Landing, rankings, player detail, auth, ruleset builder. **Attribution footer shipped — owed since Phase 0.** Proved end to end in a browser: a user-built 6-point-passing-TD ruleset put Stafford at #1 with 442.4 where Half PPR had him 4th at 350.4 |
| 5d — Deploy | ⬅ **in progress** — host changed: **Oracle Cloud Always Free VM + Caddy + Vercel**, not Fly/Neon/Upstash, because those free tiers stopped existing (north-star §5d has the survey with sources). `deploy/compose.prod.yml` + `Caddyfile` + `README.md` written and **proved on this Mac**: whole stack healthy, 6 of the 10 acceptance checks green. Two real bugs found by running it — HSTS configured but never sent, and a CORS allowlist with no mechanism to reach production. **134 in the suite.** Waiting on a domain and the accounts |
| 6 — Projections · 7 — League import (ESPN + Sleeper) · 8 — Roster tools | |
| 9–11 | consensus board · iOS (Expo) · perf pass |

Full roadmap and the reasoning for the order: [`docs/north-star.md`](docs/north-star.md) §10.

**Java 21 → 25 on 2026-09-10.** One property in `pom.xml` plus CI; no source file changed and
the suite passed on 25 unmodified. **It was done by VS Code's App Modernization extension, which
installed its JDK 25 into `~/.jdk` — a directory `java_home` does not scan — and left.** The
property stayed, the JDK became invisible, and the build stopped working on this machine for three
days. Temurin 25.0.4.1 now lives in `~/Library/Java/JavaVirtualMachines`, where `java_home` finds
it, and the enforcer rule in `pom.xml` makes the next occurrence say so. The tool's leftovers under
`.github/modernize/` are untracked (it wrote its own `.gitignore` of `**/*`) and safe to delete. Spring Boot 3.5.16 documents Java **17 up to and including
25**, so this is the top of the supported range rather than past it. The reason is dates, not
features — 25 is the current LTS and September 2026 closes the window on permissively licensed
JDK 21 updates from Oracle; handoff §4 carries the argument. **`docs/perf/baseline.md` stays on
Temurin 21.0.11**: it records what a measurement ran on, not what the stack is today, and Phase
11 re-captures on 25 before it compares anything.

2026 season opens **Sept 10**. The 2026 schedule is loaded (272 games). **Week 1 arrived in two
pieces, and that is the daily pull's entire justification**: on 2026-09-12, with only the opener
played, the source carried 135 rows and 134 stored. By 2026-09-14 the full slate was published —
1,041 read, **1,040 stored**. Before week 1 published at all, those runs correctly recorded
`SKIPPED`. A weekly pull would have held a 13%-complete week 1 for days.

**The daily pull is installed** (`./scripts/install-ingest.sh`; launchd exit 0 re-verified
2026-09-14). It fires on wake rather than at 06:00 on a sleeping laptop, and on local time rather
than ET — so gaps are expected, and `/actuator/health`'s `ingestFreshness` component is what makes
them visible instead of silent.

**It stopped for three days (2026-09-09 → 2026-09-12) and nothing said so.** Three failures
stacked: Phase 5a's `filterChain` broke the headless entrypoint, the stale-jar guard then refused
to run the old jar, and the JDK-25-in-`~/.jdk` problem meant the rebuild the guard asked for could
not run either. `ingestFreshness` was correct the whole time and DOWN the whole time — **with no
process alive to be asked**, because there is no deployed instance yet. That is the argument for
Phase 5d, and it is why the Fly machine sets `auto_stop_machines = false`: `IngestScheduler` only
fires inside a running JVM, and 06:00 ET is not an HTTP request.

**Then it stopped again — 2026-09-13 and 2026-09-14 — and again nothing said so.** This time one
failure, not three: the stale-jar guard compared **mtimes**, and at 23:49:31 on the 12th some git
operation rewrote **42 files' modification times without changing a byte of any of them**,
fourteen minutes after the jar was packaged. `backend/src` last *changed* in `8a0072a` at 23:12;
the jar was built at 23:35. So the guard refused a jar built from exactly the source it was
comparing against, twice, and the only trace was exit 2 in a log nobody reads. **Which git
operation did it is not recoverable — and that is the lesson, not a gap in the investigation: an
mtime is not a fact about content.** The guard now compares a sha256 of `backend/src/main` +
`pom.xml` recorded beside the jar at package time (`scripts/lib/jar-state.sh`, one definition,
three callers), and `session-check.sh` reads `launchctl`'s exit status — which is **~36 hours
louder** than `ingestFreshness`, whose threshold is 36h by design. Cost of the two days: week 1
sat 87% incomplete in the database.

## Measured numbers — do not re-derive or estimate these

From the loaded database, 2020–2025:

| | |
|---|---|
| `player_game_stats` rows stored | **112,319** (112,450 read; 131 dropped for blank `player_id`) |
| …plus 2026 week 1, first partial pull 2026-09-12 | **134** (135 read; 1 dropped, same blank-`player_id` cause) → **112,453** |
| …week 1 complete, 2026-09-14 | **1,040** (1,041 read; 1 dropped) → **113,359** total |
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

**The bottleneck is Postgres, not the Java scorer.** Measured at 20 VUs:
**Postgres 486–587% CPU (~5.3 cores, 20.7 ms/req) against the JVM's 73%
(0.73 cores, 2.9 ms/req) — an 88/12 split.** It isn't disk (4,044 buffer hits,
zero reads). Throughput saturates at ~256 req/s from 10 VUs on while latency
doubles 10→20 — queueing at a resource at capacity. Phase 11's *order* survives
(a cache hit skips both), but the matview and index attack the dominant cost, so
they should be worth **more** than the original plan predicted. Machine had 1.7
of 8 cores free, so this is the endpoint, not the laptop.

**The thing to say out loud:** the concurrency curve proves the endpoint is
compute-bound but *cannot say whose compute*. That took a second measurement —
sampling both processes — and it contradicted the design. handoff §9 and §12 Q4
both now teach the corrected version; §9's original framing is kept visible as a
hypothesis that was tested and failed, which is a better story than one that was
assumed.

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
- **An absent CSV column and a zero are the same thing to `CsvValues`, and that is a
  silent-failure machine.** `shortValue` maps a missing column to 0 — correct for "did not record
  this" in a box score, catastrophic for "upstream renamed it": every row parses, the counts match,
  `IntegrityChecks` only compares season and week, and the run reports `SUCCESS` with a stat zeroed
  for a whole season. `NflverseClient` now verifies the header against the columns each ingestor
  reads, generated from the same `List<Field>` that builds the upsert. Safe as a hard failure
  because it was measured: `stats_player_week` (150 columns) and `snap_counts` (16) have identical
  headers across all six loaded seasons.
- **A health indicator that reports DOWN for something degraded will restart your machine.**
  `ingestFreshness` goes DOWN when the daily pull has stopped — correct, and a platform probe on
  `/actuator/health` would then kill a server that is serving six seasons perfectly. The `liveness`
  group in `application.yml` exists for that; point deploys at `/actuator/health/liveness`. Found by
  the Phase 0 acceptance test going 503, not by reasoning.
- **Hash what a ruleset *does*, never the JSON someone wrote.** `canonicalHash` serialized the
  authored maps while `compile` zero-fills a `double[13]`, so `{"rec_td":6}` and
  `{"rec_td":6,"rec":0}` scored identically and hashed differently — the one collision the cache
  key exists to prevent. It has three shapes (absent vs explicit zero; an override repeating the
  base rate; an empty override block), so enumerating them is the wrong fix. The hash now runs over
  the compiled arrays in `ResolvedRuleset`, where two rulesets hash the same exactly when they
  score the same. An override *to* zero stays correctly distinct — zero is not the base rate.
- **`Math.round(x * 100) / 100.0` is not decimal rounding.** `0.145 * 100` is
  `14.499999999999998`, and `Math.round` breaks ties toward positive infinity, so `-0.125` and
  `+0.125` rounded different distances — and `pass_int`/`fum_lost` carry negative rates.
  `roundForDisplay` uses `BigDecimal` `HALF_UP`.
- **`ResultSet.wasNull()` reports on the last column *read*, and Java evaluates arguments left to
  right.** `new Cached(compile(rs.getString("rules")), rs.wasNull() ? null : owner)` asks whether
  `rules` was null, not `user_id` — so every preset's NULL owner became user 0, and all four
  presets 404'd for everyone including anonymous callers. Read the flag into a local immediately
  after the column it describes.
- **`@Transactional` rolls back the thing you did *before* throwing.** `RefreshTokenService.rotate`
  revokes the token family and then throws on a replay; under a plain `@Transactional` the throw
  undid the revocation, so theft detection ran and left no trace. `noRollbackFor =
  InvalidTokenException.class` is load-bearing. Found by a test, not by reading the code.
- **`Keys.hmacShaKeyFor` picks the JWT algorithm from the key's length** — 32 bytes gives HS256,
  48 gives HS384, 64 HS512. So the algorithm in production depends on how long a string somebody
  pasted into an env var, and a doc saying "HS256" quietly stops being true. `JwtService` pins
  `Jwts.SIG.HS256`.
- **`Argon2PasswordEncoder` needs BouncyCastle and Spring Security does not pull it in.** It fails
  on the first register or login rather than at startup, so it presents as an auth bug. `pom.xml`
  declares `bcprov-jdk18on` explicitly.
- **A test with no Redis container silently uses the dev one.** `spring.data.redis.host` defaults to
  `localhost:6379`, which on this machine is the running compose container — so rate-limit buckets
  leaked between test classes and across runs, and unrelated tests failed on the sixth login.
  `src/test/resources/application.properties` points it at `redis.invalid`; a class that needs Redis
  declares a container and overrides it.
- **Maven's incremental compiler hides signature changes.** Widening `ScoringProfiles.byId` to two
  arguments left every caller uncompiled and `./mvnw compile` reported BUILD SUCCESS. Use
  `./mvnw clean compile` when a public signature moves, or the first honest error arrives in CI.
- **`/usr/libexec/java_home` only scans two directories, and a JDK outside them does not exist
  as far as it is concerned.** It reads `/Library/Java/JavaVirtualMachines` and
  `~/Library/Java/JavaVirtualMachines` — *not* `~/.jdk`, which is where VS Code's App Modernization
  extension put the JDK 25 it installed to perform the Java 21 → 25 bump. So `java_home -v 25`
  returned **21** and exited **0**, and `JAVA_HOME=$(...) ./mvnw verify` died at `error: release
  version 25 not supported`: an error about the compiler, raised by the line that was supposed to
  choose one. **The JDK was never missing — it was invisible to the project's own documented
  command.** Verify with `java -version`, never with the exit code. `pom.xml` now carries a
  `maven-enforcer-plugin` `requireJavaVersion` rule so the failure names the JDK and the fix;
  proved by building with `JAVA_HOME` on 21 and reading the message. This cost three days of daily
  ingest — see the two entries below, which are the same outage.
- **`./mvnw verify` takes either ~32s or ~58s, and the difference is a Surefire timeout — not
  Docker, and not the thing either previous explanation blamed.** This entry has now been wrong
  twice, so here is the arithmetic rather than a story.

  The structure everyone agreed on is real: the suite leaves **ten cached Spring contexts, seven
  with an embedded Tomcat**, closing *serially* at JVM exit under Spring Boot 3.5's `graceful`
  default. What was never measured is how long that actually takes. Measured directly on
  2026-09-14 by varying `surefire.exitTimeout` — Surefire's cap on how long it waits for the
  forked JVM to die after `System.exit(0)` before killing it:

  | `surefire.exitTimeout` | `clean verify` | Fork killed? |
  |---|---|---|
  | 5s | **32.2s** | yes, at 5s |
  | 30s (the default) | **57.9s** (57.866 · 57.963 · 57.437) | yes, at 30s |
  | 600s | **62s** | no — it exited on its own |

  One model fits all three: **work ≈ 27s, teardown ≈ 35s, total = work + min(teardown, timeout)**.
  32.2 − 5 = 27.2 · 57.9 − 30 = 27.9 · 62 − 27 = 35. Three independent runs, one equation, no
  spare terms.

  So **teardown is the largest single term in the build** — bigger than compilation and all 132
  tests combined — and it sits *just* over the 30-second default, which is why the same command
  on the same commit lands at 31s one day and 58s the next. Both numbers were always real; they
  are two sides of one threshold.

  Three runs at 57.866 / 57.963 / 57.437 — **a tenth of a second apart** — was the tell. Work
  does not reproduce that tightly. A constant does.

  What is still unexplained: on 2026-09-12 four `clean verify` runs came in at **30.7 / 31.1 /
  33.0 / 32.1s with no fork kill at all**, meaning teardown finished inside 30s that day, on
  identical code and the same 132 tests. Teardown time varies by enough to cross the threshold
  and nobody knows on what. Do not attribute it without measuring it — that is the mistake this
  entry has already made twice, once blaming serial teardown and once blaming a cold Docker
  daemon.

  **The shared Testcontainers base class `docs/map.md` §5 owes is back to urgent.** It was
  downgraded to "worth doing, not urgent" on the belief that teardown cost ~9s. It costs ~35s,
  and fewer contexts is the only lever that moves it.
- **A focused `<input type="number">` treats the mouse wheel as increment.** The ruleset builder is
  taller than a screen, so the ordinary gesture — set a rate, scroll down to Save — silently moved
  the rate that still had focus. No error, no highlight, and the ruleset saves wrong: a scoring
  bug introduced by scrolling. `RateInput` blurs on wheel rather than calling `preventDefault`,
  because the user is trying to scroll the page and should be allowed to. Found by scrolling the
  form in a browser, not by reading it.
- **`POST /api/v1/scoring-profiles` takes `rules` as a JSON *string*, not a nested object.** The
  column is `jsonb` and the server hands the raw text to `RulesetValidator`, which is what lets it
  reject an unknown key *by name* instead of silently dropping whatever Jackson could not bind.
  Sending an object is `400 Failed to read request`.
- **A user-scoped query that fires before the session is restored caches the logged-out answer.**
  `/api/v1/scoring-profiles` is filtered by the JWT subject, and on a fresh load there is no access
  token yet — it is still being traded for from the refresh cookie. The request went out
  unauthenticated, returned the four presets, and TanStack Query cached that for five minutes: a
  signed-in user could not see their own ruleset until a hard reload. `useProfiles` is gated on
  `status !== "restoring"`; every other query is public and deliberately is not.
- **A `@Bean` that takes `HttpSecurity` breaks every entrypoint that is not a web application.**
  `scripts/ingest-once.sh` runs the daily pull with `--spring.main.web-application-type=none`, and
  `HttpSecurity` exists only in a servlet context — so Phase 5a's `SecurityConfig.filterChain` made
  the one-shot ingest die at startup with *"Parameter 0 of method filterChain required a bean of
  type HttpSecurity"*. **All 130 tests stayed green**, because every one of them boots a web
  application; the suite could not see the one shape that mattered. It then hid for three days
  behind two louder failures — the stale-jar guard refused to run, and the rebuild that would have
  satisfied it could not run either — so the ingest was already broken *before* the JDK was.
  The fix is `@ConditionalOnWebApplication(type = SERVLET)` on the bean, not on the class:
  `@EnableMethodSecurity` needs no servlet and gating it would silently disable every
  `@PreAuthorize` in the headless run. `OneShotContextTests` boots with
  `webEnvironment = NONE` and asserts the context is *not* a `WebApplicationContext`, so it fails
  for the right reason.
- **`find -newer` compares mtime, and `git checkout` rewrites mtimes without changing a byte.**
  The stale-jar guard asked "is any file under `backend/src` newer than the jar", which is a
  question about clocks pretending to be a question about code. On 2026-09-12 a git operation
  restamped 42 files at 23:49:31 — content identical to `HEAD`, last *changed* at 23:12, jar
  packaged at 23:35 — and the daily ingest refused to run for two mornings on a jar that was
  correct. Two fixes, both narrowing what the question means: compare a **sha256 of the source**
  recorded beside the jar at package time, and watch only `backend/src/main` + `pom.xml`, since
  `backend/src/test` never enters the jar and a test file was half of what tripped it. The mtime
  rule survives only as a fallback for a jar with no recorded hash, and it **says so** when it
  fires. Proved by `touch`ing a source file: the old rule flips to STALE, the hash does not move.
- **A security header that is configured is not a security header that is sent.** Spring Security's
  `httpStrictTransportSecurity()` block has been in `SecurityConfig` since Phase 5a and emitted
  **nothing** in production, because its writer only fires when `request.isSecure()` — and behind a
  proxy that terminates TLS, Tomcat sees plain HTTP and says false unless
  `server.forward-headers-strategy` is set. It was not. handoff §8's "HTTPS only, HSTS on" row would
  have shipped unsatisfied with config that reads correctly. **What made it invisible is what was
  next to it:** `x-frame-options` and `x-content-type-options` were both present on the same
  response, because those two do not check `isSecure()`. Two out of three headers arriving is a far
  better disguise than none. Found by curling the running stack and grepping, not by reading the
  config. `framework`, not `native`: the Tomcat valve needs an `internal-proxies` regex matching
  whatever address the proxy has on the container network, which breaks quietly when the network
  changes. `framework` trusts the headers unconditionally, which is sound **only** because the
  backend has `expose` and no published port and Caddy replaces `X-Forwarded-*` — publish that port
  and a forged `X-Forwarded-Proto` is enough to fake a secure request.
- **`SameSite` is decided by registrable domain, and `localhost` hides it completely.** The refresh
  cookie is `SameSite=Strict`. `fantasykai.vercel.app` → `fantasykai.fly.dev` is **cross-site** (two
  registrable domains), so the browser sends no cookie to `/api/v1/auth/refresh` and every reload
  logs the user out. `localhost:3000` → `localhost:8080` is **same-site**, because SameSite ignores
  ports — so every local test passes and production is broken. `allowCredentials(true)` does not
  save it: that is the CORS layer, this is the cookie layer, and both have to permit it
  independently. `SecurityConfig` documented the CORS half and was silent on the other, which is
  exactly what kept it hidden. The fix is one registrable domain (apex on Vercel, `api.` on the VM),
  not a weaker cookie.
- **A config value with no way to change it is a value that will be wrong.**
  `fantasykai.auth.allowed-origins` was a hardcoded yml list carrying the comment "the Vercel origin
  is added at deploy" — and nothing added it, because no mechanism existed. It is now one
  comma-separated env var, and `CorsBindingTests` asserts the **split**, not the plumbing: a
  `List<String>` bound from one string either becomes the origins you meant or collapses into a
  single comma-joined blob that matches nothing, and both of those start the application.
- **A launchd plist with a placeholder path is not an installed job.** The plist shipped three
  `__REPO__` placeholders and an instruction to "edit the two by hand"; it was never loaded, `logs/`
  stayed empty, and `ingest_runs` recorded three of the seven days before kickoff. `install-ingest.sh`
  does the substitution and fails loudly if the agent did not land. Check with
  `launchctl list | grep fantasykai`.

## Scoring — how it fits together

```
scoring_profiles.rules (JSONB)
   -> RulesetJson.read      parse; reject unknown keys rather than ignoring them
   -> RulesetValidator      bound rates to [-10,10], bonuses to 20
   -> ResolvedRuleset       version dispatch, then position overrides folded into double[]
   -> ScoringEngine.score   one dot-product + threshold bonuses. no rounding.
```

`ResolvedRuleset.hash()` is the §9 cache key: logically identical rulesets hash identically, so two users with the same league settings share one entry. **It is computed over the compiled `double[]` arrays, not over the authored JSON** — that placement is the invariant, not an implementation detail. Verify any change against `RulesetHashTests`, whose pairs assert twice: that the two rulesets score the same *and* that they hash the same.

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

`/api/v1/scoring-profiles` serves `user_id IS NULL OR user_id = ?` bound to the JWT
subject — **in the query, not the service**. A logged-out caller binds `null`, which
matches nothing, so they see the four presets and no more.

**The chain gates endpoints; the query gates rows.** Reads are `permitAll` and every
mutation is `authenticated()` plus `@PreAuthorize`. Which *profile* you may score
against is decided by the ownership filter, never by the URL — and
`ScoringProfiles`' compile cache carries its entry's owner, because the filter only
runs on a miss and a warm cache would otherwise hand one user's ruleset to the next.
A profile you do not own is **404, not 403**: 403 confirms the id exists.

## Commands

```bash
# What is actually true right now -- toolchain, containers, git, Flyway checksums,
# row counts, ingest freshness, jar staleness, and the counts the docs claim.
# Runs itself at session start; exits 1 on any FAIL or drift.
./scripts/session-check.sh
./scripts/session-check.sh --no-network   # skip the two gh calls

docker compose up -d                      # Postgres on :5433 (not 5432), Redis on :6379

# Java 25 or nothing compiles. `java_home -v 25` does NOT fail when 25 is absent --
# it silently returns the newest JDK it has, so check the version it PRINTS, not the
# exit code. It also only scans /Library/Java/JavaVirtualMachines and
# ~/Library/Java/JavaVirtualMachines: a JDK anywhere else (~/.jdk, sdkman) is invisible
# to it. pom.xml's enforcer rule now names the JDK when this goes wrong.
export JAVA_HOME=$(/usr/libexec/java_home -v 25) && java -version

# The app needs JWT_SECRET or it refuses to start. `source .env` first, or:
JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run
cd backend && ./mvnw -B verify            # needs Docker — Testcontainers boots a real PG 16

# psql
docker compose exec postgres psql -U fantasykai -d fantasykai

# one-shot historical backfill
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true

# one-shot current-season pull (what launchd runs daily)
./scripts/ingest-once.sh

# build the ingest jar AND record what it was built from. Use this, not a bare
# `./mvnw package`: without the .srcsha sidecar the staleness guard falls back to
# comparing mtimes, which a git checkout is enough to defeat.
./scripts/package.sh                      # add --with-tests to run the suite too

# install the daily job (idempotent; needs a current jar)
./scripts/install-ingest.sh
launchctl list | grep fantasykai        # loaded?
launchctl start com.fantasykai.ingest   # run it now
tail -f logs/ingest.log

# is the pipeline fresh?  (anonymous sees status only -- show-details is when-authorized)
curl -s localhost:8080/actuator/health | jq .components.ingestFreshness

# web shell (Phase 5c). Node 24 -- .nvmrc pins it; Node 20 went EOL 2026-04-30.
cd frontend && nvm use && npm ci
npm run dev                               # :3000, expects the API on :8080
npm run lint && npm run build             # what CI runs

# the deploy image, built and run against the compose Postgres
cd backend && docker build -t fantasykai-backend:local .
```

**Postgres is on 5433** because a Homebrew `postgresql@16` launchd service owns 5432 on the dev Mac and wins the connection. Symptom when this bites: `role "fantasykai" does not exist`.

## Working agreement

**Measure before asserting. Prove a constraint by trying to violate it.**

This project exists to be defended out loud in an interview, so any type, constraint or number that was picked by default becomes something its owner has to justify. Before a migration, pull the real values rather than reasoning about what the type should be — that habit has already caught the fractional sacks and the NULL-defeated unique constraint. Before quoting a number, run the query. Fixing a 200-line schema is free; fixing it under 112K rows is not.

Report design gaps you are *not* fixing explicitly rather than staying quiet about them.

## Definition of done — the review pass

Work is not finished when it works. It is finished when someone has tried to break it and
written down what they tried. **Every plan ends with this pass, and `/review-pass` runs it on
demand** — same checklist, defined once in [`.claude/commands/review-pass.md`](.claude/commands/review-pass.md).

**Read the evidence before the intent.** The diff first, in full; the plan, the commit message
and the docs second. Reading your own reasoning first is how a reviewer confirms it instead of
testing it — and the reviewer here is usually the author, which is exactly the bias the ordering
is there to defeat.

Then, in order:

1. **Run the gates and paste what they printed.** `./scripts/session-check.sh` ·
   `./mvnw -B verify` · `npm run lint && npm run build`. A gate you did not run is reported as
   **not run** — never as passing, never by omission. "Nothing here touches Java" is a
   defensible reason to skip one; silence is not.
2. **Walk the invariant table above as yes/no questions against the diff.** Persisted a computed
   point value? Indexed `player_game_stats`? Named a stat outside `StatKey`? Rounded before the
   API boundary? Concatenated SQL? Scored summed stats instead of summing scored games?
3. **Hunt the failure classes this codebase has already produced** — the traps section, not
   generic code smells. A default that hides an absence · an exit code trusted over output · an
   mtime trusted over content · NULL defeating a UNIQUE · a cache consulted before the ownership
   filter · `@Transactional` undoing the write before the throw · `wasNull()` and argument
   evaluation order · binary rounding · a bean that only exists in a servlet context.
4. **Audit every claim the diff makes in prose**, commit message included. Each number names how
   it was derived or it gets cut. A `drift` row from `session-check.sh` is a finding.
5. **Report findings ranked by severity**, each with a concrete failure scenario — specific
   inputs leading to specific wrong behaviour. "Could be fragile" is not a finding.
6. **Then two sections that are never skipped.** *What I did not fix, and why* — silence there
   reads as "nothing was left", which is almost never true. And *the argument against this
   change*: the strongest case that it should not merge as written, plus what would falsify the
   approach.

**If nothing was found, list what was searched.** "Nothing found" with a search list is a
finding. "Looks good" is not a review.
