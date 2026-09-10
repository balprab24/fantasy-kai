# The map

**Where everything is and what state it's in.** This file owns nothing. Every fact it
can't own, it links to — so when it disagrees with the doc that owns a fact, the doc wins
and this file gets fixed.

Last verified against the tree: **2026-09-10**, after Phase 5a/5b merged and the
runtime moved to Java 25.

---

## 1. Status board

| | |
|---|---|
| Phases shipped | **0 → 5b** |
| Currently next | **Phase 5c/5d** — Next.js web shell, then deploy |
| Backend | 73 files · 4,693 lines · Java 25 / Spring Boot 3.5.16 |
| Tests | 16 files · 3,049 lines · **130 tests**, all green · `./mvnw -B verify` ≈ 60s |
| HTTP endpoints | **12** — 5 public `GET`, 4 `/auth`, 3 authenticated mutations |
| Migrations | `V1` … `V5` |
| Data loaded | 112,319 stat rows · 25,065 players · 1,965 games · 2020–2026 |
| Frontend | none yet — **Phase 5c, next** |

| # | Phase | State |
|---|---|---|
| 0 | Foundation — compose, Boot skeleton, Flyway `V1` | ✅ |
| 1 | Ingestion — nflverse + Sleeper, six-season backfill in 22.8s. Daily pull installed under launchd `f478233` | ✅ |
| 2 | Scoring engine — ruleset model, validator, dot-product evaluator | ✅ |
| 3 | Read API — `JdbcTemplate` reads, `StatKey`-generated SQL, query whitelists | ✅ |
| 3.5 | Close the baseline — k6 at 1/5/10/20 VUs; **the bottleneck is Postgres, not the scorer (88/12)** | ✅ |
| 4 | Vegas in the schema — `V4` widens `games` by 10 columns | ✅ |
| **5** | **Auth + web shell** — Argon2id, JWT, rotating refresh, Bucket4j · Next.js shell | 🔶 5a/5b ✅ · 5c/5d ⬅ **next** |
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

`FantasyKaiApplication` — entry point. Supplies the single `Clock`. Since Phase 5a it is no
longer the only `@Bean` source outside `ingest`: `SecurityConfig` owns the chain and the CORS
allowlist, `RateLimitConfig` owns the bucket store. **No cache config yet** — that is Phase 11.

#### `ingest/` — Phase 1 · 19 files

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
| `IngestFreshnessHealthIndicator` | Reads `ingest_runs` back out as an actuator component. **DOWN means degraded, not dead** — which is why `application.yml` carries a `liveness` group for deploy probes |
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

#### `query/` — Phase 3 · 12 files

| Class | Does |
|---|---|
| `PlayerQueryRepository` | Every read the API makes, as parameterized JDBC |
| `StatColumns` | Generates the `SELECT` list from `StatKey`; reads a `ResultSet` into `double[]` by name |
| `PlayerSort` · `RankingScope` · `ScoringPosition` | **The three whitelists.** Each resolves a request string to an enum constant or throws |
| `ScoringProfileQueryRepository` | Preset metadata only — never the `rules` column. **Phase 5 grew it `OR user_id = ?`, bound in the query** — a logged-out caller binds `null` and matches only the presets |
| `ScoringProfileWriteRepository` | Insert / update / delete a user's own profile. Phase 5 |
| `DuplicateProfileNameException` | Two profiles with one name, for one owner |
| `ScorableRow` · `PlayerRow` · `GamelogRow` | Row records |
| `InvalidQueryParameterException` | A name that isn't on a whitelist |

#### `api/` — Phase 3 · 14 files

| Class | Does |
|---|---|
| `RankingsController` · `PlayerController` · `ScoringProfileController` | **Eight endpoints** — 5 public `GET` plus, since Phase 5, three profile mutations behind `@PreAuthorize` |
| `RankingsService` | Scores every player-week in Java, aggregates per player, sorts, pages |
| `PlayerService` | Player list, detail, and the scored game log |
| `ApiExceptionHandler` | RFC 7807 `problem+json` over `ResponseEntityExceptionHandler` |
| `PageResponse` | Generic page envelope. `DEFAULT_SIZE=50`, `MAX_SIZE=200` |
| `RankingRow` · `PlayerSummary` · `PlayerDetail` · `GamelogWeek` · `GamelogResponse` · `ScoringProfileSummary` | Response records |
| `PlayerNotFoundException` | 404 |

#### `auth/` — Phase 5a/5b · 16 files

| Class | Does |
|---|---|
| `SecurityConfig` | **The filter chain, and it is default-deny.** `permitAll` on an explicit short list, `authenticated()` on everything else, so a new endpoint is private until someone lists it. Also the CORS allowlist and HSTS |
| `AuthController` | Register · login · refresh · logout. Access token in the body, refresh token in an `HttpOnly` cookie — the asymmetry is the design, not an inconsistency |
| `AuthDtos` | The codebase's **first request bodies**, which is why §8's `@Valid` row starts mattering here and not in Phase 3 |
| `JwtService` | Issues and verifies the 15-minute HS256 access token. **Pins `Jwts.SIG.HS256`** — `hmacShaKeyFor` otherwise picks the algorithm from the key's length, so the env var would decide it |
| `JwtAuthFilter` | `Authorization: Bearer` → security context. No token passes straight through unauthenticated; the chain decides what that may reach |
| `RefreshTokenService` | Rotation with reuse detection. **`noRollbackFor = InvalidTokenException`** is load-bearing — a plain `@Transactional` undid the family revocation on the way out |
| `RefreshTokenRepository` | The `refresh_tokens` table from `V5`. Hashed at rest, never the token itself |
| `UserRepository` | The `users` table `V1` created and nothing touched until now. `JdbcTemplate`, deliberately — north-star §5a |
| `AuthRateLimitFilter` | 5/min/IP across the **whole** `/api/v1/auth/**` surface. One bucket, so guessing passwords and enumerating emails share a count instead of resetting each other |
| `RateLimitConfig` | Bucket4j's Redis store, on Lettuce directly — `RedisTemplate` lacks the compare-and-swap Bucket4j needs. Resolved lazily, so a Redis outage costs logins and not the site |
| `AuthProperties` | `@ConfigurationProperties`. **Refuses to start** without a ≥32-byte `JWT_SECRET`; no committed default |
| `Problems` · `ProblemAuthenticationEntryPoint` · `ProblemAccessDeniedHandler` | RFC 7807 for the filter-chain paths, which run before `DispatcherServlet` and so never reach `ApiExceptionHandler` |
| `InvalidTokenException` · `EmailAlreadyRegisteredException` | Absent, expired, forged or replayed token · a duplicate registration |

### Everything else

```
backend/src/main/resources/
  application.yml                  datasource, flyway, ingest config
  db/migration/                    V1 schema · V2 ingestion support · V3 presets · V4 Vegas
                                   columns · V5 auth
backend/src/test/
  java/com/fantasykai/             14 test classes + ApiFixture, Presets
  resources/application.properties test JWT secret, and Redis pointed at redis.invalid
                                   so a test that needs it has to declare a container
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

### Closes with Phase 5c/5d

| Risk | Detail |
|---|---|
| **Nothing is deployed** | The end of Phase 5 is "a site you can log into", and HTTPS/HSTS cannot be satisfied locally. Fly + Neon + Vercel, north-star §5d |
| **The attribution footer is still owed** | nflverse (CC BY 4.0) and FFC both require it; outstanding since Phase 0. It ships with the web shell |

### Closed since the 2026-09-08 audit

| Was | Closed by |
|---|---|
| `canonicalHash()` broke its own invariant | `5d5b8b4` — the hash moved to `ResolvedRuleset`, over the compiled arrays. Two rulesets now hash the same exactly when they score the same. Three shapes collapsed, not the one that was found |
| `roundForDisplay` used binary float rounding | `5d5b8b4` — `BigDecimal` `HALF_UP`. `0.145 → 0.15`, and negatives round the same distance as positives |
| The daily ingest ran nowhere | `f478233` — `scripts/install-ingest.sh`, verified by running it under launchd |
| `ingest_runs` was written and never read | `f478233` — `IngestFreshnessHealthIndicator`, with a `liveness` group so a stale pipeline cannot restart a machine |
| A renamed upstream column zeroed a stat and reported SUCCESS | `f478233` — header verified against the columns each ingestor reads, generated from the same `List<Field>` as the upsert |
| `NflverseClient` leaked the response body on the 404 path | `f478233` |
| No auth at all; five endpoints world-readable | Phase 5a/5b — default-deny chain, `show-details: when-authorized` |
| `ScoringProfiles.byId` had no ownership check | Phase 5a/5b — filtered in the query, and the compile cache carries its owner so a warm entry is not a bypass |
| `ScoringProfiles.evict(long)` had zero callers | Phase 5a/5b — every profile write calls it |
| §8's dependency row: "Dependabot on, `dependency-check` in CI" | `4f30bb6` — `.github/dependabot.yml`, weekly and grouped because a PR queue nobody reads is the same as no Dependabot. The scan is its own job in `ci.yml`, `continue-on-error`, and `da28c9b` made it **skip loudly** rather than fail every run when `NVD_API_KEY` is unset |

### Operational, and unscheduled

| Risk | Detail |
|---|---|
| `StatIngestor` holds a whole season in memory | Identity mapper into a `List<CSVRecord>`, then a second full-size batch, under a JVM with no `-Xmx` |
| The ingest depends on a laptop being awake | launchd fires on wake, not at 06:00, and on local time rather than ET. Acceptable for a pull with no deadline — and the reason the freshness indicator exists rather than being optional |
| No shared Testcontainers base class | **Measured 2026-09-10: this is now half the build.** Ten cached Spring contexts, seven of them with an embedded Tomcat. The 130 tests execute in **23.2s**; tearing those ten contexts down takes **28.5s**, and Surefire kills the fork on its 30s deadline. The build still reports SUCCESS, which is exactly why it went unnoticed for five phases. Identical on 21 and 25 (56.072s vs 56.044s), so the Java 25 bump did not cause it — see [`../CLAUDE.md`](../CLAUDE.md) |
| Hikari is at Spring Boot's default 10 connections | 10 × ~39 ms occupancy ≈ the 256 req/s ceiling. Phase 11 must not mistake raising it for a fix |

### Housekeeping

- **`ddl-auto: validate` validates nothing** — there are zero `@Entity` classes, and
  Phase 5 decided deliberately to keep it that way (north-star §5a). The setting's real
  guarantee is that it can never become `update`; CLAUDE.md now says that rather than
  claiming a drift check. JPA remains on the classpath as a carrier for `JdbcTemplate`.
- **Redis is claimed at last** — Bucket4j's bucket store, resolved lazily so an outage
  costs logins and not the site. Phase 11's ruleset cache is the second consumer.
- **Phase numbering was dual-tracked and is now decoded, not rewritten.** handoff §11
  carries an old→new table; `baseline.md`, the code comments and `perf/rankings.js` were
  corrected to Phase 11. `V1` and `V2` still say "Phase 6" in a comment and **stay that
  way** — Flyway checksums an applied migration, so editing one breaks local startup while
  CI stays green. `V4`'s "Phase 6" is correct; it means Projections.
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
