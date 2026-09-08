# fantasy-kai — product north star

**Owner:** Prabhnoor Bal · **Written:** September 7, 2026 · **Status:** active

This file is the source of truth for **scope, sequencing and product decisions**.
[`fantasy-platform-handoff.md`](fantasy-platform-handoff.md) remains the source of truth for
**engineering rationale** — §5 schema, §6 scoring, §8 security, §9 performance. Where this file and
the handoff's §1 or §11 disagree, this file wins; those two sections are marked superseded.

It exists for one reason: to make going off the rails **detectable** rather than gradual. Read §4
before adding anything.

---

## 1. What this is

A free fantasy football tool that tells you **why**, not just what — and computes it for *your*
league, not a generic 12-team PPR.

Flock Fantasy is the shape we're aiming at: league sync across Sleeper/ESPN/Yahoo, a trade
calculator that reads league size and starting slots, rankings, projections. It also has a free tier
and a paid tier, and its rankings are a **creator consensus** — paid analysts. We have no analysts
and we are not going to pretend otherwise.

**This directly overturns handoff §0 point 2, "You are not building Flock Fantasy," and the
disagreement is recorded rather than resolved quietly.** §0's reasoning — that cloning a funded
team's surface area yields a broad, shallow project — is not wrong; it is accepted as the live risk
of this document. The answer is sequencing, not optimism: §10 ships one narrow slice at a time, each
usable on its own, and §9 lists what we are refusing. §0's *other* warning, about not passing
someone else's expert rankings off as consensus, is not overturned — it is promoted to a hard
invariant in §4.

Three claims that have to stay true or the product has no reason to exist:

1. **Free with no tier.** Not freemium. No paywall, no ads, no upsell screen.
2. **Explained, not asserted.** Every ranked number can be expanded into the parts that produced it.
   "RB4 because 24.8 implied team total, 22% target share, plus schedule weeks 15–17" beats "RB4."
3. **Yours, not generic.** Rankings, waivers and trades are computed against your scoring, your
   roster slots, your roster, and your remaining schedule.

Claim 2 is the wedge. Every ranking site gives you a number; almost none of them will show you the
arithmetic. Claims 1 and 3 are table stakes that a solo project can actually deliver because the
scoring engine already computes on demand against an arbitrary ruleset.

---

## 2. Who sees what

| | Logged out | Account |
|---|---|---|
| Top-100 consensus board | ✅ | ✅ |
| Everything else — projections, your rankings, waivers, trade calculator, league sync, custom profiles | ❌ | ✅ |

An account is an email and a password. Nothing else is collected.

Enforced in the Spring Security filter chain as `permitAll` on an explicit, short list and
`authenticated()` on everything else — a **default-deny** list, never a default-allow one. A new
endpoint is private until someone deliberately adds it to the list.

The consequence is a resequencing: **auth stops being late-phase polish and becomes load-bearing
infrastructure.** It moves to Phase 5, ahead of every personalized feature, because every
personalized feature depends on it.

---

## 3. The one idea, extended

The existing invariant is *store raw stat lines, never fantasy points; compute points on demand
against a ruleset*. Everything new is the same move, one level up. This is what makes the product
one system instead of a feature list.

### 3.1 Project the stat line, never the points

A projection row carries the **same 13 `StatKey` columns** as `player_game_stats`, and is scored by
the **existing, unmodified `ScoringEngine`**.

```java
double projected = ScoringEngine.score(projectedStatLine, yourResolvedRuleset);
```

A projected *point total* is correct for exactly one ruleset — the identical mistake as storing
`fantasy_points`, which is the thing this architecture exists to avoid. Projecting the stat line
means projections need **zero new scoring code**, are automatically correct for every custom league,
and reuse `StatColumns.SELECT_LIST` verbatim.

### 3.2 Store signal components, never a blended rank

ADP, implied team total, target share, rostered %, snap trend are stored **raw**. The blend is a
`RankingRecipe` — versioned, validated, canonically hashed, exactly like `Ruleset`. A stored rank
freezes the recipe and makes every weight change a backfill.

### 3.3 `SignalKey` mirrors `StatKey`

One enum that is simultaneously the recipe's **validation allowlist**, the **`double[]` index** for
the blend dot-product, and the **signals-table column name**. Same three-jobs trick as `StatKey`, one
layer up: a recipe cannot name a signal that does not exist, the blend is a dot product rather than a
hash lookup per signal, and the query is generated from the enum rather than maintained beside it.

---

## 4. Invariants — do not break these

New, on top of the ones in [`CLAUDE.md`](../CLAUDE.md).

| Invariant | Why | Expires |
|---|---|---|
| **Project the stat line, never the points** | A projected point value is right for exactly one ruleset. Same error as storing `fantasy_points`, one layer up. §3.1 | never |
| **Store signal components, never a blended rank** | A rank is a function of (signals × recipe × league). Storing it freezes the recipe and turns a weight change into a backfill. §3.2 | never |
| **Every ranked number carries its parts** — `ExplainedScore`, not `double` | "Why is he ranked here" *is* the product. If explanation is a UI afterthought it will end up absent, or worse, wrong. | never |
| **Every external signal is optional; a ranking computes without it** | $0 budget, unlicensed sources, and ESPN *will* break mid-season. A missing signal must degrade the explanation, never 500 the endpoint. | never |
| **A trade is valued in expected wins, never a per-player number** | A context-free player value is precisely the thing every basic calculator gets wrong. §7 | never |
| **Third-party league data is fetched as the user, with the user's own credentials, and never redistributed or aggregated** | This is the line that separates legitimate league import from scraping someone's product. It is what makes ESPN import defensible under handoff §2's warning. | never |
| **No expert rankings ingested, ever.** Consensus means **market** consensus | Handoff §0. Real drafts (FFC), real roster rates (Sleeper), real betting lines (nflverse). Legal, ethical, and it survives being asked how it works. | never |
| **No paywall, no ads, no sportsbook links, no affiliate** | The product promise — and it keeps Apple guideline 5.3 (real-money gaming) out of scope entirely. | never |
| **`player_game_stats` stays unindexed past its primary key** | Existing invariant, now at much higher risk: every new phase adds compute on top of an uncaptured baseline. | Phase 11 |

---

## 5. Data sources

Probed live on 2026-09-07. Status is what the endpoint actually returned, not what the docs claim.

| Source | Status | Terms | Role |
|---|---|---|---|
| nflverse `schedules/games.csv` | ✅ 46 columns incl. `spread_line`, `total_line`, `away/home_moneyline`, `over/under_odds`, `home/away_score`, `roof`, `surface`, `temp`, `wind`. **112 of 272 2026 games have lines** — they land ~a week ahead. | CC BY 4.0 | **The Vegas layer.** Already downloaded on the daily cron. |
| nflverse `stats_player_week`, `players`, `snap_counts`, `teams` | ✅ in production since Phase 1 | CC BY 4.0 | Backbone — unchanged |
| Sleeper `GET /v1/league/{id}` | ✅ returns `scoring_settings`, `roster_positions`, `settings` | Free, **non-commercial only** | Second league provider |
| Sleeper `GET /players/nfl/research/regular/{season}/{week}` | ✅ `{"11533":{"owned":99.7,"started":98.8}, …}` | same | In-season market signal |
| Sleeper `GET /v1/players/nfl/trending/add` | ✅ live | same | Waiver momentum |
| Fantasy Football Calculator ADP API | ✅ `Gibbs 1.4 · Bijan 2.4 · Puka 2.8 · Chase 3.8` — 6,075 drafts, 12-team PPR, window 2026-08-31→09-07 | **Free incl. commercial**, attribution requested | Draft consensus (Aug 2027) |
| ESPN `lm-api-reads.fantasy.espn.com` | ✅ HTTP 200 unauthenticated for league defaults; private leagues need `SWID` + `espn_s2` | Undocumented, no license | **League import only — never a ranking input.** See §4. |
| Polymarket `gamma-api.polymarket.com` | ✅ public, no auth — but the top live NFL event is *"Tush Push banned for 2026?"* | Free | Team-level season priors. Low weight, optional, honest about depth. |
| Yahoo `fantasysports.yahooapis.com` | ❌ 401 — requires OAuth2 (free developer app) | Authorized | Third league provider, later |
| Underdog | ❌ 404 / 426 Upgrade Required | Gated | **Skip.** |
| The Odds API — NFL **player props** | ❌ Business tier, **$99/mo** | — | **Out.** Props get derived, not bought. §6 |
| FantasyPros ECR | not attempted | Paid / partner-gated | **Never.** Handoff §0. |

**The ESPN split is deliberate and it is the whole ethical argument.** Fetching *your* league with
*your* cookies is you acting as yourself, and the data never leaves your account. Ingesting ESPN's
`draftRanksByRankType` into a public ranking would be redistributing their editorial product. The
first is allowed. The second is forbidden by §4.

**Attribution is owed and still unpaid.** nflverse (CC BY 4.0) and FFC both require it, and the site
footer has been outstanding since Phase 0. It ships in Phase 5.

---

## 6. How a ranking is built

```
projected_stat[k] = team_volume(team, wk) × player_share(player, k) × efficiency(player, k) × availability
                    └──── Vegas ────┘       └── snap% / tgt share ──┘  └─ regressed y/tgt ─┘   └ status ┘

implied_team_total = total_line/2 ± spread_line/2     # derived on read. NEVER a stored column.
game_script        = spread drives the pass/run mix   # trailing teams throw
points             = ScoringEngine.score(projected StatLine, your ResolvedRuleset)   # unchanged code
```

Each factor and where it comes from:

| Factor | Source | Notes |
|---|---|---|
| `team_volume` | `games.total_line`, `games.spread_line` | Implied team total → implied plays and pass/run split. This is where Vegas **earns its place** rather than being decoration. |
| `player_share` | `snap_pct` (99.9% coverage), `targets`, `rush_att` | Exponentially weighted over recent weeks. Recent form > season average. |
| `efficiency` | `rec_yd/targets`, `rush_yd/rush_att` | **Regressed to the positional mean** — small samples lie. |
| `availability` | nflverse `players.status`, Sleeper status | Multiplier, not a filter. |

`implied_team_total` is derived on read for the same reason fantasy points are: it is
`total_line/2 ± spread_line/2` and storing it would be storing a computed value that a corrected
line invalidates.

### Credibility requirement

A **backtest harness** that projects week N using only weeks < N, across 2024–25, and publishes MAE
and bias by position to `docs/perf/projection-accuracy.md`. Same discipline as `NflverseOracleTests`:
pin the number, don't assert quality. A projection model with no published error bar is an opinion.

The model above is a hypothesis until that file exists. Every weight in it is a candidate for the
backtest to reject.

---

## 7. The trade calculator

> **A trade's value is not a number attached to a player. It is the change in your expected wins** —
> given your roster, your starting slots, your scoring, and your remaining schedule.

1. Project every player's stat line for each remaining week → score under **your** ruleset (§6)
2. `LineupOptimizer` solves the slot assignment against your league's `roster_positions`, flex and
   superflex included
3. Your projected weekly total vs. your **actual scheduled opponent's** projected total → win
   probability from a normal model, using per-position residual σ backed out of the six seasons of
   `player_game_stats` already loaded. Means alone are not enough — variance is why a boom/bust
   starter and a steady one are not interchangeable.
4. Σ win probabilities over remaining weeks = expected wins. Repeat with the post-trade rosters.
   **ΔEW, reported for both sides.**

Positional need, bye weeks, schedule strength and bench-vs-starter value all **fall out** of this
rather than being bolted on as fudge factors:

- A 4th good RB when you start 2 barely moves ΔEW. A first good WR moves it a lot.
- A bye-week collision is a week where the optimizer has to reach further down your bench.
- The output can honestly say *"+0.6 wins for you, +0.2 for them"* — a trade can be good for both,
  and a calculator that can't say so is lying by construction.

**Waivers are the same engine**: `ΔEW(add X, drop your worst bench player)`, ranked for your roster,
cross-referenced against Sleeper `owned%` for whether X is plausibly available in a league that size.

This endpoint is **CPU-bound by construction** — per-week lineup solves across a roster across
remaining weeks. That is a feature for Phase 11: it gives the ruleset-hash cache a second, harder
endpoint to generalize to.

---

## 8. Known gaps — written down, not discovered later

Per the working agreement: report design gaps you are *not* fixing.

- **K and DST break the lineup optimizer.** The target ESPN league almost certainly starts both, and
  handoff §6 defers them. Planned fix: model each as a positional constant with historical variance,
  flagged `unmodeled` in the explanation, rather than silently omitted from ΔEW. The usual defence —
  that K/DST week-to-week scoring is nearly uncorrelated — is *widely believed and not yet measured
  here*. Six seasons are already loaded; **measure it before relying on it.**
- **`ScoringPosition` is a 4-constant whitelist** (QB/RB/WR/TE). League import introduces
  FLEX/SUPERFLEX/K/DST slot types that it cannot currently express.
- **ESPN player mapping is already solved and nobody noticed.** `PlayerIngestor` packs `espn` into
  `players.external_ids`, so ESPN rosters resolve via `external_ids->>'espn'`. Needs a second
  expression index alongside the existing `idx_players_sleeper_id`. `players` is not the frozen
  table — only `player_game_stats` is.
- **Apple Developer costs $99/yr**, which contradicts the "$0 forever" budget. It is the one
  unavoidable cost of the App Store requirement. Decide now, not at submission.
- **Sleeper is licensed non-commercial.** A free app with no ads and no IAP is fine. Monetizing ever
  — including ads — requires a license conversation first.
- **Nothing is on the classpath yet** for auth (no Spring Security, no JWT), caching (no Redis
  client, no `@EnableCaching`), or rate limiting (no Bucket4j) — despite Redis running in
  `docker-compose.yml` since Phase 0. These are greenfield additions, not extensions.
- **Adding a data source costs more than it should.** There is no `Ingestor` interface; each source
  is a `@Component` hardcoded into `IngestService`'s constructor and its fixed call sequence, and
  `NflverseClient` can only reach nflverse release URLs. `StatIngestor`'s `List<Field>` +
  `buildUpsert()` pattern is the template worth copying. Extract the interface when the second new
  source lands, not the first.
- ~~The k6 baseline is still owed~~ — **captured 2026-09-07**, and it contradicted handoff §9's
  premise. See §10. The open item it leaves behind: `application.yml` sets no Hikari config, so the
  pool is Spring Boot's default of **10 connections**, which is itself the throughput ceiling at
  ~256 req/s. Phase 11 must not mistake raising it for a fix.
  See Phase 3.5.

---

## 9. Explicitly not building

| Not building | Why |
|---|---|
| DFS optimizer | Different product, different data, zero overlap with season-long league tools |
| Best ball | Same |
| Mock draft simulator | Needs the draft board (Phase 9) to exist first, and it's a next-August problem |
| Dynasty / keeper values | Requires multi-year projections. Revisit after §6 has a published error bar. |
| Creator or expert content | We have no analysts. §4 forbids ingesting anyone else's. |
| Anything real-money | Product promise, and it keeps Apple 5.3 out of scope |
| Android | After iOS ships and is used |
| Push notifications | Meaningless before the Expo app (Phase 10) |
| K/DST **scoring rulesets** | Still deferred per handoff §6. §8 covers the lineup-optimizer workaround. |

---

## 10. Roadmap

Supersedes handoff §11 from Phase 4 onward. Phases 0–3 keep their numbers and their commits.

**From Phase 5 on, every phase ships its own slice of UI.** There is no monolithic frontend phase —
a product you can't open is not one you'll use, and this only gets built if it gets used.

| # | Phase | Ships | Status |
|---|---|---|---|
| 0–3 | Foundation · Ingestion · Scoring · Read API | see handoff §11 | ✅ |
| **3.5** | Close the baseline | Measured at 1/5/10/20 VUs, 2026-09-07. p95 **21.4 → 125.0 ms**, throughput saturates ~256 req/s, and **the bottleneck is Postgres at 88% of CPU, not the Java scorer** — which contradicts handoff §9. | ✅ |
| **4** | **Vegas in the schema** | `V4` widens `games` with scores + betting + weather columns; ~10 lines in `GameIngestor`'s existing mapper; re-run backfill. No new HTTP source. ~1 day. **Brief below.** | ⬅ **next** |
| 5 | Auth + web shell | Handoff §8 in full — Argon2id, JWT, rotating refresh, Bucket4j. First write endpoints (`POST/PUT/DELETE /scoring-profiles`). Next.js 15: login, rankings table, player detail, profile switcher, public landing page. Attribution footer. **End of phase = a deployed site you can log into.** | |
| 6 | Projections | `SignalKey`, `player_week_projection`, `ProjectionEngine`, `ExplainedScore`, backtest + published MAE. The heart of "valid reasons for ranking." | |
| 7 | League import | `LeagueProvider` interface. ESPN first (cookie paste, encrypted at rest), **Sleeper in the same phase** to prove the seam is real. ESPN `mSettings.scoringItems` → `Ruleset`, auto-creating your profile. Manual ruleset builder as the fallback for when ESPN breaks — because it will. | |
| 8 | Roster tools | `LineupOptimizer`, `SeasonSimulator`, `TradeEvaluator`, `WaiverBoard`. §7 made real. | |
| 9 | Consensus board | FFC ADP ingest + `player_adp` + Sleeper `owned%`/trending → the logged-out top 100. In-season it's rest-of-season; **August 2027 it becomes the draft board** with no rework. | |
| 10 | iOS (Expo) | ~Nov. Same REST API. Native navigation + push — a webview wrapper fails Apple guideline 4.2 (minimum functionality). | |
| 11 | Perf pass | Cache → matview → indexes, measuring after each. **Order survives Phase 3.5's finding, expected magnitudes do not** — the matview and index attack the dominant cost (the scan), so they should beat §9's prediction rather than trail it. Sample Postgres CPU, not just the JVM's. Plus the trade simulator as a second endpoint for the ruleset-hash cache. | |

### What Phase 3.5 found — and why it was worth doing first

The baseline was captured before any new phase added compute, which is the only reason it means
anything. It also **disproved the hypothesis the whole performance story was built on.**

Handoff §9 asserts the bottleneck is Java recomputation. Measured at 20 VUs: **Postgres 5.3 cores
against the JVM's 0.73 — 88/12, 20.7 ms versus 2.9 ms per request.** §9 is right that this isn't
disk (4,044 buffer hits, zero reads) and wrong about which CPU is busy. Three sequential scans at
256 req/s cost about seven times the dot-product over the 6,037 rows that survive them.

Two things follow. Phase 11's *order* is unchanged — a cache hit skips the scan and the scoring
both, so it stays first. But its *expected magnitudes* invert: the matview and the indexes are
no longer supporting evidence for a story about recomputation, they are the fix for the actual
dominant cost. And handoff §12 Q4's stock answer is now wrong as written; the corrected version is
in the doc, and it is a better answer than the original because it describes a hypothesis that got
tested and failed rather than one that got confirmed.

### Phase 4 — ready to start

**The first action is a measurement, not a migration.** The working agreement — the habit that caught
the fractional sacks and the NULL-defeated unique constraint — says pull the real value ranges out of
`games.csv` *before* choosing column types:

| Column | What the data does | Therefore |
|---|---|---|
| `spread_line`, `total_line` | carry halves — `3.5`, `44.5` | `NUMERIC(4,1)`. **`SMALLINT` would round 3.5 → 4 and silently corrupt every line** — the `def_sacks` lesson exactly |
| `temp`, `wind` | blank for dome games; `temp` goes negative | nullable `SMALLINT`, and blank must land as `NULL`, not `0`. `CsvValues` already treats `""`/`NA` as null |
| `away_moneyline`, `home_moneyline` | signed, and exceed ±32,767 on heavy favourites | `INT`, not `SMALLINT` |
| `roof`, `surface` | enumerate the real distinct values before sizing | don't guess the `VARCHAR` width |
| `home_score`, `away_score`, `result`, `total` | absent for unplayed games | all nullable |

Then `V4__games_betting_and_results.sql` widens `games`, and `GameIngestor`'s mapper and `UPSERT`
list grow to match — it already downloads `schedules/games.csv` and reads **8 of its 46 columns**,
so this adds no HTTP source and no new client. `implied_team_total` is **derived on read**
(`total_line/2 ± spread_line/2`), never a column — §4.

Tests extend the existing 5-row `backend/src/test/resources/nflverse/games.csv` fixture: a fractional
spread survives the round trip, and a dome game's blank `temp` reads back `NULL` rather than zero.

**Acceptance:** `SELECT count(*) FROM games WHERE season = 2026 AND spread_line IS NOT NULL` returns
**112** of 272, matching the source as probed on 2026-09-07. Lines land roughly a week ahead of
kickoff, so this number grows all season and a re-run must not regress it.

### Why the draft board is Phase 9 and not Phase 1

The season opens **September 10, 2026** — three days from this file's date. Draft ADP is frozen for
the next ~18 weeks. Shipping the consensus draft board first would mean shipping a museum piece and
having no reason to open the site until August. The in-season stack is what gets used in week 3.
Phase 9's board becomes the draft board next August with no rework, because the ADP ingest and the
`player_adp` table don't care which month it is.

---

## Appendix — reference links

- nflverse data releases — https://github.com/nflverse/nflverse-data/releases
- nflverse `games.csv` (betting columns) — https://github.com/nflverse/nfldata
- Sleeper API docs — https://docs.sleeper.com/
- Fantasy Football Calculator ADP API — https://help.fantasyfootballcalculator.com/article/42-adp-rest-api
- Polymarket Gamma API — https://docs.polymarket.com/
- Apple App Review Guidelines — https://developer.apple.com/app-store/review/guidelines/
- ESPN v3 endpoint notes (community gist) — https://gist.github.com/nntrn/ee26cb2a0716de0947a0a4e9a157bc1c
