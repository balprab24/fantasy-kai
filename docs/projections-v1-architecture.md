# Projections V1: architecture (approved 2026-10-09)

*Written 2026-10-09, after the research closed with **decision C**
([`perf/projection-accuracy.md`](perf/projection-accuracy.md)).*

**Status: approved by the owner on 2026-10-09.** Slice 2 (the provider and its parity tests) is
the first code. north-star's Phase 6 brief (§10) points here, and where they disagree this document
is the newer intent.

**Slice 3 (2026-10-12)** follows §8, with the owner's decisions of that day and the
refinements of its review, each recorded where it applies:
- §15 is resolved as (a): zero-stat appearances are stored.
- The job ships switched off, and stays off after Slice 4 until it is turned on deliberately.
- §8 records:
  - the primary-key order, and `data_through` as two columns;
  - the provider-id length, held by the domain and the column together;
  - the lock and its one audited repair path;
  - schedule reconciliation (`V8`, from nflverse's measured behaviour);
  - the 48-hour rule, narrowed to late stats;
  - a corrupt history failing the run by name.

**Slice 4 (2026-10-12)** adds known availability: the roster and injury feeds measured before
anything was built, the rule, storage, ingest, the historical check (§6), and roster eligibility
for the job (§7). The job stays switched off; no endpoint or UI changed.

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

## 2. Domain model and provider interface (`com.fantasykai.projections`, built in Slice 2)

```java
public interface ProjectionProvider {
    ProjectionSource source();                            // what made it; persisted beside every line
    Optional<RawProjection> project(PlayerHistory history);   // raw stats, or nothing
}
record ProjectionSource(String id, String label, String description) {}  // id: versioned, never a class name
record SeasonWeek(int season, int week) implements Comparable<SeasonWeek> {}   // regular season, 1-18
record HistoricalGame(SeasonWeek when, double[] stats /*StatKey order*/,
                      double passAttempts, double carries, double targets) {}  // one appearance
final class PlayerHistory {                               // the only way a provider sees games
    static PlayerHistory before(SeasonWeek target, Collection<HistoricalGame> all);  // strictly before target, oldest first
}
record RawProjection(ProjectionSource source, SeasonWeek target, double[] stats,
                     double passAttempts, double carries, double targets,
                     int gamesUsed, SeasonWeek lastGame) {      // never points
    StatLine line(String position);                       // what ScoringEngine reads
}
```

- **`PlayerHistory.before` is the one leakage door.** It is the production twin of
  `Timeline.asOf`, and the cutoff is built into the only way to construct a history.
  `PlayerHistoryTests` fails if it ever admits the target week.
- **Selection.** `ProjectionConfig` exposes one `ProjectionProvider` bean, chosen by
  `fantasykai.projections.provider` (default `weighted-average-v1`). An unknown id stops startup.
  The bean exists in the headless one-shot context the daily job runs in (`OneShotContextTests`).

**One refinement from the approved sketch, made in Slice 2.** The provider projects **one player
from his history** instead of taking a batch with eligibility inputs (`ProjectionInputs.players`).
Which players get projected (rostered, playing that week) is the job's question in Slices 3–4,
not the provider's.

A future learned provider that needs more than a player's own games (team volume, the line) would
extend the input. Only the provider and the job, its single caller, would change. Scoring, the
API and the UI read stored raw lines and `ProjectionSource`, and never see a provider.

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
| Active zero-stat games | **An appearance.** The provider counts every game it is given, including an all-zero line, and never filters one out. Since Slice 3 production supplies them (§15, decision (a)): `JdbcProjectionInputs` adds every stored appearance with no stat row as a zero game, so production history is research v2's corrected dataset — equal game by game on every 2024–25 player-week (`backtest.sh parity`, 14,906 compared, 0 mismatches) |
| Stat- or position-specific behaviour | None. Every stat, including the rare ones (2-pt, `ret_td`, `fum_lost`), is its own EWMA |
| Clamping / shrinkage | None. Matches the research |

**Its expected error, from the research.** On stored-row data the PPR MAE per player-week was
5.48 (2024) and 5.51 (2025) on P, and 4.47 on the board-wide population (2025). With zero-stat
appearances in the history, which is what ships since Slice 3, it was 5.532 on 2025 — on a
population that also contains those appearances, so the two are not directly comparable (§15).

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

## 6. Availability: known information only (measured; built in Slice 4)

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

**The feeds, read before anything was built** (2024–26 files, 2026-10-12). Neither carries a
timestamp.

| Concept | Feed | Field(s) | Example values | V1 use |
|---|---|---|---|---|
| Player | both | `gsis_id` | `00-0036900` | The only join key: never a name, jersey or team. A blank id (2026: 6 roster rows) or one the player master lacks (26 rows) is skipped and logged |
| Team | both | `team` | `CIN`, `LA` (Rams, as in the schedule) | The team that week; the projection's team since Slice 4 |
| Week | both | `week`, `game_type` | `5`, `REG` / `WC` | Regular season only, weeks 1–18. The wild card is week 18 in 2020, so `game_type` decides |
| Roster status | roster | `status` | `ACT` `INA` `RES` `DEV` `CUT` `RET` `EXE` (+ rare `TRD` `TRC` `TRT`) | Membership, and reserve lists. One row per player per week; a bye team has no rows that week |
| Roster detail | roster | `status_description_abbr` | `A01`, `R01`, `R48`, `P07` | Stored, never decides: it only refines `status` |
| Game status | injuries | `report_status` | `Out` `Doubtful` `Questionable`, blank; `Note` (2024 only) | The week's designation. Pregame by nature: final Friday for a Sunday game |
| Practice | injuries | `practice_status`, `report_primary_injury` | `Limited Participation in Practice`, `Hamstring` | Stored for display; never decides |

- **What each status meant, measured.** 2025 regular season, rows with an id, against who
  recorded a stat line or an offensive snap that week:
  - ACT: 26,129 rows, 19,297 played;
  - DEV (practice squad): 8,333 rows, 0 played;
  - RES (reserve lists): 5,486 rows, 0 played;
  - INA: 3,419 rows, 1 played;
  - CUT: 932 rows, 0 played; RET: 349, 0; EXE: 7, 0;
  - injury-report Out: 1,342 rows, 0 played.
  - A first pass appeared to show 3 `RES R09` players who played. That was the analysis
    collapsing blank ids together: those rows are 1960s players with no id at all.
- **INA is the game-day inactive list.** On 2026-10-12 the Monday night teams read ACT 53 and
  INA 0, while every team that had played read ACT 48 and about 7 INA. It is written about 90
  minutes before kickoff, so a 06:00 pull never sees it, and a backtest that read it would read
  the future. It is never a reason to rank anyone lower.
- **Snapshots, not history.** Each week's rows are rewritten until (and after) its games, so a
  past week is its final state. A week is published during that week: week 6 was absent on
  Monday 2026-10-12, and week 5 present on Friday 10-09.
- **The injury file changed shape.** 2024 has `date_modified` and no `season_type`, 2025–26 the
  reverse; neither is read. 2024 listed two player-weeks twice (Cade Stover, week 15:
  Questionable, then Out four hours later).

**The rule, as built (`AvailabilityRules`).** First match wins:

1. roster RES or EXE &rarr; `RESERVE`, not ranked, even when also listed Doubtful (Trey Lance,
   2026 week 5);
2. roster DEV, CUT or RET &rarr; `NOT_ON_ROSTER`, not ranked, and not projected;
3. report Out &rarr; `OUT`, not ranked. Roster ACT with Out is not a conflict: ACT is membership
   of the 53, Out is this week's game;
4. Doubtful &rarr; `DOUBTFUL`, then Questionable &rarr; `QUESTIONABLE`, both ranked with a badge;
5. roster ACT or INA &rarr; `AVAILABLE`;
6. otherwise &rarr; `UNKNOWN`, ranked.

Any other value is no information, never unavailability. Two reports for one player-week keep
the more restrictive, because 2025–26 carry no timestamp to order them.

**Which week answers (`AvailabilityService`).**

- An injury designation counts for **its own week only**; a week-5 Questionable says nothing about
  week 6.
- A roster counts until the team's next week is published: each team is read at its latest
  published week up to the target, and the week it came from travels with the result. A roster
  status lasts until a transaction, and a reserve stint lasts weeks by rule.
- A player missing from his team's newest roster is not on it. A traded player is read from his
  newest row.
- Only the target season is ever read.
- No roster row and no designation is `UNKNOWN`. A missing, late or failed feed is not news
  about anyone, so it never makes a player disappear.

**Storage and ingest (`V9`).**

- **Tables.** `roster_week` and `injury_report` hold the source values as shipped, a blank
  designation as NULL, plus `ingested_at`, with PK *(season, week, player_id)*. Meaning is
  derived on read, so changing the rule never needs a backfill.
- **Current season only.** The daily pull and a backfill alike read just the season being played;
  production needs nothing older.
- **Each week the file carries replaces that week**, in one transaction. A week it does not carry
  is left alone.
- **Shrink guard.** A roster file that would shrink a stored week below 90% fails the run and
  writes nothing, because membership decides who is projected. Injury rows have no guard: losing
  one can only un-designate a player, who is then ranked.
- **Ordering.** `IngestService` reads both after the player master and the stats, so
  `DailyPull`'s projection job always runs on the morning's availability.
- **Reading at the API.** Availability never changes the raw line. It is read at request time
  (Slice 5), never stored with a projection.

**Validated historically** (`scripts/backtest.sh availability`). The production classifier,
nothing tuned, on 2025's P0 rebuilt from stored rows: 3,808 player-weeks, the research's 406
absences reproduced.

| V1 classification | Ranked | Absent | Played |
|---|---|---:|---:|
| AVAILABLE | yes | 156 | 3,302 |
| QUESTIONABLE | yes | 41 | 100 |
| DOUBTFUL | yes | 11 | 0 |
| OUT | **no** | 93 | 0 |
| RESERVE | **no** | 60 | 0 |
| NOT_ON_ROSTER | **no** | 39 | 0 |
| UNKNOWN | yes | 6 | 0 |

- **Out or reserve:** 153 absent players correctly left off the board, and **0 players who played
  wrongly left off**.
- **Not projected at all** (not rostered that week): 45 absent, 0 who played.
- **Game-day inactives with no designation:** 37, which cannot be known in time.
- **Doubtful sat 11 times out of 11** in 2025. It stays ranked: that is the approved rule, and
  this check does not tune it.
- **The leak this cannot see.** The roster file is each week's *final* snapshot, so a player
  activated from reserve, or elevated from the practice squad, after a Saturday 06:00 pull shows
  ACT here.
  - The bound: 0 players played after being RES the week before; 2 played as elevated
    practice-squad players.
  - Production would have left those 2 unprojected. This is not a false ranking suppression: the
    projection simply would not exist.
- `players.status` is not used: it says ACT for 3,900 skill players with no game since 2020.

## 7. Eligibility and sparse history (measured on the 2026 week 5 roster; built in Slice 4)

A player is projected when he is rostered for W (ACT, or RES/INA for the status display), has a
skill position, his team plays in W, and he has n ≥ 1.

- **As built.** Each team is read at its latest published roster week up to W, and the statuses
  are ACT, INA, RES or EXE (`AvailabilityRules.PROJECTED_ROSTER_STATUSES`).
  - This replaces Slice 3's interim `players.team_id`, which kept a retired player's last team
    for good.
  - A season with no roster yet projects no one.
  - Out and reserve players keep their raw line; availability only decides the board.
- **Of 468 ACT skill players:** 399 have n ≥ 4, **43 are Limited** (1–3), and **26 get no
  projection**: 22 of them rookies, and 14 of the 26 QBs.
- A position-prior fallback would add 26 fabricated lines, mostly for backup QBs, so it is
  rejected in favour of honest missingness.
- **Confidence** is `gamesUsed` (n) plus a binary *Limited history* (n < 4). No percentages.

## 8. Persistence and lifecycle

**Persist raw projections, score per request.**

- `V7__projections_and_appearances` (Slice 3) creates two tables.
  - `player_week_projection`: PK *(season, week, provider, player_id)*, plus `game_id`,
    `team_id`, the 13 `StatKey` columns and 3 usage columns as `DOUBLE PRECISION`, `games_used`,
    `data_through_season` and `data_through_week`, `generated_at` and `locked_at`.
  - `player_game_appearances` (§15): every game a skill player took an offensive snap in.
- `V8__games_delisted` (Slice 3) adds `games.delisted_at`. See "The schedule" below.
- **No points column**, enforced by a schema test, and `player_game_stats` is untouched.
- **Refinements made in Slice 3:**
  - The PK holds the same four columns, but leads with *(season, week, provider)*. The job's
    replace and the board's read are then a range of it, and a player's own lookup still names
    every column.
  - `data_through` is two columns: it is `RawProjection.lastGame`, a season and a week.
  - `provider` is `VARCHAR(40)`, and `ProjectionSource.MAX_ID_LENGTH` is 40. An id the column
    cannot hold is refused when the source is constructed, at startup, not by the first night's
    insert. A schema test holds the two numbers together.
- `V9__roster_week_and_injury_report` (Slice 4): `roster_week`, `injury_report`. See §6.
- Scoring about 500 rows per request is trivial next to `/rankings`'s ~6K-row scan. No Redis.

**The lock, and the one way to repair it.**

- **Normal application code never rewrites a projection after its game has kicked off.** A `V7`
  trigger refuses `UPDATE` and `DELETE` of a locked row, whatever code path asks. The job and the
  trigger are each proven alone: the trigger catches a job that forgot `locked_at IS NULL`, and
  the schema test catches a missing trigger.
- This is not a claim that no correction can ever happen. Genuine corruption, meaning a
  projection for a game that never existed, can be repaired by an operator, one row at a time.
  - It happens in one transaction that sets `fantasykai.projection_repair_reason`, a sentence of
    at least 10 characters.
  - The trigger copies the row as it stood, with the reason, user and time, into
    `player_week_projection_repairs` before allowing the change. A repair corrects the board
    without erasing what was recorded.
  - No application code sets the reason, and a test reads `src/main/java` to keep it that way.
  - `TRUNCATE` is for an empty test database, never a repair. The procedure is
    `deploy/README.md` §8.

**The schedule (`V8`).** nflverse's schedule has no status column. These cases were read from the
history of `nflverse/nfldata`'s `data/games.csv`, which the schedules release is built from
(2026-10-12):

| What happened | What the file did | Seen in |
|---|---|---|
| Moved within its week | Same `game_id`, new `gameday`/`gametime` | 2020 NE–KC (Sun → Mon), BUF–TEN (Sun → Tue), KC–BUF (Thu → Mon), BAL–PIT (Thanksgiving → Wed), DAL–BAL (Thu → Tue) |
| Moved to another week | Row deleted, a new `game_id` added; `old_game_id` is not stable across the move either | 2020 PIT–TEN week 4 → 7 (on 2020-10-02 neither row existed), DEN–NE 5 → 6, PIT–BAL 7 → 8 |
| Cancelled | Row deleted | 2022 BUF–CIN: listed unchanged, no score, from kickoff on 2023-01-02 to 01-05, gone on 01-06 |

- An upsert alone kept every such game in `games` for good, with no stats, and the job projected
  both teams for it.
- So `GameIngestor` reconciles each season it reads against the file.
  - A stored game the file no longer lists gets `delisted_at`.
  - One the file lists again is cleared.
  - It is marked, never deleted, because stats, appearances and locked projections may point at
    it.
- **Guards.**
  - A row the run cannot use (say, a blank team) still counts as listed: "could not read it" is
    not "it is gone".
  - A season the file has no rows for is not reconciled.
  - A run that would delist more than 16 games of one season (a week's slate) fails and delists
    nothing: that is a broken file, not a schedule change.
- **What delisting means downstream:**
  - A delisted game is never part of a target week and never projected.
  - The job **withdraws** its open rows on its next run, *before* locking, so a game dropped
    before the job saw it kick off never enters the record.
  - Its locked rows stay. A game that kicked off and was then cancelled, as BUF–CIN was, has a
    true pre-game record.
- **Measured.** Replayed from those real file versions in `GameReconciliationTests`. Against
  today's full schedule, reconciling 2020–26 delisted 0 of 1,965 stored games.
- `IntegrityChecks` logs any stat rows on a delisted game. A played game does not leave the
  schedule; when one does, nflverse has most likely re-identified it, and its stats will arrive a
  second time.
- **Stats and rankings: no double count, and no filter (choice B, 2026-10-12).**
  - A stat row's key is `(player_id, game_id)`, from the stats file's own `game_id`, and the
    stats ingest only upserts. So our code would keep one performance twice if the source ever
    carried it under two ids.
  - It never has. nflverse's stats come from the plays of a game as played, under its id at the
    time, and every observed cross-week move happened before kickoff.
  - Measured 2020–26: 0 stats rows under an id the schedule no longer lists, and 0 player-weeks
    with two regular-season rows, in the source and in our database.
  - BUF–CIN has no stats or play-by-play today; its January 2023 state is unknowable.
  - So rankings, the game log and career do not read `delisted_at`. Filtering would only matter
    for stats published and then withdrawn, which has never been observed.
  - `GameReconciliationTests` replays the 2020 PIT–TEN sequence through both ingestors and the
    rankings query (counted once), and pins the unfiltered read.

**Target week W.**

- W is the smallest regular-season week of the current season in which some **listed** game is
  not yet complete. A game is **complete** when its stat rows are stored (the score can lag, as
  ATL–NO did).
- The board rolls forward only when **all** of last week's stats are in: normally the Tuesday
  06:00 ET ingest, otherwise the next one. Until then week N shows with every game locked.
- **Two conditions, two mechanisms:**
  - *The game is no longer on the schedule* (moved or cancelled). It is `delisted_at` and leaves
    the week at once, with no waiting.
  - *The game is on the schedule and its stats are late.* It stops holding the week **48 hours
    after kickoff**, stays listed, and its stats count when they land: the next run regenerates
    every open row from them. Without this, a Monday night game whose stats were still missing at
    Thursday 06:00 would leave Thursday's game never projected, since the last run before it would
    still be on the old week; 48 hours puts Monday night's kickoff behind that run.
  - The 48-hour rule is not how a cancellation is handled. It only bounds the days before
    nflverse deletes the row (BUF–CIN: three).
- **Whether the 48-hour rule is needed** rests on a risk that has not been measured: how often
  Monday night's stats arrive after Thursday 06:00. It costs one condition, and tests pin both
  conditions apart.

**Generation.**

- `ProjectionJob` runs right after the daily ingest (same scheduler) and headless through
  `ingest-once`, both through `ingest.DailyPull`. It runs even after a failed ingest, because it
  reads only what is stored; the ingest's failure is still the one the process exits on.
- **It ships switched off** (`fantasykai.projections.job-enabled=false`, owner decision
  2026-10-12), and is turned on only deliberately, because a row the job writes locks for good at
  kickoff.
  - Slice 3 shipped with an interim rule: a player's week-W team was `players.team_id`, which
    retired and released players keep. Slice 4 replaced it with weekly rosters (§7).
  - **Before switching it on in production**, run one backfill. The daily pull fetches only the
    current season, so `player_game_appearances` would otherwise lack every earlier season's
    zero games.
- It is recorded in `ingest_runs` as `fantasykai.projections`, so `ingestFreshness` covers it.
- It regenerates W's rows for games that haven't kicked off. Kicked-off rows get `locked_at` and
  are never rewritten by the application, which keeps an honest record for live-accuracy
  tracking.
- It is deterministic and idempotent. It recomputes daily because stats can be corrected and
  rosters change. No real-time updates.
- **A corrupt history fails the run, on purpose** (owner decision 2026-10-12). Two games for one
  player in one week violate a domain fact; skipping him would hide the fault and publish an
  incomplete board.
  - The failure is the diagnosis. `CorruptHistoryException` names the player (id and gsis), the
    week, and each conflicting game with where it came from (stat row or appearance).
  - That message is what `ingest_runs.error` and `ingestFreshness` show.
  - There were 0 such player-weeks on 2026-10-12.

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
| Migrations | `V7__projections_and_appearances.sql`, `V8__games_delisted.sql` (both Slice 3), `V9__roster_week_and_injury_report.sql` |
| `api/` | `ProjectionsController`, `ProjectionsService`, `ProjectionBoard`, `ProjectionRow`, `PlayerProjectionResponse`, `ExplainedScore` |
| Frontend | `lib/types.ts`, `lib/api.ts` (hooks), `rankings/FilterBar.tsx`, `RankingsWorkspace.tsx`, `PlayerRow.tsx`, `player/PlayerWorkspace.tsx`, a new `player/ProjectionPanel.tsx`, the info popover |
| Docs and checks | `CLAUDE.md`, `map.md`, north-star brief, `session-check.sh` (endpoint count 13 → 15), `deploy/README.md` (V7–V9; §8 the locked-row repair) |

**Reused:** `StatKey`, `StatLine`, `ScoringEngine`, `ScoringProfiles.byId`, `ScoringPosition`,
`PageResponse`, the `StatColumns` pattern, `IngestRunRecorder`, `CsvValues`, `NflverseClient`.

## 13. Slices (each its own PR, independently testable)

| # | Ships | Done when |
|---|---|---|
| **2** | `projections/` domain, `WeightedAverageProjectionProvider` (pure, no DB) | Hand-computed tests; randomized bit-parity against research baseline D; `backtest.sh parity` exact on every 2024–25 player-week |
| **3** | `V7`, `JdbcProjectionInputs`, `TargetWeekResolver`, `ProjectionJob` (lock at kickoff, `ingest_runs`, headless) | Schema test (all `StatKey` columns, no points column); lifecycle tests (mid-week, MNF stats missing, rollover, bye, after week 18, preseason); a locked row survives a rerun; `OneShotContextTests` with the job. **Built 2026-10-12** on `feat/projections-job`. Also shipped: zero-stat appearances (§15), schedule reconciliation (`V8`), the audited locked-row repair, and the provider-id limit. The job is switched off until Slice 4 |
| **4** | `V9`, `RosterWeekIngestor`, `InjuryIngestor`, `AvailabilityResolver`, roster-team eligibility | Real 2025 rows as fixtures; header check; 404 → `SKIPPED`; Out/reserve/Q/D/unknown resolution; missing data → UNKNOWN, never a failure. **Built 2026-10-12** on `feat/projections-availability`: `AvailabilityRules`/`AvailabilityService` (the resolver), `RosterWeekIngestor`, `InjuryReportIngestor`, real 2024–26 rows; 2025 check reproduced 153 / 0 (§6) |
| **5** | The two endpoints and `ExplainedScore` | 401 anonymous; another member's profile 404 (warm cache); `QuerySafetyTests`; points bit-identical to `ScoringEngine.score`; PPR − 0 PPR = rec; parts sum to total; Out not ranked; states for bye, no history, no week |
| **6** | Board "Next week" and the player panel | lint/test/build; browser-verified at 390 and 1440; other pages unchanged |
| **7** | QA and monitoring | A weekly live-accuracy report (locked rows against actuals); a freshness alert; deploy runbook; acceptance checks; docs |

Slices 3 and 4 can run in parallel; 5 needs both.

## 14. Known risks

1. **Unknowable absences.** Game-day inactives (37 of 406 in 2025) and late changes after the
   06:00 ingest can't be known before kickoff. 164 absences had no flag at all. Since Slice 4,
   the same Saturday-morning clock also misses reserve activations and practice-squad
   elevations made later that day. The 2025 bound is 0 activations and 2 elevations (§6).
2. ~~**No zero-stat games in production history.**~~ Retired in Slice 3: they are stored and
   read (§15). Without them the baseline over-projected fringe players; 2025 baseline bias on P
   was +0.54.
3. **MNF stats lag** delays rollover to Wednesday on some weeks. Since Slice 3 it can delay it no
   further than 48 hours after kickoff (§8).
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
9. **Appearances are upsert-only.** If nflverse removed or corrected a published snap row, its
   appearance would stay, and the player would keep a zero game he did not play. This has not
   been observed, and the evidence is thin, because the release assets keep no history.
   - The 2020–25 snap files were byte-identical on 2026-10-09 and 10-12.
   - None of 2026 weeks 1–4's 5,970 rows was removed or changed over those three days.
   - Deferred. The schedule needed reconciliation because nflverse *does* delete schedule rows
     (§8); snap rows have shown no such behaviour.

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

  **Decided 2026-10-12: (a), in Slice 3.**
  - No new source was needed. `SnapCountIngestor` already downloads the snap file every day, and
    now also writes `player_game_appearances`: a skill position in the file, `offense_snaps > 0`,
    a pfr id and a game that resolve, and a team that is a side of that game.
  - Every appearance is stored, with or without a stat line. "Zero-stat" is decided when it is
    read, so ingest order cannot double-count or drop a game.
  - The stored zero-stat counts reproduce research v2 exactly: 730 / 796 / 806 / 851 / 818 / 687
    in 2020–25, and 158 in 2026 weeks 1–4.
  - Decided before any projection row exists, so `weighted-average-v1` has one meaning from its
    first locked row.


- **Availability at the API and in accuracy tracking.** Decided by the owner on 2026-10-12, to be
  built in the slices named. Not implemented in Slice 4.
  - **Slice 5, the live API, uses current request-time availability.** The answer is the stored
    raw projection, plus current known availability (`AvailabilityService`), plus the member's
    scoring rules.
    - If a player becomes Out after his line was generated, the board stops ranking him at once,
      instead of showing Tuesday's eligibility.
    - The raw projection itself never changes.
  - **Slice 7, live accuracy, snapshots the resolved availability when a projection locks.**
    - `roster_week` and `injury_report` are rewritten to each week's final state, so they cannot
      say what Fantasy-Kai knew before kickoff.
    - The snapshot is for evaluation and audit only, and never replaces request-time availability
      in the product.
    - Request-time state is what we believe now; the lock-time snapshot is what we knew before
      kickoff.
