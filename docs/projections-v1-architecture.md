# Projections V1: architecture (approved 2026-10-09)

*Written 2026-10-09, after the research closed with **decision C**
([`perf/projection-accuracy.md`](perf/projection-accuracy.md)).*

**Status: approved by the owner on 2026-10-09.** Slice 2 (the provider and its parity tests) is
the first code. north-star's Phase 6 brief (§10) points here, and where they disagree this document
is the newer intent.

**The approved decisions, verbatim in substance:**

1. **Bonuses are scored on the projected line.** Projected raw stats go through the existing
   deterministic `ScoringEngine`, with no expected-value bonus scoring in V1. The research
   finding is kept: expected-value bonuses differed by about 0.20 points on average and were
   slightly worse for the weighted baseline (§5).
2. **Availability uses known information only.**
   - `Out` and applicable reserve lists are excluded from the projection rankings.
   - `Questionable` and `Doubtful` players stay projected and ranked, with a visible status.
   - Injury availability is not predicted (§6).
   - **No unexplained disappearance.** A player excluded for availability keeps his stored
     projection. His page says a projection exists and that he is unavailable, and the board
     says who it left out (§6, §9, §11).
3. **New nflverse inputs:** the weekly roster and injury report feeds, in Slice 4, not Slice 2.
4. **No NFL history, no projection.** 1–3 games get a projection, later labelled
   `Limited history`. No rookie priors, nothing fabricated (§7).

V1 ships the **weighted-average baseline** (research baseline D, EWMA h = 4) behind a provider
boundary. Swapping in a learned provider later changes one class and one config value, and nothing
in the API, scoring or UI.

**Every number below was measured on 2026-10-09:**

- against the local database;
- against nflverse's `injuries`, `weekly_rosters` and `stats_player_week` files;
- against the Slice 1 test run's `test-predictions.csv`.

Each one names its population and season.


## 1. The boundary

```
ingested stats (player_game_stats, REG)          roster_week · injury_report (new, raw)
          │                                                  │
   ProjectionInputs ──► ProjectionProvider ──► ProjectedLine │  (raw stats only)
          │            (WeightedAverage… V1)        │        │
          └──────────── ProjectionJob ──────► player_week_projection (raw, per provider)
                                                    │        │
          ScoringProfiles.byId ─► ScoringEngine.score(line, rules)   AvailabilityResolver
                                                    │        │
                           ProjectionsService ─► /api/v1/projections · /players/{id}/projection ─► UI
```

**Only `ProjectionJob` knows which provider runs.** It is chosen by config
(`fantasykai.projections.provider`, one value in V1).

- Rows carry `provider`. The API reads the active provider's rows and passes the provider's
  metadata (label, summary) to the UI.
- So no screen hardcodes "weighted average". A `ModelProjectionProvider` later is a new class and
  a config value. It could even run in shadow, writing its own rows for honest prospective
  scoring.

**Package: `com.fantasykai.projections`** (plural) in `src/main`. The research lives in test-scoped
`com.fantasykai.projection`. Production never imports test code. Parity tests point test → main
only.

## 2. Domain model and provider interface (`com.fantasykai.projections`)

```java
public interface ProjectionProvider {
    ProjectionSource source();      // id "weighted-average-v1", label "Fantasy-Kai Baseline", summary
    List<PlayerProjection> project(TargetWeek target, ProjectionInputs inputs);   // batch
}
public interface ProjectionInputs {  // read-only; grows additively for a future provider
    List<EligiblePlayer> players(TargetWeek t);          // rostered for t, team plays in t
    List<HistoricalLine> history(long playerId, TargetWeek t);   // stored REG lines strictly before t, oldest first
}
record TargetWeek(int season, int week) {}
record HistoricalLine(int season, int week, double[] stats /*StatKey order*/, double[] usage) {}
record PlayerProjection(long playerId, long gameId, int teamId, double[] stats, double[] usage,
                        int gamesUsed, String dataThrough) {}   // raw stats, never points
```

- `history()` is the production twin of `Timeline.asOf`. It is the one leakage door, and it gets
  its own tests.
- A batch interface lets a future model compute features in bulk.

## 3. The weighted-average algorithm (exactly the tested baseline D, `h = 4`)

For player *p* and target week *(S, W)*:

- **History.** H is *p*'s **stored** regular-season stat rows with *(season, week) < (S, W)*, every
  season since 2020 (the ingest window, the same as the research) and every team, oldest first.
  n = |H|.
- **Formula.** For each of the 13 `StatKey` stats and 3 usage counts:
  `proj = Σᵢ wᵢ·xᵢ / Σᵢ wᵢ`, with `wᵢ = 0.5^((n−1−i)/4)`.
  - Age is counted in **games played**, with the most recent game at age 0.
  - The sums run in the oldest → newest order.
  - The half-life is **4 games**, a constant inside the provider and not config, so it cannot
    drift.

| Case | Behaviour (identical to the tested baseline) |
|---|---|
| Week 1 | Last season's games (crosses seasons, no offseason decay) |
| Rookie / n = 0 | **No projection** |
| n = 1–3 | Projected and labelled **Limited history**. Relative error (MAE ÷ mean) is 0.96 with 1 prior game and 0.81 with 2–3, against ≤0.70 with 4+ (2025, measured) |
| Team change | Ignored: history follows the player |
| Bye, inactive, missed weeks | Absent from H. Ages count games, not weeks |
| Active zero-stat games | **An appearance.** The provider counts every game it is given, including an all-zero line, and never filters one out. Today's ingest stores no snap-only appearance, so production history would match the Slice 1 tested baseline (stored rows) until a later slice adds them. That is an open data decision; see §15 |
| Stat- or position-specific behaviour | None. Every stat, including the rare ones (2-pt, `ret_td`, `fum_lost`), is its own EWMA |
| Clamping / shrinkage | None. Matches the research |

**Its expected error, from the research.** On stored-row data the PPR MAE per player-week was
5.48 (2024) and 5.51 (2025) on P, and 4.47 on the board-wide population (2025).

**Parity, three layers.**

1. Unit tests with hand-computed values.
2. A seeded randomized test in the research package: the production provider equals
   `Baseline(EWMA, 4).project(...)` **bit for bit** on the 13 stats.
3. A `scripts/backtest.sh parity` stage: for every 2024–25 player-week, the production provider
   equals the research EWMA exactly, and the count is printed.

## 4. Stat schema

- **Projected:** the 13 `StatKey` stats, every stat any ruleset can score, since custom rulesets
  may score any of them. pass_yd, pass_td, pass_int, pass_2pt, rush_yd, rush_td, rush_2pt, rec,
  rec_yd, rec_td, rec_2pt, fum_lost, ret_td.
- **Display-only usage:** pass_att, rush_att, targets. They explain the line ("6.1 targets") and
  are never scored.
- Nothing else: no air yards or any other nflverse field.

## 5. Threshold bonuses: Option A, deterministic (measured)

**Real rulesets.**

- No preset uses a bonus, and neither does My league.
- 0 of 5 local profiles use one. Production profiles were not checked, since there is no prod DB
  access.
- The builder does allow bonuses.

**Measured on 2025** (5,914 player-weeks, a 100-rush / 100-rec / 300-pass, 3-point league),
expected-value against stepped bonus points:

- mean difference 0.20 points, median 0.06, p99 0.95; ≥1 point on only 0.9% of player-weeks;
- expected value is **worse** for this baseline: MAE 4.250 against 4.220, bias +0.28 against
  +0.12.

**So V1:** `ScoringEngine.score(projectedLine, rules)` unchanged, which gives bonuses on the
projected line. The player page's explanation shows each bonus as "projected 101 ≥ 100: +3".

This **supersedes brief decision #1** (expected bonuses), on evidence. The research
`ExpectedPoints` is not ported. Revisit only if bonus usage in production turns out material.

## 6. Availability: known information only (measured)

On 2025's 406 player-weeks that were projected before kickoff and then absent:

- **153 (38%) were already Out or on a reserve list**, and **0 players who played** carried those
  flags.
- Removing their projections would cut **0.426 PPR** of availability error per projected
  player-week, 2.9× the learned model's whole edge, with no false positives.
- 41 of the absences were Questionable (100 Questionable players played).
- 37 were game-day inactives, which our pipeline can't know before kickoff.

**Policy (B for definite, C for uncertain):**

- **OUT** (injury report) and **RESERVE** (weekly roster RES/PUP/SUS/NFI-type) keep their raw
  projection but are **not ranked**. They never vanish silently:
  - the player page shows the projection with "Out — not ranked this week" and why;
  - the board's caption counts who was left out and lists them on request
    (`excluded.unavailable` in the API).
- **DOUBTFUL** and **QUESTIONABLE** are ranked unchanged and carry a badge. We don't predict.
- **UNKNOWN** applies when roster or injury data is missing or stale. The player is ranked, and
  the caption says what we don't know. Degrade, never 500.

**Inputs.** Two new optional raw ingests, header-verified, with a 404 recorded as `SKIPPED`:

- `roster_week`, from nflverse `roster_weekly_{season}.csv`: gsis, team, week, status. It also
  gives the **target-week team** (byes, trades).
- `injury_report`, from `injuries_{season}.csv`: gsis, week, report_status.

Both update daily (measured: today 14:19 and 14:22 UTC). The final Out/Questionable statuses
land Friday, and our 06:00 ET daily ingest picks them up Saturday. `players.status` is not used:
it says ACT for 3,900 skill players with no game since 2020, so it's stale.

## 7. Eligibility and sparse history (measured on the 2026 week 5 roster)

A player is projected when he is rostered for W (ACT, or RES/INA for the status display), has a
skill position, his team plays in W, and he has n ≥ 1.

- **Of 468 ACT skill players:** 399 have n ≥ 4, **43 are Limited** (1–3), and **26 get no
  projection**: 22 of them rookies, and 14 of the 26 QBs.
- A position-prior fallback would add 26 fabricated lines, mostly for backup QBs, so it is
  rejected in favour of honest missingness.
- **Confidence** is `gamesUsed` (n) plus a binary *Limited history* (n < 4). No percentages.

## 8. Persistence and lifecycle

**Persist raw projections, score per request.**

- `V7__player_week_projection`: PK *(player_id, season, week, provider)*, plus `game_id`,
  `team_id`, the 13 `StatKey` columns and 3 usage columns as `DOUBLE PRECISION`, `games_used`,
  `data_through`, `generated_at` and `locked_at`.
- **No points column**, enforced by a schema test, and `player_game_stats` is untouched.
- `V8`: `roster_week`, `injury_report`.
- Scoring about 500 rows per request is trivial next to `/rankings`'s ~6K-row scan. No Redis.

**Target week W.**

- W is the smallest regular-season week of the current season in which some game is not yet
  complete. A game is **complete** when its stat rows are stored (the score can lag, as ATL–NO
  did).
- The board rolls forward only when **all** of last week's stats are in: normally the Tuesday
  06:00 ET ingest, otherwise the next one. Until then week N shows with every game locked.

**Generation.**

- `ProjectionJob` runs right after the daily ingest (same scheduler) and headless through
  `ingest-once`.
- It is recorded in `ingest_runs` as `fantasykai.projections`, so `ingestFreshness` covers it.
- It regenerates W's rows for games that haven't kicked off. Kicked-off rows get `locked_at` and
  are **never rewritten**, which keeps an honest record for live-accuracy tracking.
- It is deterministic and idempotent. It recomputes daily because stats can be corrected and
  rosters change. No real-time updates.

**Edge cases:**

- **Bye:** no row; the player page says "Bye in week W".
- **After week 18:** state `NO_UPCOMING_WEEK`. The daily scheduler is off from March to August,
  and the first September run generates Week 1 from last season's games.
- **Thursday games:** those rows lock at kickoff while the rest of the week stays open.

## 9. API: new endpoints, `/rankings` and the §9 baseline untouched

- **`GET /api/v1/projections?profileId&position&page&size`** returns `ProjectionBoard`:
  - `state`, `season`, `week`;
  - `source {id, label, summary}`;
  - `dataThrough`, `generatedAt`, `availabilityAsOf`;
  - `excluded {unavailable, noHistory, bye}`;
  - `page: PageResponse<ProjectionRow>`.

  Each `ProjectionRow` holds rank, playerId, name, position, team, opponent, home, kickoff,
  locked, points (rounded once here), gamesUsed, history (ESTABLISHED | LIMITED), availability
  {status, detail} and espnId.
- **`GET /api/v1/players/{id}/projection?profileId`** returns `PlayerProjection`:
  - `state` (PROJECTED | BYE | NO_HISTORY | NOT_ROSTERED | NO_UPCOMING_WEEK), plus the target
    game, source, gamesUsed, history and availability;
  - an `ExplainedScore` that **sums to `points`**: the line, each stat as value × rate = points,
    and each bonus;
  - the usage counts.
- **Inherited from existing code:** members-only via the default-deny chain; the `profileId`
  ownership 404 through `ScoringProfiles.byId`, warm cache included; the `ScoringPosition`
  whitelist; `roundForDisplay` once at the boundary.
- **No week or season parameter in V1**, because it is next week only.

## 10. Rankings semantics (follow `/rankings` where possible)

- The ranked set is projected and available players for W: OUT and RESERVE are excluded and
  counted.
- Overall plus a position filter, like the board. QBs are compared directly under the profile's
  rules, as the season board does.
- Order is points descending, then player id (stable paging).
- Locked (kicked-off) players stay in the list, flagged.
- Byes and no-history players are absent and counted in `excluded`.

## 11. Frontend UX (an extension of the board, not a new surface)

**Board.**

- The existing scope control gains a fourth option, **"Next week"**, beside Season, Per game and
  Last 4. It switches the data source to `/projections` and hides the season selector.
- Rows show "@ KC · Sun 1:00", a lock when kicked off, and status badges (Out rows don't appear;
  Q/D badges do).
- One caption line: "Week 6 · Fantasy-Kai Baseline ⓘ · injury report as of Sat 6:00 AM ET".
- **The scoring switch re-ranks live off the same raw lines.** This is the product's whole
  argument.

**Player page.** A **"Week 6 projection"** panel at the top of the workspace:

- the fixed raw line ("6.1 targets · 4.2 rec · 52 yd · 0.4 TD");
- points under the selected ruleset;
- a compact "0 PPR 9.8 · Half 11.9 · PPR 14.0" strip, plus the member's league if it's custom:
  *same line, different value*;
- an expandable value × rate explanation;
- the availability badge and the limited-history note.

**Transparency.** The label is **"Fantasy-Kai Baseline"**, never AI or model. The summary reads:
*"A weighted average of recent games — the latest counts most; a game four games older counts
half."*

The ⓘ popover holds the method, what it ignores (matchups, game script, injuries beyond the
official report), and its typical miss: about 5.5 PPR points per player-week in 2025 for players
who played. The number is in the popover, not on rows. The copy comes from the API's
`source.summary`, so a provider swap updates it.

## 12. Files likely to change

| Area | Files |
|---|---|
| New `projections/` | `ProjectionProvider`, `WeightedAverageProjectionProvider`, `ProjectionSource`, `ProjectionInputs` + `JdbcProjectionInputs`, `TargetWeek` + `TargetWeekResolver`, `PlayerProjection`/`HistoricalLine`, `ProjectionRepository`, `ProjectionJob`, `AvailabilityResolver` |
| `ingest/` | `RosterWeekIngestor`, `InjuryIngestor` (the `StatIngestor` `List<Field>` pattern); `IngestService`, `IngestScheduler`, `IngestOnceRunner` (run the job) |
| Migrations | `V7__player_week_projection.sql`, `V8__roster_week_and_injury_report.sql` |
| `api/` | `ProjectionsController`, `ProjectionsService`, `ProjectionBoard`, `ProjectionRow`, `PlayerProjectionResponse`, `ExplainedScore` |
| Frontend | `lib/types.ts`, `lib/api.ts` (hooks), `rankings/FilterBar.tsx`, `RankingsWorkspace.tsx`, `PlayerRow.tsx`, `player/PlayerWorkspace.tsx`, a new `player/ProjectionPanel.tsx`, the info popover |
| Docs and checks | `CLAUDE.md`, `map.md`, north-star brief, `session-check.sh` (endpoint count 13 → 15), `deploy/README.md` (V7/V8) |

**Reused:** `StatKey`, `StatLine`, `ScoringEngine`, `ScoringProfiles.byId`, `ScoringPosition`,
`PageResponse`, the `StatColumns` pattern, `IngestRunRecorder`, `CsvValues`, `NflverseClient`.

## 13. Slices (each its own PR, independently testable)

| # | Ships | Done when |
|---|---|---|
| **2** | `projections/` domain, `WeightedAverageProjectionProvider` (pure, no DB) | Hand-computed tests; randomized bit-parity against research baseline D; `backtest.sh parity` exact on every 2024–25 player-week |
| **3** | `V7`, `JdbcProjectionInputs`, `TargetWeekResolver`, `ProjectionJob` (lock at kickoff, `ingest_runs`, headless) | Schema test (all `StatKey` columns, no points column); lifecycle tests (mid-week, MNF stats missing, rollover, bye, after week 18, preseason); a locked row survives a rerun; `OneShotContextTests` with the job |
| **4** | `V8`, `RosterWeekIngestor`, `InjuryIngestor`, `AvailabilityResolver`, roster-team eligibility | Real 2025 rows as fixtures; header check; 404 → `SKIPPED`; Out/reserve/Q/D/unknown resolution; missing data → UNKNOWN, never a failure |
| **5** | The two endpoints and `ExplainedScore` | 401 anonymous; another member's profile 404 (warm cache); `QuerySafetyTests`; points bit-identical to `ScoringEngine.score`; PPR − 0 PPR = rec; parts sum to total; Out not ranked; states for bye, no history, no week |
| **6** | Board "Next week" and the player panel | lint/test/build; browser-verified at 390 and 1440; other pages unchanged |
| **7** | QA and monitoring | A weekly live-accuracy report (locked rows against actuals); a freshness alert; deploy runbook; acceptance checks; docs |

Slices 3 and 4 can run in parallel; 5 needs both.

## 14. Known risks

1. **Unknowable absences.** Game-day inactives (37 of 406 in 2025) and late changes after the
   06:00 ingest can't be known before kickoff. 164 absences had no flag at all.
2. **No zero-stat games in production history.** They make the baseline over-project fringe
   players; 2025 baseline bias on P was +0.54.
3. **MNF stats lag** delays rollover to Wednesday on some weeks.
4. **The bonus step is discontinuous** at a threshold. Measured small, and shown in the
   explanation.
5. **A future provider may need more inputs.** Handled by `ProjectionInputs` growing additively;
   the row contract (raw stats) never changes.
6. **Spring Boot 3.5 is past OSS end-of-life.** V1 adds endpoints and ingests before Phase 11.5,
   which is still owed.
7. **nflverse schema or availability changes.** Covered by header verification, optional sources
   and UNKNOWN states.
8. **Visible gaps.** 26 rostered players have no projection, and players are not projected until
   their first game.

## 15. Open decisions for later slices

- **Zero-stat active appearances in production history.** The provider counts them (Slice 2 tests
  this). Whether production *supplies* them is a data question for Slice 3 or 4. Today a skill
  player with offensive snaps and no stat line leaves no row: `SnapCountIngestor` only writes
  `snap_pct` onto existing rows.

  The two research datasets give the size of the effect. On 2025 the baseline's error was
  5.512 on stored rows (Slice 1) and 5.532 with zero-stat appearances added (v2), the second on a
  population that also contains those appearances.

  The options:

  - **(a)** store snap-only appearances in their own table, next to the weekly roster feed.
    Never in `player_game_stats`, whose row count is the §9 baseline and whose `gamesPlayed`
    counts the board shows.
  - **(b)** accept the documented optimism.

  This needs the owner's decision before Slice 3's inputs are built.

