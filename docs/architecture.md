# Architecture

How Fantasy Kai is built and the rules that keep it correct. Product scope and the roadmap live in
[`north-star.md`](north-star.md); the original engineering rationale (schema, scoring, security,
performance) is in [`fantasy-platform-handoff.md`](fantasy-platform-handoff.md).

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

## Code map

```
backend/src/main/java/com/fantasykai/
  ingest/      nflverse + Sleeper pipeline: CSV clients, idempotent upserts, the daily scheduler,
               the ingest-freshness health indicator
  scoring/     ruleset model, validator, compiled ResolvedRuleset, ScoringEngine
  query/       JdbcTemplate reads, StatKey-generated SQL, the sort/filter whitelists
  api/         REST controllers, DTOs, RFC 7807 errors, scoring-profile writes
  auth/        security filter chain, JWT, rotating refresh tokens, Argon2id, Redis rate limiting
backend/src/main/resources/db/migration/     Flyway migrations, V1 onward
backend/src/test/java/com/fantasykai/projection/
               the projection backtest: test-scoped research code, never in the jar
frontend/src/  Next.js App Router: app/(site) public pages, app/(app) the signed-in workspace,
               components/, lib/ (api.ts holds the only access token, in memory)
deploy/        production Compose stack, Caddyfile, runbook, acceptance checks
perf/          k6 load script
scripts/       session check, packaging, one-shot ingest, backtest, database restore
```

Every package's classes, the two pipelines and the known gaps are in [`map.md`](map.md).

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
subject — **in the query, not the service**. Until 2026-09-29 a logged-out caller bound `null`,
which matches nothing, and saw the four presets and no more; since then the chain turns them away
first, and the filter decides what a member sees.

**The chain gates endpoints; the query gates rows.** Every read and every mutation is
`authenticated()` since 2026-09-29 (reads were `permitAll` before), and mutations add
`@PreAuthorize`. Which *profile* you may score
against is decided by the ownership filter, never by the URL — and
`ScoringProfiles`' compile cache carries its entry's owner, because the filter only
runs on a miss and a warm cache would otherwise hand one user's ruleset to the next.
A profile you do not own is **404, not 403**: 403 confirms the id exists.

## Measured numbers — do not re-derive or estimate these

From the loaded database, 2020–2026. **Re-measured 2026-09-21** after a four-day ingest outage was
found and the missing week refilled — the 2020–2025 rows below are unchanged and historical, the
2026 rows move every day the pull runs. **Week 2 is 15 games, not 16: the Monday night game had not
been played when this was measured.** That is the same "a week arrives in pieces" behaviour the
daily pull exists for, not a gap.

| | |
|---|---|
| `player_game_stats` rows stored | **112,319** (112,450 read; 131 dropped for blank `player_id`) |
| …plus 2026 week 1, first partial pull 2026-09-12 | **134** (135 read; 1 dropped, same blank-`player_id` cause) → **112,453** |
| …week 1 complete, 2026-09-14 | **1,040** (1,041 read; 1 dropped) → **113,359** total |
| …2026 weeks 1–2, re-measured **2026-09-21** | week 1 **1,117** / 16 games · week 2 **1,043** / 15 games → **114,479** total |
| Stat rows, QB/RB/WR/TE | 37,261 |
| Distinct players, all positions | 4,232 |
| Distinct players, QB/RB/WR/TE | **1,298** across seven seasons — **578–633 in any one 2020–2025 season** |
| `snap_pct` coverage | 99.9% (114,397 / 114,479) |
| Sleeper ids attached | 883 of 25,066 players |
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

**Player identity, measured 2026-09-28** (what the player workspace's ages and logos -- and its
headshots, off since 2026-10-20 for want of permission --
rest on — the source files and the loaded database, not a guess):

| | |
|---|---|
| QB/RB/WR/TE with a stat line carrying an ESPN id | **1,296 of 1,298** before that day's 14:12 ingest, **1,306 of 1,308** after — every one all digits. The two without: Kevin White, Rod Williams |
| Same, in `players.csv` (active since 2020) | espn id **1,897** · nflverse `headshot` URL **1,841** · birth date **1,900** — of 1,903 |
| `players.csv` birth dates | 24,833 rows · 32 blank · **0 malformed** → **24,801 of 25,066** stored (the rest are `StatIngestor`'s placeholder players). Read but never *required*: a bad or missing one costs an age on screen, never the pull |
| Team logos | **36 of 36** rows https; longest URL 54 chars. `LA`/`STL` → `lar.png`, `WAS` → `wsh.png`: not derivable from the abbreviation |
| ESPN headshot, full · resized 64px square | ~237 KB · ~6 KB. A missing id is a **404**, so `<img onError>` is a reliable fallback |
| Stat rows whose team is neither side of the game | **0** of 115,590 |
| QB/RB/WR/TE player-seasons with playoff games and no regular season | **7** — one player has no regular season at all. `/career`'s `seasonsPlayed` exists for them |

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
