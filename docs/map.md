# The map

**Where everything is and what state it's in.** This file owns nothing. Every fact it
can't own, it links to — so when it disagrees with the doc that owns a fact, the doc wins
and this file gets fixed.

Last verified against the tree: **2026-09-21** — and no longer by hand. `./scripts/session-check.sh`
re-derives every count in the status board below, plus the database's own numbers, in about three
seconds, and runs at the start of every session. **When it disagrees with this file, it wins.**
That is what it was built for: the "14 files" in the row below was wrong the day it was written,
and nothing caught it for two days.

---

## 1. Status board

| | |
|---|---|
| Phases shipped | **0 → 5c** |
| Currently next | **Phase 11.5** — Spring Boot 3.5 → 4, overdue security work (north-star §10). Then Phase 6 — Projections. **5d is live** at `https://www.fantasykai.com` since 2026-09-23 |
| Backend | **81** files · Java 25 / Spring Boot 3.5.16 — **OSS-EOL since 2026-06-30**, Tomcat pinned to 10.1.59 over the parent's 10.1.55. See [`../CLAUDE.md`](../CLAUDE.md) "The EOL clock" |
| Tests | 20 files · **175 tests**, all green (this row said "17 files" until 2026-09-28; there were 18 — counted with `find`, not recalled) · `./mvnw -B clean verify` **≈ 32s of work + up to 30s waiting for the forked JVM to die** — 47.5s measured 2026-09-24, 58.2s on 09-21, 57.9s on 09-14, 30.7s on 09-12. Teardown is the biggest term in the build; see [`../CLAUDE.md`](../CLAUDE.md) |
| HTTP endpoints | **13** — 6 `GET`, members only since 2026-09-29 (the sixth is `/players/{id}/career`, 2026-09-28), 4 `/auth`, 3 authenticated mutations |
| Migrations | `V1` … `V6` (`V6`: `players.birth_date`, `teams.logo_url`) |
| Data loaded | **114,479** stat rows (2026 week 2 refilled 2026-09-21 after a 4-day outage) · 25,066 players · 1,965 games · 2020–2026 |
| Frontend | **Next.js 16 · 80 `.ts`/`.tsx` files** (82 under `frontend/src`) — every page rendered per request since 2026-09-29, under a nonce-based CSP (`src/proxy.ts`). Visual system "Prime time" since 2026-09-30 (branch `feat/kai-identity`; the rulebook is `DESIGN.md`), in two modes: the public pages in **Daylight** (a chalk ground) -- a landing page at `/` whose hero is the real 2025 board in the product's dark skin, re-sorting under 0 PPR / Half PPR / PPR, then a rule change drawn out, the arithmetic behind a total, a board slice, one player's weeks and seasons, and email-first sign-up -- and behind sign-in, in **Prime time**, a top bar, the rankings workspace (tiers, positional ranks, find-in-board, ESPN headshots, filters in the URL), the player workspace (cut-out plate, weekly chart, game log, career), ruleset builder; attribution footer on both. `npm run build` + `npm run lint` green. **`npm test`** — `node --test` over the pure libs, the landing's captured data and the two modes' token parity, 71 tests, no dependency, and in CI since 2026-09-28 |

| # | Phase | State |
|---|---|---|
| 0 | Foundation — compose, Boot skeleton, Flyway `V1` | ✅ |
| 1 | Ingestion — nflverse + Sleeper, six-season backfill in 22.8s. Daily pull installed under launchd `f478233` | ✅ |
| 2 | Scoring engine — ruleset model, validator, dot-product evaluator | ✅ |
| 3 | Read API — `JdbcTemplate` reads, `StatKey`-generated SQL, query whitelists | ✅ |
| 3.5 | Close the baseline — k6 at 1/5/10/20 VUs; **the bottleneck is Postgres, not the scorer (88/12)** | ✅ |
| 4 | Vegas in the schema — `V4` widens `games` by 10 columns | ✅ |
| 4.75 | Toolchain recovery — JDK 25 located, enforcer rule, deps current, headless-context bug fixed | ✅ |
| **5** | **Auth + web shell** — Argon2id, JWT, rotating refresh, Bucket4j · Next.js shell | ✅ 5a/5b · 5c · **5d live 2026-09-23** — 10/11 acceptance checks; 4b owed. 4c (three rate-limiter bypasses, fixed in code 2026-09-24) passes since the **2026-09-29 redeploy**. `DEPLOY-STEPS.md` |
| 6 | Projections — `SignalKey`, `ProjectionEngine`, `ExplainedScore`, published MAE | |
| 7 | League import — `LeagueProvider`, ESPN + Sleeper | |
| 8 | Roster tools — optimizer, simulator, trade evaluator, waivers | |
| 9 | Consensus board — FFC ADP + Sleeper rostered% → the market board (members-only since 2026-09-28; it was "the logged-out top 100") | |
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
| `TeamIngestor` | `teams_colors_logos.csv` → `teams`, including historical abbreviations, and since `V6` the ESPN logo URL (https only) |
| `PlayerIngestor` | `players.csv` → `players`; espn/pfr/nfl/esb ids as `jsonb`, and since `V6` `birth_date` — read but never required, and a malformed one is null: display-only data must not stop the pull |
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

#### `query/` — Phase 3 · 14 files

| Class | Does |
|---|---|
| `PlayerQueryRepository` | Every read the API makes, as parameterized JDBC |
| `StatColumns` | Generates the `SELECT` list from `StatKey`; reads a `ResultSet` into `double[]` by name |
| `PlayerSort` · `RankingScope` · `ScoringPosition` | **The three whitelists.** Each resolves a request string to an enum constant or throws |
| `ScoringProfileQueryRepository` | Preset metadata only — never the `rules` column. **Phase 5 grew it `OR user_id = ?`, bound in the query** — a member sees the presets and their own; since 2026-09-29 a logged-out caller never reaches it |
| `ScoringProfileWriteRepository` | Insert / update / delete a user's own profile. Phase 5 |
| `DuplicateProfileNameException` | Two profiles with one name, for one owner |
| `ScorableRow` · `PlayerRow` · `GamelogRow` · `PositionWeekRow` | Row records. `PositionWeekRow` is the career's lean position-wide scan: id, season, week, the 13 stats |
| `Usage` | Attempts, completions, carries, targets — volume, **deliberately not `StatKey`s**. The only place those four columns are named on the read path |
| `InvalidQueryParameterException` | A name that isn't on a whitelist |

#### `api/` — Phase 3 · 19 files

| Class | Does |
|---|---|
| `RankingsController` · `PlayerController` · `ScoringProfileController` | **Nine endpoints** — 6 public `GET` plus, since Phase 5, three profile mutations behind `@PreAuthorize` |
| `RankingsService` | Scores every player-week in Java, aggregates per player, sorts, pages; then looks up the page's ESPN ids by primary key |
| `PlayerService` | Player list, detail, the scored game log, and the **career** — regular-season totals plus season and weekly positional ranks from one position-wide scan |
| `PointsTally` | One player's points, **summed in week order**, and the one ranking comparator (points desc, id asc). Shared by rankings and career, so a career's positional rank equals the board's by construction |
| `Ages` | Age on 1 September of a season — the one rule |
| `ApiExceptionHandler` | RFC 7807 `problem+json` over `ResponseEntityExceptionHandler` |
| `PageResponse` | Generic page envelope. `DEFAULT_SIZE=50`, `MAX_SIZE=200` |
| `RankingRow` · `PlayerSummary` · `PlayerDetail` · `GamelogWeek` · `GamelogResponse` · `CareerResponse` · `CareerSeason` · `CareerWeek` · `ScoringProfileSummary` | Response records |
| `PlayerNotFoundException` | 404 |

#### `auth/` — Phase 5a/5b · 17 files

| Class | Does |
|---|---|
| `SecurityConfig` | **The filter chain, and it is default-deny.** `permitAll` on an explicit short list — since 2026-09-29 only `/api/v1/auth/**` and health, every read included in `authenticated()` — so a new endpoint is private until someone lists it. Also the CORS allowlist and HSTS. The chain bean is `@ConditionalOnWebApplication(SERVLET)` — the headless ingest entrypoint has no `HttpSecurity` and died on it for three days; `@EnableMethodSecurity` stays unconditional on purpose |
| `AuthController` | Register · login · refresh · logout. Access token in the body, refresh token in an `HttpOnly` cookie — the asymmetry is the design, not an inconsistency |
| `AuthDtos` | The codebase's **first request bodies**, which is why §8's `@Valid` row starts mattering here and not in Phase 3 |
| `JwtService` | Issues and verifies the 15-minute HS256 access token. **Pins `Jwts.SIG.HS256`** — `hmacShaKeyFor` otherwise picks the algorithm from the key's length, so the env var would decide it |
| `JwtAuthFilter` | `Authorization: Bearer` → security context. No token passes straight through unauthenticated; the chain decides what that may reach |
| `RefreshTokenService` | Rotation with reuse detection. **`noRollbackFor = InvalidTokenException`** is load-bearing — a plain `@Transactional` undid the family revocation on the way out |
| `RefreshTokenRepository` | The `refresh_tokens` table from `V5`. Hashed at rest, never the token itself |
| `UserRepository` | The `users` table `V1` created and nothing touched until now. `JdbcTemplate`, deliberately — north-star §5a |
| `AuthRateLimitFilter` | Two buckets per IP. **Strict, 5/min**: login, register and every other `/api/v1/auth/**` path, so guessing passwords and enumerating emails share a count — and an endpoint added later is limited on day one. **Loose, 30/min**: refresh and logout, named in an allowlist (since 2026-09-29; page loads spend refreshes). Matches paths the way routing does (`PathPatternRequestMatcher`) — a raw-URI `startsWith` let `/api/v1/%61uth/login` skip it |
| `ForwardedHeaderConfig` | Replaces Boot's `ForwardedHeaderFilter` registration with an **allowlist**: only `X-Forwarded-For/-Proto/-Host`, the three Caddy writes, are believed. `Forwarded` and `X-Forwarded-Prefix` bypassed the limiter through Caddy until 2026-09-24 |
| `RateLimitConfig` | Bucket4j's Redis store, on Lettuce directly — `RedisTemplate` lacks the compare-and-swap Bucket4j needs. Resolved lazily, so a Redis outage costs logins and not the site |
| `AuthProperties` | `@ConfigurationProperties`. **Refuses to start** without a ≥32-byte `JWT_SECRET`; no committed default |
| `Problems` · `ProblemAuthenticationEntryPoint` · `ProblemAccessDeniedHandler` | RFC 7807 for the filter-chain paths, which run before `DispatcherServlet` and so never reach `ApiExceptionHandler` |
| `InvalidTokenException` · `EmailAlreadyRegisteredException` | Absent, expired, forged or replayed token · a duplicate registration |

### Everything else

```
backend/src/main/resources/
  application.yml                  datasource, flyway, ingest config
  db/migration/                    V1 schema · V2 ingestion support · V3 presets · V4 Vegas
                                   columns · V5 auth · V6 birth date + team logo
backend/
  Dockerfile                       multi-stage, Temurin 25, Alpine runtime. arm64 + amd64;
                                   3.82s from docker restart to a 200 on liveness
  .dockerignore
deploy/                            Phase 5d
  compose.prod.yml                 Postgres + Redis + backend + Caddy. restart: unless-stopped is
                                   load-bearing -- @Scheduled needs a live JVM
  Caddyfile                        TLS, and a warning against adding trusted_proxies
  README.md                        the runbook, the OCI firewall trap, the acceptance checks,
                                   §7 redeploying a running stack (and the bind-mount inode trap)
  acceptance.sh                    the §6 checks as one command, run from the laptop; `?` for a
                                   check it cannot run, never PASS
frontend/                          Phase 5c. Next.js 16 App Router, TypeScript 6, Tailwind 4
  src/proxy.ts                     runs before every page: mints the CSP nonce and sets the policy
                                   on the request (Next stamps its scripts from it) and response
  src/lib/csp.ts                   the Content-Security-Policy as one pure, tested function --
                                   and why a nonce, and why none in style-src
  src/lib/apiBase.ts               API_BASE, the one definition api.ts fetches from and the
                                   policy's connect-src allows
  src/lib/memberHint.ts            the fk_member hint: a non-secret "has signed in here" cookie
                                   on www. Skips /auth/refresh for visitors who never signed in,
                                   and lets proxy.ts route before render. A hint, never a gate
  src/lib/api.ts                   the only place the access token lives, and it is memory-only
  src/lib/auth.tsx                 trades the HttpOnly refresh cookie for a token on load
  src/lib/queries.ts               TanStack Query hooks; useProfiles waits for the session
  src/lib/ruleset.ts               the ruleset document + a mirror of RulesetValidator's bounds
  src/lib/board.ts                 what the board derives, never invents: positional rank,
                                   % of leader, tiers (natural breaks over the top 60), and a
                                   per-game quality band against 12-team starter lines
  src/lib/season.ts                currentSeason() -- mirrors IngestProperties.seasonFor, so
                                   no page pins a season literal again
  src/lib/profiles.ts              profileLabel(): presets Standard -> "0 PPR", Full PPR -> "PPR"
                                   (display only; stored names unchanged)
  src/lib/headshot.ts              ESPN headshot URL from the stored espn id -- never stored;
                                   headshotCutoutUrl() the whole 600x436 alpha cut-out
  src/lib/trace.ts                 routePath(): a season's weekly points as a monotone cubic
                                   path -- through every week, no overshoot, broken at a bye
  src/lib/playerStats.ts           the position-aware box-score columns, one definition for
                                   the game log, the career table and the season line
  src/lib/player.ts                age, season to open on, playoff round names, matchup text
  src/lib/boardParams.ts           the board's URL state: parse against whitelists, serialize
  src/lib/boardReturn.ts           sessionStorage note of where the board was left, for back
  src/lib/landing.ts               the landing page's sections (one list for the header and the
                                   page) and safeNext() -- the sign-in redirect, resolved twice
                                   so `/..//evil` cannot come out as a host
  src/lib/useNextParam.ts          ?next= read without useSearchParams (prerendered routes)
  src/lib/heroBoard.ts             the landing hero's board, derived: places, positional ranks
                                   (nothing when a player above is missing), moves, entrance
  src/lib/authErrors.ts            one wording for a failed sign-in or sign-up
  src/app/globals.css              the design tokens (DESIGN.md): Prime time in @theme -- true-black
                                   canvas < surface < well < lift, blue rising with elevation;
                                   ki orange = brand/best/the one action, energy blue = you are
                                   here -- and .daylight (the public pages, chalk) / .primetime
                                   (the product shown on them) redeclaring the same tokens; type
                                   roles as @utility; motion gated on no-preference, including
                                   the landing's one re-sort. Every text pair measured for contrast
  src/components/                  RulesetSwitch (the hero), RankingsBoard, RulesetBuilder,
                                   RateInput, Movement, Attribution, AuthForm, SeasonRoute (a
                                   season drawn as a route over its bars -- the landing's motif)
    shell/                         AppShell, TopBar (the product's bar: wordmark, Rankings,
                                   Scoring, ComingMenu -- a native popover of the six unbuilt
                                   sections, never links -- and AccountButton), Wordmark (logo
                                   mark + the orb-dotted i), MobileNav (<dialog> below md),
                                   nav.ts -- one nav definition. RequireAccount -- the website's
                                   sign-in gate
    landing/                       the public pages, in Daylight: SiteHeader (links scroll to
                                   the landing's sections), EmailStart (email and password side
                                   by side, the password waiting for step two), HeroPlate (the
                                   real 2025 board's top 8 in Prime time, re-sorting under 0 PPR
                                   / Half / PPR, played once on load) + HeroReadout (its docked
                                   lower-third: McCaffrey's season and the drawn route) +
                                   Crossfade (a figure turning from 0 PPR to PPR in the entrance)
                                   + heroImage (no likeness until one is licensed), RuleSwing (top
                                   six, PPR vs 0 PPR), PointsReceipt (one season taken apart),
                                   BoardSlice (ranks 6-16 across the first tier break), CareerLine
                                   (a player's finished seasons), ComingNext, StillPlate (the
                                   board, still, beside sign-in and register), previewData
                                   (captured 2025 data, with the GETs that make them; npm test
                                   holds it to its own totals, and the receipt to the API's)
    ui/                            Icon (inline SVG), SegmentedControl (native radios; track /
                                   bare / pill), SeasonSelect, SearchField, StatusMessage +
                                   Skeleton, buttons.ts (the shared button looks)
    rankings/                      RankingsWorkspace (the /rankings page),
                                   FilterBar, PlayerRow, TierHeader, PositionBadge, PlayerAvatar
                                   (headshot with a monogram fallback)
    player/                        PlayerWorkspace (the /players/[id] page), PlayerIdentity
                                   (the plate), PlayerCutout (ESPN's whole cut-out, initials on
                                   a 404), WeeklyChart (dependency-free columns), StatTable
                                   (game log and career share it)
  src/app/                         (site): / /login /register -- the site header
                                   (app): /rankings /players/[id] /profiles -- the top bar, and
                                   behind RequireAccount
  tests/lib.test.ts                node --test over the pure libs (npm test)
backend/src/test/
  java/com/fantasykai/             18 test classes + ApiFixture, Presets
  resources/application.properties test JWT secret, and Redis pointed at redis.invalid
                                   so a test that needs it has to declare a container
  resources/nflverse/              6 fixture files — real rows, never invented
docs/
  north-star.md                    scope, sequencing, product decisions
  fantasy-platform-handoff.md      engineering rationale (§1 and §11 superseded)
  perf/baseline.md                 the Phase 3.5 measurement
  map.md                           this file
perf/rankings.js                   k6 script — pins season=2025 on purpose
scripts/                           session-check.sh (the session-start truth check) ·
                                   package.sh + lib/jar-state.sh (one definition of "is the
                                   jar current", shared by three callers) · ingest-once.sh ·
                                   launchd plist · perf-explain.sh · db-restore.sh (full
                                   dump; refuses a non-empty target and compares row counts)
.claude/                           settings.json's SessionStart hook · commands/review-pass.md
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
| Know what is true right now | `./scripts/session-check.sh` — it runs itself at session start. A `drift` row means a doc is wrong, including this one |
| Understand any of this without the jargon | [`orientation.md`](orientation.md) — glossary, real-vs-planned, how a request actually works |
| Finish a piece of work | [`../CLAUDE.md`](../CLAUDE.md), "Definition of done" · or type `/review-pass` |
| Run it | [`../README.md`](../README.md) for setup · [`../CLAUDE.md`](../CLAUDE.md) for the full command list |
| Deploy it | [`../deploy/README.md`](../deploy/README.md) — the runbook, the firewall trap, and the acceptance checks. `deploy/compose.prod.yml` + `Caddyfile`; move the data with `scripts/db-restore.sh`. north-star §5d |
| Add a screen | a route under `frontend/src/app/`, a hook in `src/lib/queries.ts`, a type in `src/lib/types.ts` |
| Change how a request is authenticated | `frontend/src/lib/api.ts` — one fetch wrapper, and the only thing that holds the access token |
| Understand why a build died on the JDK | `pom.xml`'s enforcer rule says it in the error. Background in [`../CLAUDE.md`](../CLAUDE.md) |

---

## 5. Known gaps and open risks

Audited 2026-09-08. Each verified against the source, and tagged with the phase that
closes it. Nothing here is a surprise to the docs unless marked **new**.

### Closes with Phase 5c/5d

| Risk | Detail |
|---|---|
| **Nothing is deployed** | The end of Phase 5 is "a site you can log into", and HTTPS/HSTS cannot be satisfied locally. **Host changed 2026-09-14: Oracle Cloud Always Free VM + Caddy + Vercel**, because the Fly/Neon/Upstash free tiers this was written against stopped existing — north-star §5d carries the survey and its sources. `deploy/compose.prod.yml`, `deploy/Caddyfile` and `deploy/README.md` now exist and the **whole stack was run and probed on the dev Mac**: all four containers healthy, 3.82s from restart to a 200 on liveness, and 6 of the 10 acceptance checks green. Verifying it found two real defects — HSTS configured but never emitted, and a CORS allowlist with no mechanism to reach production. Waiting on a domain and the accounts |
| ~~**The attribution footer is still owed**~~ | **Closed.** `frontend/src/components/Attribution.tsx`, on every page: in the root layout until 2026-09-28, in both route-group layouts since — nflverse (CC BY 4.0), Sleeper and FFC. Owed since Phase 0 |
| **Nothing watches the pipeline** | `ingestFreshness` was DOWN and correct for three days in September 2026 with no process alive to be asked. The indicator is not the gap; **a host for it is**. Closed by the Fly deploy, whose `auto_stop_machines = false` is load-bearing for the same reason. **Partially mitigated 2026-09-14**: `session-check.sh` reads `ingest_runs` and `launchctl` directly, so a stopped pipeline is visible without a running JVM — and the launchd exit code surfaces it ~36h before the freshness threshold does |

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
| ~~The ingest depends on a laptop being awake~~ | **Closed 2026-09-29.** Production's `IngestScheduler` has run at 06:00 ET since 2026-09-23 and the laptop's launchd job was uninstalled — it had failed on 6 of its last 8 mornings, the three examined with `pmset` inside a Power Nap DarkWake (inferred, not proven). The local database is a mirror refreshed by hand; `session-check.sh` treats it as one |
| No shared Testcontainers base class | Ten cached Spring contexts, seven with an embedded Tomcat. **Teardown measured at ~35s on 2026-09-14 — the largest single term in the build, more than compilation and all 132 tests together.** It sits just over Surefire's 30s fork-exit timeout, which is why the same commit builds in 31s some days and 58s others. **Back to urgent**: it was downgraded on a ~9s estimate that direct measurement contradicts. See [`../CLAUDE.md`](../CLAUDE.md) |
| Hikari is at Spring Boot's default 10 connections | 10 × ~39 ms occupancy ≈ the 256 req/s ceiling. Phase 11 must not mistake raising it for a fix |

### Follow-ups from the rankings workspace pass — **new** 2026-09-28

Found while shipping the shell, the current-season fix, position hues and per-game quality
colour. Recorded, deliberately not fixed in that milestone.

| Follow-up | Detail |
|---|---|
| ~~Local 2026 data is stale~~ | **Refilled 2026-09-28 14:12** by a one-shot ingest from the `feat/player-workspace` jar (which also applied `V6`): weeks 1–3 now hold 16 / 16 / 15 games, 1,117 / 1,106 / 1,048 rows — week 3's Monday game had not been played. The launchd job itself still last exited 1 |
| Short absences go unjudged | `QUALIFYING_SHARE = 0.5` in `lib/board.ts` leaves Joe Burrow's 2025 (8 of 17 games, 16.8/game) uncoloured. Half the board's games is a judgement, not a measurement |
| Starter lines assume a 12-team 1/2/3/1 league | `STARTERS` in `lib/board.ts`. Phase 7 league import supplies real roster slots; until then green means "a 12-team starter" and nothing more |
| No quality colour on a phone's Season board | Per game is the secondary column there, hidden below `md`. Phones see the colour only on the Per-game board |
| ~~`/` and `/rankings` prerender the build-time season~~ | **Closed 2026-09-28** by the player-workspace branch: the board reads its filters from the URL inside `<Suspense>`, so it renders in the browser and no build-time season is ever prerendered |
| Two hue pairs sit close | `q-poor` shares RB's hue (lower saturation, numbers only); `pos-wr` sits 11° from `energy`. Separated by role and placement, not by hue — check with real users before calling it settled |

### Follow-ups from the player workspace — **new** 2026-09-28

| Follow-up | Detail |
|---|---|
| Hotlinked ESPN images | Headshots and team logos load from `a.espncdn.com`. Nothing is copied or stored, and the footer says where they come from — but no doc decides image rights. An owner call for `north-star.md`; the monogram fallback means dropping them costs one line in `lib/headshot.ts` |
| No historical team or position | `players.team_id` and `players.position` are current only. The board's Team column is today's team on every season's board (its header says so), and a past season is ranked under today's position — on the board and in `/career` alike, so the two agree, and are equally wrong for a player who moved |
| Byes and missed games look the same | Only played games have rows, so the chart's gap and the log's missing week cannot say which. The schedule (`games`) could, per team, for a player who was not traded |
| "Home" is the designated home side | nflverse's `location` (neutral site) is not stored |
| A player page costs a position-wide scan | One per (player, ruleset); measured at 23.6–26.0 ms for WR on 2026-09-28 (`perf/baseline.md`). Phase 11's cache covers it with the ranking |
| ~~Deploy order~~ | **Closed 2026-09-29, the wrong way round.** The frontend reached production first, twice (PR #33 at 01:58 UTC, PR #34 at 15:38 UTC), and player pages showed the career error state for 19h17m until the backend redeployed at 21:15 UTC. `deploy/README.md` §7 is the procedure now; the order problem itself — nothing stops a frontend merge from outrunning its backend — is not solved |
| ~~Production data needs one ingest~~ | **Closed 2026-09-29** by a one-shot ingest right after the redeploy: `birth_date` on 24,802 players, `logo_url` on 36 of 36 teams |

### Follow-ups from the landing page and the account gate — **new** 2026-09-28

| Follow-up | Detail |
|---|---|
| ~~The API is still public~~ | **Closed 2026-09-29** (`feat/members-only-api`, owner decision): every read needs a token, proven by `ReadApiTests.everyReadNeedsAnAccount` across all 6 GETs; the signed-in app reads 200 everywhere (measured in a browser). Production is public until the backend redeploys |
| The hero wants a real player, and has none | `components/landing/heroImage.ts` is `null` until an image clears copyright (an agency's editorial licence does not cover promotion) **and** the player's consent. Since 2026-09-30 the hero shows no one at all: the real 2025 board (`HeroPlate`) stands in, and a licensed cut-out would stand at the edge of its lower-third (`HeroReadout`). An owner call, next to the ESPN one above |
| ~~Every anonymous page load spends an auth token~~ | **Closed 2026-09-29, twice over.** A visitor with no `fk_member` hint makes no `/auth/refresh` call at all (measured: 0 on the landing page), and a member's refreshes spend their own 30/min bucket, not login's 5/min (measured: 8 refreshes, then a login still judged on its password) |
| ~~A member can glimpse the landing page~~ | **Closed 2026-09-29.** `proxy.ts` reads the hint and answers `/` with a 307 to `/rankings` before anything renders (measured: the landing form never mounted). A never-signed-in visitor's deep link likewise goes straight to sign-in. One-time cost: a member who signed in before this shipped has no hint, and signs in once more |
| ~~Two tabs refreshing at once sign the member out~~ | **Closed 2026-09-29** (`fix/refresh-rotation-races`). Pre-existing, found testing the hint: two refreshes presenting one cookie revoked the whole family — the race path silently — and so did a reload that aborted a rotation. Now: the browser serializes refreshes across tabs (Web Locks) and sends them `keepalive`; the server exchanges a token consumed ≤10 s ago once more if its family has not moved on, and logs every revocation. Measured in a browser: 8 rapid reloads → one clean chain, 0 grace reuses needed; two uncoordinated concurrent refreshes → both 200, 1 grace reuse logged; the same pair under the lock → serialized, none. **The trade:** a thief replaying within 10 s, before the owner refreshes again, is let through |
| Daylight on a browser without `color-mix` | Tailwind compiles an opacity-modified colour (`bg-ink/60`) to a literal Prime time value, and uses the variable only inside `@supports (color-mix)`. A browser older than about 2023 therefore shows Prime time's value for those colours on the public pages. The site header was moved to a token (`--color-veil`); the week chart's average line and faded axis labels still take the fallback. Minor, recorded in `DESIGN.md`, not fixed |
| The preview does not update | `previewData.ts` is captured 2025 data on purpose (the season is over, so none of it goes stale). Re-capture with the GETs in its header if the scoring presets ever change. It carries a third copy of the PPR rates for the receipt, and `npm test` fails if they stop adding up to the captured total -- a check that moved there from the component once every page rendered per request |
| Sign-up on a Vercel preview fails | The landing page renders there, because its data is static, but the API's CORS allowlist is the real domain only, the same as every other call |
| Magic-link sign-in | Not built. It needs an email provider, SPF/DKIM at Porkbun, and a one-time token table. The landing form asks for the email first and the password second, which keeps north-star §2's "an email and a password" true |

### Follow-ups from the security headers — **new** 2026-09-29

| Follow-up | Detail |
|---|---|
| Every page renders per request | The nonce has to be minted per request, so no page is static HTML any more and none is cached at Vercel's edge. Measured locally (`next start`, 30 requests): p50 **0.7–1.1 ms static → 2.3–3.8 ms** per request. Production's baseline before the change: p50 **338–401 ms**, cache HITs, handshakes included. Production after the change is measured once it deploys |
| Injected CSS still applies | `style-src` keeps `'unsafe-inline'`, because React's `style=` attributes (`PlayerRow`, `WeeklyChart`) cannot carry a nonce — and a nonce there would switch `'unsafe-inline'` off. Script is the threat the policy is for |
| A script that runs can still navigate away | CSP limits where `fetch`, images and forms may go; it has no say over `location = …` with a token in the URL. The nonce is what stops a script from running in the first place |
| The Vercel toolbar is gone from previews | It injects a script without the nonce. Accepted |
| SRI is not a static alternative | Tried once, 2026-09-29: `experimental.sri` with `script-src 'self'` keeps pages static, but App Router pages carry inline RSC payload scripts (`self.__next_f.push`) that no integrity hash covers. On a page with no redirect the app did not hydrate. One page (`/`) still ran its client redirect with no violation reported — not explained |
| F11 still owed | `lib/apiBase.ts` keeps an empty `NEXT_PUBLIC_API_URL` as same-origin, and the policy mirrors it rather than hiding it; the build should refuse it |

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
| [`../CLAUDE.md`](../CLAUDE.md) | **Operational memory** — invariants, measured numbers, the traps in the source data, the commands, the definition of done |
| [`orientation.md`](orientation.md) | **Explanation.** What this is in plain English, real-vs-planned, and a glossary of every term the other four assume. No facts of its own either |
| **this file** | **Navigation.** Where things are, what state they're in, where to look next. No facts of its own |

The working agreement, which governs all five: **measure before asserting, and prove a
constraint by trying to violate it.** `./scripts/session-check.sh` is that agreement made
executable — it re-derives the claims rather than trusting them, and reports `?` rather than
`ok` for anything it could not actually check.
