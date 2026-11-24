# Projections Slice 5 — plan

**Status: approved by the owner on 2026-11-18 (D1 to D5 as recommended; PR #98). Built: 5.0 (the HTTP
characterization tests; confirming the 14,906-week parity command of §6.2 is still open) and 5.1 in PR #100 (merged),
5.2 to 5.7 in PR #101 (2026-11-23). This plan was written on 2026-11-17: where a section says what `main` does
"today", or what "will" be measured or pinned, that is the day's snapshot, and the notes marked "built as" and
`docs/projections-v1-architecture.md` §9 "as built" say what was built. The switch-on work of §7 has not
started: the job is off, and the R2 production backfill, the R3 rehearsal and the R4 config line are owed.**
Written 2026-11-17 against `main` at `5a05dd6` (production runs the same commit).
It extends [`projections-v1-architecture.md`](projections-v1-architecture.md) (approved 2026-10-09) and does
not replace it; where they differ, §3 below asks for a decision.

*Sizes are rough: **S** under half a day, **M** one to two days, **L** three or more.*

## 0. In short

- **Slice 5 is already half built.** `GET /api/v1/projections` and `GET /api/v1/players/{id}/projection`
  exist on `main` (`ProjectionController`, from the #75 integration) and answer today. No frontend code calls either, so
  their JSON shape can change freely **now** and not after Slice 6 builds on it. That is the case for doing
  the contract first.
- **What is owed** is listed in the controller's own Javadoc and in the 2026-10-28 handoff: the board's
  run metadata and exclusion counts, a target week that knows when there is none, one explanation line per
  bonus (today a single remainder), the preset comparison the player panel needs, and HTTP-level tests for
  §13's "done when" row (only a few exist).
- **No migration is proposed** (decision D1 recommends reading the target week from `TargetWeekResolver` at
  request time). Slice 5 touches no migration, no job and no frontend. Two small touches outside the API, both
  behaviour-neutral (built as 5.3 and 5.4): the two ingestors' `SOURCE` names became public, so the as-of query reads
  the strings the pulls record; and the engine's threshold comparison moved into `Bonus.reachedBy`, which the
  explanation shares, with the arithmetic unchanged.
- **Switching the job on is a separate step** with its own prerequisites (§7). One is already measured: production
  has appearance rows for 2026 only. One is newly found: the production backfill has no way to exit
  (`BackfillRunner` never ends the JVM).
- **Five decisions are needed from you** (§3).

## 1. Where `main` stands against the approved contract

| Contract item (arch §) | On `main` today | Gap |
|---|---|---|
| The two endpoints (§9, §13) | Both exist: `ProjectionController` `board()` and `player()` | none |
| Board envelope (§9): `state, season, week, source, dataThrough, generatedAt, availabilityAsOf, excluded{…}, page` | `Board(state, source, page)` — three fields. `season`, `week` and `generatedAt` are repeated on every row | **season, week, dataThrough, generatedAt, availabilityAsOf and `excluded` are missing at the top level** |
| `source{id,label,summary}` (§9) | `ProjectionSource(id, label, description)` | the field is named `description`; no measured error (§11's popover needs it) |
| Target week (§8, §9) | `TARGET_WEEK`: the latest `(season, week)` that has a stored row for the provider | **no `NO_UPCOMING_WEEK`**; a week with no rows (job off, failed, or nothing to project) is indistinguishable from "never ran". The controller's Javadoc says the same: *"the target is the latest stored week, not a persisted run's target"* |
| Board state | `READY`, `STALE` (every game of the stored week kicked off over 48 h ago), `UNAVAILABLE` | `NO_UPCOMING_WEEK` (after week 18, §8) absent |
| Player states (§9): `PROJECTED, BYE, NO_HISTORY, NOT_ROSTERED, NO_UPCOMING_WEEK` | `PROJECTED, STALE, UNAVAILABLE, BYE, NO_HISTORY, NOT_ROSTERED` (`missingReason`) | `NO_UPCOMING_WEEK` absent; `STALE` and `UNAVAILABLE` are additions the contract should name |
| `ExplainedScore` that sums to `points` (§9) | `ExplainedScore.of`: one `Part(stat, value, rate, points)` per stat, plus **one** `bonusPoints` remainder (`total − linear`) | **"each bonus" is not explained**: a member sees "+3.0 bonus" and not *which* threshold the line crossed |
| Preset strip "0 PPR 9.8 · Half 11.9 · PPR 14.0" (§11) | absent | the player panel needs it (D2) |
| Order: points desc, then player id, on the **unrounded** total (§10) | `Comparator.comparingDouble(Row::rawPoints).reversed().thenComparingLong(Row::playerId)`; `rawPoints` is `@JsonIgnore` | none (fixed in S0, pinned by `ProjectionOrderingTests`) |
| Availability decides the board, never the raw line | `row.availability().ranked()` filter; the line is stored whatever the status | none in code; the filter is **not** pinned by an HTTP test |
| Members-only; profile ownership 404; whitelists (§9) | the default-deny chain, `profiles.byId(profileId, userId)`, `ScoringPosition.resolve` | inherited; **not pinned for these routes** (see below) |

**What the tests pin today** (read from the files, not assumed). `ProjectionOrderingTests` (1 test: sort before
paging, raw value kept out of JSON). `ProjectionScoringTests` (3). `ExplainedScoreTests` (3: total is the
engine's, parts add up, a bonus is the remainder). `ProjectionJobTests` (22) and `ProjectionSchemaTests` (14)
cover the job and the table. At the HTTP level, `RankingsTests` lines 448–461 check one player's projection
under two presets, the board for profile 2, and **anonymous `/projections` → 401** (not
`/players/{id}/projection`). `ReadApiTests.everyReadNeedsAnAccount` and `QuerySafetyTests` mention neither
route. So these §13 conditions have **no test**: another member's profile → 404 on a warm cache, query-safety on
the projection parameters, an Out player not ranked, and every non-`PROJECTED` state.

## 2. Scope

**In (Slice 5):**
1. The board envelope (§4), including `excluded{unavailable, noHistory, bye}`.
2. A target week derived at request time, with `NO_UPCOMING_WEEK` and an honest `STALE` (D1).
3. One explanation line per bonus, so parts and bonuses sum to the total.
4. The preset comparison on the player response (D2).
5. `source.summary` and the measured error, from one place (D3).
6. The HTTP-level tests of §13's row, each mutation-checked (§6.1).
7. The controller split §12 already names (`ProjectionsService` and the response records out of the controller).
8. Docs: arch §9 and §13 refreshed, `docs/map.md`, the `session-check.sh` baselines.

**Out, on purpose:** the board's "Next week" mode and the player panel (**Slice 6**); the live-accuracy report and
the freshness alert (**Slice 7**); **switching the job on** (§7); any model change (decision C stands); a `week` or
`season` parameter (V1 is next week only); trades; any migration, ingest or frontend change.

## 3. Decisions needed

| # | Question | Options | Recommendation |
|---|---|---|---|
| **D1** | How does the API know which week it is, and that there is none? | **(a)** call `TargetWeekResolver.resolve(season, now)` per request (the job's own rule, one place); **(b)** a new `V12` table `projection_run` written by the job | **(a).** No migration, no job change, and it is the request-time truth: if the resolver says week 11 and there are no rows for it, the state is `UNAVAILABLE`; if it says none, `NO_UPCOMING_WEEK`; if the rows are for an older week, `STALE`. (b) buys one thing — a record of a run that wrote zero rows — and `ingest_runs` already holds one row per run (`fantasykai.projections`). Cost of (a): I expected about three small queries per request; measured (`docs/perf/target-week-resolver-2026-11-18.md`), the resolver is ~105 ms warm on the local mirror (123 candidate games), so it is cached for 60 s (`CurrentTargetWeek`) and not asked per request. Not yet measured on production (R3) |
| **D2** | Where does the "same line, different value" strip come from? | **(a)** the API returns the three presets' points on the player response; **(b)** the browser derives them from `parts` | **(a).** (b) puts a second scoring path in TypeScript, which breaks the one invariant this product is built on. (a) uses the unmodified `ScoringEngine`, so the strip is bit-identical to the number under the member's own ruleset by construction (and by test) |
| **D3** | How is the error disclosed? | `source.typicalError` = the measured PPR MAE, with its population and season; the popover text says "about 5.5" (§11) | Store **three** measured values beside the label — 2024 **5.480** (tuning season), 2025 **5.512** (test, n = 3,807), 2026 weeks 1–4 **5.688** (n = 896) — and let the copy say "about 5.5" only while all three stay inside 5.5 ± 0.2. The number lives in one place, pinned by `ProjectionSourceTests` to the committed report |
| **D4** | When is the job switched on, relative to the UI? | **(a)** as soon as §7's prerequisites are met, before Slice 6; **(b)** only when Slice 6 can show it | **(a).** The endpoints are members-only and nothing calls them, and every week the job is off is a week of live-accuracy record lost: the next target week is 11 (week 10 still has a game outstanding) and the regular season ends at week 18. Cost: rows accrue before the UI exists, and the first row freezes `weighted-average-v1`'s meaning (§7) |
| **D5** | How is the production appearance backfill run? | **(a)** from the laptop through an SSH tunnel with the existing `BackfillRunner`; **(b)** add a `backfill-once` path that exits, then run it from the image like `ingest-once` | **(a)** for the single run it is needed for: no new code. **(b)** only if you want it repeatable. Either one is rehearsed on a restored copy first |

**Follow-ups raised after approval (2026-11-23), not decided here:**

- **D3: which population does "about 5.5" describe?** The figure is over the research's P (a fixed top-224-by-opportunity
  group who played). Over every player the board ranks (P-all) the same baseline reads about 4.5 (4.509 in 2024,
  4.470 in 2025), and counting players who then sat as zeros (P0) 5.8 to 5.9 (5.845, 5.887). It is also a *PPR*
  figure (2024: 4.579 under 0 PPR, 5.003 under Half). The API now says exactly which P it is, but the owner decides
  what the popover headlines before Slice 6 prints it; Slice 7's live accuracy will measure whatever the board
  ranks, and the two should not be left to disagree unexplained.
- **D2 as built is three presets, not four.** TE Premium is not on the strip (§4's note). Amending is one name.
- **`excluded` has no bucket for a rostered player who has history and no line** (the job has not run since a roster
  change, or his game kicked off before its first run). His page says `UNAVAILABLE`, and he is in no count. In the
  first week after switch-on (R5) this will be everyone on an early game.

## 4. The contract (additive; nothing here exists as a consumer yet)

```jsonc
// GET /api/v1/projections?profileId=&position=&q=&page=&size=
{
  "state": "READY",                    // READY | STALE | UNAVAILABLE | NO_UPCOMING_WEEK
  "season": 2026, "week": 11,          // the resolver's target (null when NO_UPCOMING_WEEK)
  "source": { "id": "weighted-average-v1", "label": "Fantasy-Kai Baseline",
              "summary": "A weighted average of recent games ...",
              "typicalError": { "metric": "PPR MAE", "value": 5.5,
                                "population": "each week's most-used skill players (top 32 QB, 64 RB, 96 WR, 32 TE by opportunity) who played",
                                "measured": [ {"season":2024,"mae":5.480,"n":3808}, {"season":2025,"mae":5.512,"n":3807},
                                              {"season":2026,"weeks":"1-4","mae":5.688,"n":896} ] } },
  // built as 5.6. The population is the research's P, spelled out: the first draft said "players who appeared",
  // which is not the board's population (the same baseline reads ~4.5 over every player with history who played).
  // The three rows are different dataset versions (2026's P holds 33 zero-stat appearances of 896).
  "dataThrough": { "season": 2026, "week": 10 },        // latest game any row was built from
  "generatedAt": "2026-11-18T11:00:12Z",                // newest row; null if none
  "availabilityAsOf": "2026-11-18T11:00:09Z",           // the older of the last successful roster and injury pulls
  "excluded": { "unavailable": 31, "noHistory": 12, "bye": 74 },   // respect `position`, ignore `q`
  "page": { "content": [ /* rows */ ], "page": 0, "size": 50, "total": 412, "totalPages": 9 }
}
// a row = today's Row, with `bonusPoints` replaced by:
"parts":   [ { "stat": "rec", "value": 5.1, "rate": 1.0, "points": 5.1 }, ... ],
"bonuses": [ { "stat": "rec_yd", "gte": 100, "value": 61.4, "points": 3.0, "triggered": false } ]
//   invariant: sum(parts.points) + sum(bonuses where triggered .points) == total, then rounded once
//   (built as 5.4: a bonus is listed when the line has some of its stat or it was paid -- the rule `parts`
//   follows -- so a running back carries no passing-yards line; the new type is `BonusLine`, since
//   `scoring.Bonus` is the rule record)
// GET /api/v1/players/{id}/projection?profileId=
{ "state": "PROJECTED | STALE | BYE | NO_HISTORY | NOT_ROSTERED | NO_UPCOMING_WEEK | UNAVAILABLE",
  "source": { ... }, "projection": { /* row, or null */ },
  "presets": [ { "profileId": 1, "name": "Standard", "points": 9.8 }, ... ]   // D2; null unless PROJECTED/STALE
  // built as 5.5: three entries, as D2 says -- Standard, Half PPR, Full PPR. TE Premium, the fourth system
  // preset, is not on the strip: it equals Full PPR for every position but TE, and a member who scores under
  // it has the number as projection.points. (Amending D2 to four is one name in ProjectionsService.STRIP_PRESETS.)
}
```

**D1(a) as built (5.2).** The resolver's query took **about 105 ms warm** in `EXPLAIN ANALYZE` on the 2026-11-18 local
mirror (102 to 108 ms over five runs; 143 ms on the next and 196 ms on the first, which still read 11 pages from disk;
122,755 stat rows, 123 candidate games, 108,270 buffer hits: it walks `player_game_stats`' primary-key index once per
candidate game, and that table must not get another index before Phase 11; evidence in
`docs/perf/target-week-resolver-2026-11-18.md`). It was **not measured on production**. It scales with the games left
in the season (about twice as much in week 1, an extrapolation), and the resolver's own Javadoc said a request path
must never call it. D1(a) stands, with the call behind `CurrentTargetWeek`, a per-process cache keyed by season with a
60-second time-to-live (`fantasykai.projections.target-cache-seconds`, `0` = ask every time). No migration, no job
change. A week rolls over at most a minute after the ingest lands its last stat row.

State rules, with the resolver as the single authority (D1a): no weeks left → `NO_UPCOMING_WEEK`; target week
has rows → `READY`, or `STALE` if every game of it kicked off at least 48 h ago; target week has no rows →
`UNAVAILABLE`; rows exist only for an older week of the same season → `STALE`. The 48-hour constant moves from the controller to
`TargetWeekResolver`, where the job's matching 48-hour rule already lives.

## 5. Files touched

| File | Change |
|---|---|
| `backend/src/main/java/com/fantasykai/api/ProjectionController.java` | shrinks to routing; the two methods delegate |
| `…/api/ProjectionsService.java`, `ProjectionBoard.java`, `ProjectionRow.java`, `PlayerProjectionResponse.java` (new) | the names §12 already lists (the existing controller keeps its name, `ProjectionController`); scoring, state and counts move here |
| `…/scoring/ExplainedScore.java` | built as 5.4: the per-bonus line is `BonusLine` (stat, gte, value, points, triggered; `scoring.Bonus` is the rule record), `total` stays `ScoringEngine.score` itself. `times(factor)` was deleted: no caller, and it has no coherent meaning once bonuses are all-or-nothing |
| `…/scoring/Bonus.java`, `ScoringEngine.java` | `Bonus.reachedBy` is the one definition of "the line reaches the threshold"; the engine pays by it and the explanation reports by it. The engine's arithmetic is unchanged |
| `…/projections/ProjectionSource.java` | `description` → `summary`; `typicalError`; the id/length checks stay |
| `…/projections/WeightedAverageProjectionProvider.java` (`source()`) | supplies the measured values |
| `…/projections/TargetWeekResolver.java`, `CurrentTargetWeek.java` (new), `application.yml` | the shared 48-hour constant, public; the per-request entry point is the new `CurrentTargetWeek`, a 60 s cache over the resolver (`fantasykai.projections.target-cache-seconds`), with `CurrentTargetWeekTests` |
| `…/projections/AvailabilityService.java` | a `forWeek(target, roster)` overload, so the roster is read once and shared with the counts (not a count helper) |
| `…/ingest/RosterWeekIngestor.java`, `InjuryReportIngestor.java` | `SOURCE` made `public`, so `availabilityAsOf` reads the strings the pulls record. Nothing else |
| Tests: `api/ProjectionApiTests` (new), `api/ProjectionOrderingTests`, `api/RankingsTests` (lines 448–461 move to the new class), `scoring/ExplainedScoreTests`, `projections/ProjectionSourceTests`, `projections/CurrentTargetWeekTests` (new). The anonymous and hostile-parameter checks for the two routes are in `ProjectionApiTests`; `ReadApiTests` and `QuerySafetyTests` were not touched | §6.1 |
| `docs/projections-v1-architecture.md` (§9, §13), `docs/map.md`, `scripts/session-check.sh` (`EXPECT_TESTS`, `EXPECT_BACKEND_FILES`) | `EXPECT_ENDPOINTS` stays 25 and `EXPECT_MIGRATIONS` stays 11 under D1a |

**Not touched:** any migration, `ProjectionJob`, `ProjectionRepository`, `StatKey`, `player_game_stats`, the frontend,
and `ScoringEngine`'s arithmetic. Touched without changing behaviour: the two ingestors (a `public` on `SOURCE`) and
`ScoringEngine` (its comparison is `Bonus.reachedBy`).

## 6. How projections are validated

Three separate questions, three separate gates. None of them is a model change: **decision C stands** — the
EWMA h = 4 baseline ships, labelled as one, with its error printed beside it.

### 6.1 Is the API right? (the Slice 5 gate)

Each assertion is an HTTP-level test, and each is **mutation-checked**: the line it protects is broken and the
test must fail (this project's own rule: *a test that passes is not a test that can fail*).

| Assertion | Mutation that must turn it red |
|---|---|
| Anonymous → 401 on **both** routes; another member's `profileId` → **404** (not 403), also after the profile is warm in `ScoringProfiles`' cache | pass `null` as the owner to `profiles.byId` |
| `points` equals `roundForDisplay(ScoringEngine.score(line, rules))` — **bit-identical** on the unrounded value, for every preset and a custom ruleset with bonuses | route the row through a re-derivation |
| `sum(parts) + sum(triggered bonuses) == total`; PPR − 0 PPR = `rec` exactly; a line at exactly a threshold triggers it | `>` for `>=` in the bonus test |
| Order: unrounded points desc, then id, **before** paging, across a page boundary | sort the rounded value (exists; kept) |
| An Out or reserve player is not ranked and is counted in `excluded.unavailable`, and still has his line on `/players/{id}/projection` | drop the `ranked()` filter |
| Each state is reachable: `READY`, `STALE` (47 h vs 49 h), `UNAVAILABLE`, `NO_UPCOMING_WEEK`; `BYE`, `NO_HISTORY`, `NOT_ROSTERED` | change the 48-hour constant |
| `excluded` respects `position` and ignores `q`; a bye is counted once | count the wrong team |
| Query safety: `DROP TABLE` in `position`, `size=5000`, a non-numeric `profileId` → 400, and the tables survive | remove a whitelist call |
| Presets (D2) equal the engine's score under each of the three presets, and the strip is only presets | score with the wrong profile id |

Gates reported as the CONTRIBUTING review pass requires: `./mvnw -B verify`, `session-check.sh`, the frontend
suite (unchanged, so "nothing here touches it" with the build still run).

### 6.2 Is the thing in production the thing that was tested? (before switch-on)

The shipped function must be research baseline D, **bit for bit**. Both checks already exist and are re-run on
`main` immediately before switch-on; the threshold is exactly zero:

- `scripts/backtest.sh parity` (a real stage of the script): the provider against research baseline D over every
  2024–25 player-week on both research datasets. Slice 2 recorded **28,652 compared, 0 mismatches**.
- `ProviderParityTests.seededRandomHistoriesMatchBitForBit` (2,000 seeded histories, bit for bit) runs in every
  `verify`, so that half is already a standing gate.
- Slice 3 recorded a third row: production's `JdbcProjectionInputs` against the research histories on **14,906**
  2024–25 player-weeks, 0 mismatches. That figure is recorded in the docs; **the command that reproduces it
  was not confirmed for this plan** (§10), and confirming it is part of 5.0.

### 6.3 How good is it, and how will we know? (the metric, the evidence, the threshold)

**Metric.** PPR mean absolute error over **P**: each week's top 32 QB / 64 RB / 96 WR / 32 TE by opportunity who
played (a stat row or offensive snaps; `projection-accuracy.md` defines it, and "players who appeared" is only its
shorthand: the board's own population, P-all, reads about 4.5 on the same baseline), unrounded, per week and
pooled, with **bias** and RMSE beside it; also under 0 PPR and Half PPR. This is the
metric the project pre-registered (`projection-accuracy.md`, "Metrics and the pre-registered ship rule"); MAE has a
known flaw (it rewards projecting a point below the mean, v2 found), so it is reported with bias and RMSE and is
**not** the acceptance metric for any future model (decision C item 4 stays open).

**Evidence for the baseline (D), all from committed reports.**

| Sample | n | PPR MAE | Bias | RMSE |
|---|---:|---:|---:|---:|
| 2024, tuning season | — | 5.480 | — | — |
| 2025, test, P | 3,807 | **5.512** | +0.544 | 7.121 |
| 2025, P with zero-stat appearances | — | 5.532 | — | — |
| 2026 weeks 1–4, P (v2 prospective) | 896 | **5.688** | +0.300 | 7.322 |

**What the model-change rule says (unchanged, for completeness).** A learned model replaces the baseline only if
it beats it by **≥ 3% PPR MAE** with a paired, week-resampled 95% CI entirely below 0 and no position's CI above
0 (the pre-registered rule). The learned model (v1) missed it on 2025 (+2.7%). Its frozen successor v1′ passed formally on 2026 weeks 1–4
(+3.3%) but was judged not convincing (decision C) and keeps being scored as weeks land
(`scripts/backtest.sh v2-prospective`); nothing in Slice 5 changes that.

**Live thresholds (Slice 7 builds the report; the numbers are proposed here so they are fixed *before* any live
week, as the project requires).** From locked rows against what happened, scored by the same engine:

| Tripwire | Threshold | Where it comes from |
|---|---|---|
| 4-week pooled PPR MAE | outside **5.19 – 5.81** → investigate | 5.5 ± 2 SE. SD of the absolute error = √(RMSE² − MAE²) = √(7.121² − 5.512²) = **4.51**; a 4-week pool is about 846 player-weeks, so SE = 4.51/√846 = **0.155**; 2 SE = 0.31. The 2026 sample (5.688) sits **1.2 SE** above 5.5, inside the band |
| 4-week pooled bias | outside **−0.1 … +0.9** → investigate | the baseline's own observed bias is **+0.30** (2026) and **+0.54** (2025), a real, documented over-projection; centred on 0.4, ± 2 SE (bias SE = 7.1/√846 = **0.244**; 2 SE = 0.49) |
| A single week | reported, never alerted | SE of one week is 0.31, so one week is noise |

Two honest cautions. **The pre-registered "|bias| ≤ 0.5" cannot be reused as a tripwire for the baseline**: it was
written for candidate models, and the baseline's own 2025 bias (+0.544) would have failed it. And the bands assume
independent errors; games within a week share conditions, so the true SE is larger (the research bootstrapped whole
weeks for that reason). They are **tripwires to investigate, not tests, and never an automatic switch-off.** I also
propose reporting **P0** (players who were expected and sat, scored as zero) beside P, because availability is the
largest error: knowing who was out would remove **0.915** PPR points of MAE per P0 player-week for the baseline in
2025 (0.879 for the model), about six times the model's whole edge over it.

**Not validated, and not claimed:** predicting whether a Questionable or Doubtful player plays (out of scope for
V1); matchups, game script, or injuries beyond the official report.

## 7. What "switch the job on" requires

`fantasykai.projections.job-enabled` is `false` (`application.yml`), by owner decision 2026-10-12, *"turned on
only deliberately, because a row the job writes locks for good at kickoff"*. It is not part of Slice 5. Preconditions:

| # | Requirement | Why | Status, with evidence |
|---|---|---|---|
| R1 | **Slice 5 merged and deployed** (D4 may relax this to "before Slice 6") | the contract members' tools will read | approved and built (PR #101); **not merged, not deployed** as of 2026-11-23 |
| R2 | **Production appearance backfill, 2020–2025** | the architecture says so: *"The daily pull fetches only the current season, so `player_game_appearances` would otherwise lack every earlier season's zero games"*; the baseline reads history across seasons | **Verified gap, 2026-11-17.** Production holds appearances for **2026 only**: 3,219 rows (355 zero-stat, regular season). The local mirror, backfilled, holds 2020–2026: regular-season rows 6,000 / 6,461 / 6,402 / 6,474 / 6,454 / 6,381 / 3,508 |
| R2a | Its acceptance test | a backfill is only proven by counts | production's per-season regular-season counts equal the mirror's. **Do not assert equality with research**: the mirror's zero-stat counts are **746 / 816 / 812 / 854 / 823 / 689** against the research's **730 / 796 / 806 / 851 / 818 / 687** that Slice 3 recorded as reproduced exactly on 2026-10-12 — 2 to 20 higher. The cause is **not established** (a hypothesis: players-master revisions since October resolve more pfr ids, and the research listed 29–46 unplaced snap rows per season). Explaining or bounding that gap is the first task of R2 |
| R2b | A way to run it | `BackfillRunner` is an `ApplicationRunner` that returns; it does not end the JVM, while the scheduler's non-daemon threads keep it alive (`IngestOnceRunner` calls `System.exit` for exactly that reason, and `deploy/README.md` documents the "second backend with its own scheduler" trap). So the README's one-shot command **cannot run a backfill as written** | D5. Rehearsed on a restored copy either way, after a §7-step-2 backup |
| R3 | **Rehearse the job on a restored production copy** with `job-enabled=true`, and time `TargetWeekResolver`'s query there (`docs/perf/target-week-resolver-2026-11-18.md`) | the first production run writes rows that lock; the cache's premise has only been measured on the laptop | expect: `candidates` ≈ rostered skill players on teams with an open game; `written` ≤ `candidates`; `locked` 0; a second run writes identical rows (`ProjectionJobTests` proves it on synthetic data; this proves it on real data); 20 sampled raw lines equal the local mirror's, same code and data |
| R4 | **Config**: `compose.prod.yml` sets the backend's environment explicitly, so the flag needs a line there (`FANTASYKAI_PROJECTIONS_JOB_ENABLED: ${PROJECTIONS_JOB_ENABLED:-false}`) and `PROJECTIONS_JOB_ENABLED=true` in `deploy/.env` | no runtime override exists today (2026-11-12 report) | a backend redeploy, §7 of the runbook; Caddy untouched |
| R5 | **Time the first run early in a week** | the job writes rows **only for games that have not kicked off** and locks them at kickoff. A game that kicked off before the first run gets no row, and its players have no projection that week | run it Tuesday or Wednesday after the 06:00 ET ingest, before Thursday night |
| R6 | **Monitoring exists** | the job reports as `ingest_runs.source = 'fantasykai.projections'`, so `ingestFreshness` covers it | verify the row appears daily after switch-on, with the SELECT in `docs/development.md` |
| R7 | **A rollback that does not pretend to undo** | locked rows are the record | set the flag back to `false`: open rows stop regenerating and the API reports `STALE` after 48 h; locked rows stay; a corrupt locked row has the one audited repair path (`deploy/README.md` §8). Nothing is deleted |
| R8 | **Owner sign-off on the first week's board** before anything is built on it | | review the top 20 under PPR against the mirror's run |

**What you commit to by switching on.** The first stored row fixes the meaning of `weighted-average-v1` for good:
any later change to the weighting, window, roster eligibility or availability semantics must be a new id
(`-v2`), because a stored projection must keep saying exactly how it was made (`ProjectionSource`).

## 8. Order of work

| # | Work | Size | Depends on |
|---|---|---|---|
| 5.0 | Characterization tests of the current contract (§6.1 rows that already hold) | S | approval |
| 5.1 | Split the controller into service and records, no behaviour change | S–M | 5.0 |
| 5.2 | Resolver-derived target and states; `NO_UPCOMING_WEEK`; the shared 48 h constant | M | 5.1, D1 |
| 5.3 | Envelope fields and `excluded` | M | 5.2 |
| 5.4 | Per-bonus explanation | S | 5.1 |
| 5.5 | Preset comparison | S | 5.1, D2 |
| 5.6 | `source.summary` and `typicalError` | S | D3 |
| 5.7 | Mutations, docs, baselines, review pass | S | all |

Two PRs: **(1)** 5.0–5.1 (tests and a pure refactor), then **(2)** 5.2–5.7 (the contract change). About **M–L** in
all. Switch-on work (R2–R5) is separate: R2 about **M**, R3 **S**, R4 **S**.

## 9. The argument against this plan

- **It polishes an API nobody calls.** The strongest alternative is to build Slice 6's UI against the current shape and
  let the screen drive the contract. The reply is cost asymmetry: while no consumer exists the shape is free to
  change; after Slice 6 every field is a migration of a screen. If you would rather see the UI first, 5.4–5.6 can
  follow it and 5.0–5.3 still go first.
- **D1(a) puts the resolver on the request path.** If it proves slow on production-sized data, D1(b) is the fallback.
  I would measure once and report; arch §8 argues the board is trivial next to `/rankings`' ~6K-row scan, and the
  §9 baseline invariants are untouched.
- **D4(a) starts a permanent record before there is a screen for it.** What would falsify "start early" is R3 turning
  up an eligibility or availability problem on real data, in which case the first week is wasted and nothing locked.
- **The live thresholds are derived under independence** and are therefore optimistic; they are tripwires.

## 10. Not verified

- That the per-request `TargetWeekResolver` always agrees with what the job resolved at 06:00 (same class, same
  inputs). **Still not pinned:** no test runs the job and the board together over a week, and nothing has run it on
  production data (R3 does).
- The cause of the +2 to +20 zero-stat difference against research (R2a).
- The command that reproduces Slice 3's 14,906-week input parity (§6.2). `backtest.sh parity` and
  `ProviderParityTests` are confirmed to exist; I did not run either for this plan.
- That `BackfillRunner` really hangs in a container with `scheduled-enabled=true`: inferred from the code and
  `IngestOnceRunner`'s comment, not run.
- Production roster-feed timing for week 11 (when next week's rosters appear relative to the Tuesday pull).
- Everything in §6.3's tripwires beyond their arithmetic: they have never been exercised on a live week.
