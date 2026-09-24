---
paths:
  - "backend/**"
---

# Backend rules — Java, Spring, JDBC, tests

Loaded when Claude reads a file under `backend/`. Each line is a rule this codebase learned the hard
way; the full story is in `docs/traps.md` — grep it for the quoted phrase.

- **`ResultSet.wasNull()` reports on the last column *read*.** Read the flag into a local right after
  the column it describes; argument evaluation order will otherwise ask about the wrong column. → "wasNull"
- **`@Transactional` rolls back what you did before throwing.** A write that must survive the
  exception (revoking a token family on replay) needs `noRollbackFor`. → "@Transactional"
- **Never put `@Validated` on a controller** — it turns a 400 into a 500. Spring 6.1+ validates
  constrained parameters itself. → "@Validated"
- **pgjdbc maps `smallint` to `Integer`, not `Short`** — assert with ints. → "pgjdbc"
- **`JdbcTemplate` reads jsonb `?` as a bind placeholder** — use `external_ids ->> 'x' IS NOT NULL`. → "jsonb"
- **`players.full_name` is not unique** (832 shared names) — never key a lookup on a name. → "full_name"
- **Round with `BigDecimal` `HALF_UP`, never `Math.round(x * 100) / 100.0`** — and only at the API
  boundary; `ScoringEngine` never rounds. → "Math.round"
- **Hash what a ruleset *does*, not the JSON someone wrote** — the cache key is computed over the
  compiled arrays in `ResolvedRuleset`; verify against `RulesetHashTests`. → "Hash what a ruleset"
- **`Keys.hmacShaKeyFor` picks the JWT algorithm from the key's length** — keep `Jwts.SIG.HS256` pinned. → "hmacShaKeyFor"
- **`Argon2PasswordEncoder` needs BouncyCastle** (`bcprov-jdk18on`) and fails on first login, not at startup. → "BouncyCastle"
- **A `@Bean` taking `HttpSecurity` must be `@ConditionalOnWebApplication(type = SERVLET)` — on the
  bean, not the class** — or the headless ingest entrypoint dies. `OneShotContextTests` guards it. → "HttpSecurity"
- **A health indicator that goes DOWN for "degraded" restarts machines** — probes use
  `/actuator/health/liveness`. → "health indicator"
- **Forwarded headers are an allowlist** — the app believes only `X-Forwarded-For/-Proto/-Host`
  (`ForwardedHeaderConfig`), and `X-Forwarded-For` is still Caddy's job to overwrite. Match request
  paths with `PathPatternRequestMatcher`, never `getRequestURI().startsWith(...)` — the raw URI is
  still percent-encoded. → "auth rate limiter"
- **Tests: a class that needs Redis declares its own container**, or it silently uses the dev Redis. → "Redis container"
- **Tests: state shared between test methods is a false-green machine** — `AuthRateLimitTests`
  flushes Redis before each test because every test shares one bucket. A new test must **fail before
  the fix**, for the reason you expect, or it is testing something else.
- **After a public signature moves, run `./mvnw clean compile`** — the incremental compiler reports
  BUILD SUCCESS with callers uncompiled. → "incremental compiler"
- **`./mvnw verify` at ~32s vs ~58s is a Surefire fork-exit timeout, not Docker** — never attribute
  build time without measuring it. → "Surefire"

---

The two pipelines below were moved verbatim from CLAUDE.md on 2026-09-24.

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
