# Development

Commands, tooling, known platform risks, and the traps that have already cost time. Setup from a
fresh clone is in the [README](../README.md#running-locally); how a change is reviewed is in
[`CONTRIBUTING.md`](../CONTRIBUTING.md).

## Check the state of a checkout first

```bash
./scripts/session-check.sh        # ~3s, read-only
```

It runs automatically at session start (the `SessionStart` hook in `.claude/settings.json`), and
by hand any time. It re-derives from the tree and the database what these docs only *claim*:
toolchain, container health, git and PR state, Flyway checksums against the applied migrations,
row counts, ingest freshness, jar staleness, the landing hero's capture week against the
schedule, and the file/test/endpoint counts written in prose.

**Where the check and a doc disagree, the check wins and the doc is what gets fixed** — before
the session moves on to anything else. A `drift` row is not noise; it is a doc that has started
lying, which in a project whose whole argument is *measure before asserting* is the most
expensive kind of bug there is.

**A check that cannot run prints `?`, never `ok`.** Docker down, `gh` unauthenticated, no
`python3` — each reports unknown with its reason. This is `CsvValues.shortValue` one layer up:
the moment "could not read it" and "read it, it was fine" print the same thing, the check is
worse than no check.

It does **not** run the test suite, build the frontend, or check a deployment. Those are the
definition of done at the other end of the session — see [`../CONTRIBUTING.md`](../CONTRIBUTING.md).

## Commands

```bash
# What is actually true right now -- toolchain, containers, git, Flyway checksums,
# row counts, ingest freshness, jar staleness, and the counts the docs claim.
# Runs itself at session start; exits 1 on any FAIL or drift.
./scripts/session-check.sh
./scripts/session-check.sh --no-network   # skip the two gh calls

docker compose up -d                      # Postgres on :5433 (not 5432), Redis on :6379

# Java 25 or nothing compiles; pom.xml's enforcer rule names the JDK when it is wrong.
# macOS: select it explicitly, and check the version java PRINTS, not the exit code.
export JAVA_HOME=$(/usr/libexec/java_home -v 25) && java -version

# The app needs JWT_SECRET or it refuses to start. `source .env` first, or:
JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run
cd backend && ./mvnw -B verify            # needs Docker — Testcontainers boots a real PG 16

# psql
docker compose exec postgres psql -U fantasykai -d fantasykai

# one-shot historical backfill
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true

# one-shot current-season pull (the same pull production runs daily at 06:00 ET)
./scripts/ingest-once.sh

# build the ingest jar AND record what it was built from. Use this, not a bare
# `./mvnw package`: without the .srcsha sidecar the staleness guard falls back to
# comparing mtimes, which a git checkout is enough to defeat.
./scripts/package.sh                      # add --with-tests to run the suite too

# optional, macOS: schedule the local pull with launchd (idempotent; needs a current jar).
# Production runs its own scheduler; this only keeps a local mirror fresh.
./scripts/install-ingest.sh
launchctl list | grep fantasykai        # loaded?
tail -f logs/ingest.log

# Phase 6 projection backtest (needs Docker Postgres only; ~6 s). validate makes every choice on
# 2024 and writes frozen-config.json; test refuses to run until that file is committed.
./scripts/backtest.sh validate | test | prospective

# production: redeploy procedure in deploy/README.md §7, then the acceptance checks
VM=<server-ip> ./deploy/acceptance.sh   # 1, 2, 4a, 4c, 5, 6, 7, 9, 10 against the live site
                                        # (9 needs PROD_TOKEN + LOCAL_TOKEN: reads need an account)

# is the pipeline fresh?  (anonymous sees status only -- show-details is when-authorized)
curl -s localhost:8080/actuator/health | jq .components.ingestFreshness

# web shell (Phase 5c). Node 24 -- .nvmrc pins it; Node 20 went EOL 2026-04-30.
cd frontend && nvm use && npm ci
npm run dev                               # :3000, expects the API on :8080
npm run lint && npm test && npm run build # what CI runs (npm test = node --test, no dependency)

# the deploy image, built and run against the compose Postgres
cd backend && docker build -t fantasykai-backend:local .
```

**Postgres is on 5433** because a Homebrew `postgresql@16` launchd service owns 5432 on the dev Mac and wins the connection. Symptom when this bites: `role "fantasykai" does not exist`.

## Why the pull is daily

2026 season opens **Sept 10**. The 2026 schedule is loaded (272 games). **Week 1 arrived in two
pieces, and that is the daily pull's entire justification**: on 2026-09-12, with only the opener
played, the source carried 135 rows and 134 stored. By 2026-09-14 the full slate was published —
1,041 read, **1,040 stored**. Before week 1 published at all, those runs correctly recorded
`SKIPPED`. A weekly pull would have held a 13%-complete week 1 for days.

## Spring Boot 3.5 is past open-source end of life

**Spring Boot 3.5's OSS support ended 2026-06-30, and 3.5.16 — released 2026-06-25 — was the last
OSS release of the line.** Commercial support runs to 2032; free patches do not exist. The docs
and north-star §10 row 11.5 both called 3.5.16 "current" until 2026-09-21. It is the head of its
line **because the line ended**, which is the opposite of the thing that word implies.

**How it was missed is the lesson.** On 2026-09-18 a Dependabot PR took the backend to Boot 4.1.1
with its own CI red, and `main` could not parse its own POM for three days. Reverting it on
2026-09-21 was correct — an unbuildable tree cannot be deployed, and north-star §10 row 11.5 had
already scoped Boot 4 to its own phase. But the revert was argued entirely from *"does it build"*
and never from *"is the thing we are reverting to still supported"*. Both questions were live; only
one got asked.

**What the pin buys, and what it does not.** `pom.xml` now carries
`<tomcat.version>10.1.59</tomcat.version>`, ahead of the parent, because Boot 3.5.16 manages Tomcat
**10.1.55** and — reading Apache's own affected ranges — 10.1.55 sits inside **every advisory fixed
in 10.1.56, 10.1.57 and 10.1.59**: 19 CVEs, four rated Important. Most are very likely unreachable
here (Spring Security rather than a Tomcat realm; no RewriteValve, DIGEST, FORM auth, WebSocket or
clustering), but "very likely unreachable" is a judgement about today's code and the pin costs one
line. **Verify a pin took rather than trusting that it was declared:**

```bash
unzip -l backend/target/backend-0.0.1-SNAPSHOT.jar | grep tomcat-embed-core   # must say 10.1.59
```

**The pin is a stopgap and the clock keeps running.** Every month on 3.5.x is a month where the
next Spring or Tomcat CVE has no free upgrade path — and pinning only works for dependencies whose
version the parent exposes as a property. Phase 11.5 is now a **security obligation with a date
attached**, not a nicety after the perf pass.

**Java 25 since 2026-09-10.** One property in `pom.xml` plus CI; no source file changed and the
suite passed on 25 unmodified. Spring Boot 3.5.16 documents Java **17 up to and including 25**, so
this is the top of the supported range rather than past it. The reason is dates, not features —
25 is the current LTS; handoff §4 carries the argument. The `pom.xml` enforcer rule names the JDK
when the wrong one is on the path. **`docs/perf/baseline.md` stays on Temurin 21.0.11**: it records
what a measurement ran on, not what the stack is today, and Phase 11 re-captures on 25 before it
compares anything.

## Traps — each of these cost real time

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
- **Weeks run to 22, not 18 — and a playoff game is never `'POST'`.** `games.season_type` holds
  `REG` or the round: `WC`, `DIV`, `CON`, `SB` (measured 2026-09-28). This entry said `'POST'`
  until then, the API test fixture seeded a fictional `"POST"`, and the old player page tagged
  playoff weeks with `seasonType === "POST"` — so it never tagged one. Test for `REG`, never for
  a postseason value. Weeks also overlap: 2020's wild card is week 18. Rankings join `games` and
  filter to `REG`, because fantasy leagues do not score the playoffs and `last4` would otherwise
  mean "the postseason". The game log deliberately does *not* filter — it is a record of what a
  player did — and `/career` does, so the workspace's season numbers are the board's.
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
  signed-in user could not see their own ruleset until a hard reload. `useProfiles` was gated on
  `status !== "restoring"`, and since every read needs a token (2026-09-29) on `status ===
  "signed-in"`; every other query renders only under `RequireAccount`.
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
- **The auth rate limiter is forgeable at the application layer, and only Caddy stops it.**
  `AuthRateLimitFilter` keys its 5/min bucket on `X-Forwarded-For`, falling back to
  `getRemoteAddr()`. `deploy/README.md` acceptance check 4a claimed six logins with a **varying
  forged** header still yield `429`, and that it had been "run locally on 2026-09-14" and passed.
  **It does not pass. A test that asserts it returns 401, not 429** — six forged addresses, six
  fresh buckets. The claim was false and had survived in the doc because nothing executed it.
  The mechanism is also not the one the code appears to use: `server.forward-headers-strategy:
  framework` installs Spring's `ForwardedHeaderFilter` ahead of the security chain, and its wrapper
  extends `ForwardedHeaderRemovingRequest` — so by the time the filter runs, `getHeader(...)` is
  already `null` and `getRemoteAddr()` has been **rewritten from the forged value**. The filter's
  own `X-Forwarded-For` branch is dead code while that property is set, and deleting it would
  change nothing: the trust lives in the property, not the filter. What makes this safe today is
  entirely external and both halves are load-bearing — Caddy with `trusted_proxies` unset discards
  an incoming value and writes the real peer, and `compose.prod.yml` gives the backend `expose`
  with no published port. Publish that port, add a CDN, proxy through Cloudflare's orange cloud, or
  set `trusted_proxies`, and the limit becomes decoration. `AuthRateLimitTests` now pins the **true**
  behaviour rather than the desired one, so it fails the moment the trust model changes — which is
  exactly when the deploy docs need rereading.
  **And "only Caddy stops it" was true for one header out of seven.** On 2026-09-24 three bypasses
  went straight through Caddy as configured for production, each reproduced against a local copy of
  that stack (production itself was not probed): RFC 7239 `Forwarded` (Spring reads it *before* `X-Forwarded-For`; Caddy passed it
  untouched), `X-Forwarded-Prefix` (moved the URI outside the limiter's `startsWith` check), and a
  percent-encoded path `/api/v1/%61uth/login` (raw URI vs decoded routing — no proxy can fix that).
  Fixed in code in two layers, each proven alone — **both deployed 2026-09-29, and check 4c returns
  `429` for all three on production** (`deploy/acceptance.sh`). Caddy strips the four forwarded headers it does not
  write, and the application both ignores them (`ForwardedHeaderConfig`) and matches the path the
  way routing does (`PathPatternRequestMatcher`). **The lesson: a trust boundary is a list of
  everything that crosses it, not the one thing you thought about.** `X-Forwarded-For` alone
  remains Caddy's job.
- **A test that passes is not a test that can fail.** The first career-rank test compared the
  career's positional rank with the board's under every profile and passed — and it still passed
  with the career ranking by *per game* instead of by points, because in every fixture position
  the points leader was also the per-game leader. Found by mutating the comparator and watching
  20 of 20 stay green. The fix was data, not assertions: a one-game receiver who leads on per-game
  and trails on total. Mutate the line a test exists to protect before believing it.
- **Summing in arrival order makes a total depend on the scan.** A ranking added each player's
  weeks in whatever order the sequential scan returned them, and floating-point addition is not
  associative — `0.1 + 0.2 + 0.3 != 0.3 + 0.2 + 0.1`. Upserts and synchronized scans move that
  order, so two players level on screen could swap between requests, and a career rank could
  disagree with the board's. `PointsTally` sums in week order; `PointsTallyTests` pins it with the
  textbook triple. Java-only — the §9 SQL is unchanged.
- **An intercepting route catches the page it intercepts.** The player overlay is
  `@modal/(.)players/[id]`, so a soft navigation to `/players/[id]` *from the full player page
  itself* -- `router.replace` to change `?tab=` or `?profileId=` -- was intercepted too, and a
  second copy of the player opened as an overlay over the page. Every section link and ruleset
  change on a shared link or after a refresh did it; the overlay, which every review opened from
  the board, never showed it. On a page the query is now written with `history.replaceState`, and
  previous/next load a page. **The state argument must be `null`:** passing `history.state` (which
  carries Next's tree) makes Next skip syncing `useSearchParams`, so the URL changed and the view
  kept the old ruleset. Found by a scripted review counting `dialog[open]`, not by reading the code.
- **`useSearchParams` on a prerendered route needs a `<Suspense>` boundary, and `next dev` hides
  it.** Dev renders on demand, so a missing boundary works there and fails `npm run build`. `/`
  and `/rankings` wrap the board; its fallback copies the header, control-row and row heights so
  nothing moves when the board lands.
- **A `next dev` started before a route move makes `npm run build` fail on routes that no longer
  exist.** The dev server writes `.next/dev/types/validator.ts`, `tsconfig` includes it, and the
  build type-checks it: after moving `app/page.tsx` into `app/(site)/`, the build died on
  `Cannot find module '../../../src/app/page.js'` while the code was correct. CI never sees it,
  because CI never ran `next dev`. Stop the stale dev server and delete `.next/dev`.
- **A single-file bind mount pins an inode, not a path — so `git pull` does not reach a running
  container.** `compose.prod.yml` mounts `./Caddyfile:/etc/caddy/Caddyfile:ro`. `git pull` replaces
  the file with a new inode and the container keeps reading the old one: on 2026-09-29, after the
  pull, the host's Caddyfile was inode 552587 with four `header_up` lines and the container's was
  552551 with **none**. `caddy reload` would have reloaded the stale config, succeeded, and left
  the three 4c bypasses open behind a step that looked done. Recreate the container
  (`up -d --force-recreate caddy`) and verify by reading the file **inside** it —
  `deploy/README.md` §7. Predicted from reading `compose.prod.yml` while planning the deploy — and
  then measured before reloading, which is the part that counts: the prediction alone would have
  been one more claim nobody executed.
- **A nonce in `style-src` switches `'unsafe-inline'` off.** Next.js's own CSP example puts the
  nonce in both `script-src` and `style-src`. Copied as written, every React `style={}` attribute
  here (`PlayerRow`'s bars, `WeeklyChart`'s columns) would silently stop applying, because a
  browser that sees a nonce or hash in a directive ignores `'unsafe-inline'` in it — and style
  attributes cannot carry a nonce. `lib/csp.ts` keeps the nonce out of `style-src` and a test
  pins it. Relatedly, **a CSP header is only half of it in Next.js**: the policy must be set on
  the *request* too, because that is where Next finds the nonce to stamp on its scripts. On the
  response alone the browser enforces a nonce no script carries.
- **A one-use token meets a browser that runs in parallel.** Rotation with reuse detection treats
  a second presentation of a consumed token as theft and revokes the family. A browser presents one
  twice all the time: two tabs opened together share the cookie jar but not memory, and a reload
  that lands after the server rotated but before the new cookie was stored leaves the old one in
  the jar. Both signed the member out, and the concurrent case did it through the `consume()` race
  branch, which revoked without logging — so production would never have said why. The class's
  own javadoc called it "the rare race". Reproduced with two curls on one cookie before anything
  changed; `AuthTests` now pins the reload and the two-tab case, and a replay after the window or
  after the owner moved on still revokes.
- **A request that never finished is not an answer.** The first version of the member hint cleared
  itself whenever a session restore returned false — and `refreshOnce()` returned false for a 401
  *and* for a fetch the browser aborted because the member reloaded. Eight fast reloads left a
  healthy token family on the server and a signed-out member in the browser. Only the server's own
  401 retires the hint now (`refreshOutcome()` in `lib/memberHint.ts`); a 429, a 5xx or an abort
  says nothing about the session. Found by reloading in a browser, not by the tests, which had
  pinned the happy path. The same experiment exposed the rotation race in map.md §5.
