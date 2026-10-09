# Projection accuracy — Phase 6, Slice 1

**The question.** Can fantasy-kai project next week's *raw stat lines* well enough to beat simple
baselines, and are the fantasy points those lines score accurate under real rulesets?

**Status.** Validation (2024) is complete and its choices are frozen in
[`frozen-config.json`](../../backend/src/test/resources/projection/frozen-config.json). The test
season (2025) **has not been read**: `scripts/backtest.sh test` refuses to run until that file is
committed and unmodified. The commit that freezes it is the pre-registration; the test results
and the verdict land in a later commit.

North-star calls the projection model "a hypothesis until this file exists". This file now
exists, and the hypothesis is still untested on data it did not choose against.

## Reproduce

```bash
docker compose up -d                  # the local Postgres on :5433
./scripts/backtest.sh validate        # ~6 s; writes backend/target/backtest/validate-results.md
./scripts/backtest.sh test            # only once frozen-config.json is committed
./scripts/backtest.sh prospective     # 2026 so far, same frozen choices
cd backend && ./mvnw -B verify        # the 21 unit tests, including the leakage mutation targets
```

Generated reports are copied into [`projection-backtest/`](projection-backtest/). Two runs on
the same data give byte-identical reports (checked, runs 4 and 5 below). Every report opens with
the data fingerprint it was computed from.

## Method

### Data: only what the pipeline already ingests

The local database, regular season only (`games.season_type = 'REG'`), QB/RB/WR/TE rows of
`player_game_stats`. It uses the 13 `StatKey` columns, `pass_att`, `rush_att`, `targets`,
`snap_pct`, punt and kick returns, every team's per-game volume (summed over all positions), and
the schedule with `spread_line` and `total_line`.

| | |
|---|---|
| Fingerprint | sha256 `ac3fe6ae…4c11e223` over 36,355 skill rows, 2020–2026. Per-season sums are checked against a direct SQL `SUM` at load |
| Lines | 0 of 1,615 regular-season games in 2020–25 lack a spread or total |
| Not used | Air yards, target share, WOPR (nflverse has them, we don't ingest them), injuries, routes. The winner has to be buildable in production without a new source |

### Split: time-ordered, test read once

| Role | Seasons | Use |
|---|---|---|
| History | 2020 | Features for 2021 week 1. Never a label |
| Train | 2021–2023 | Ridge coefficients, positional priors, rare-stat shrinkage, bonus bins |
| Validate | 2024 | **Every** choice: windows, half-life, λ, feature groups, pooling, which baseline is best |
| Test | 2025 | Frozen choices, refit on 2021–24, read once |
| Prospective | 2026 wk 1–4 | Frozen choices, refit on 2021–25. Local mirror as of 2026-10-07 |

### Population: pinned before any method runs

The population moves MAE more than any weight does (north-star brief). It is defined from
pre-game information only and is the same for every method. Players are ranked by **trailing
opportunity**: mean targets + carries over the last four games, or attempts + carries for a QB.
Because that ranking is not any candidate's projection, no method can tilt its own grading.

- **P (primary).** Per week: top QB 32 / RB 64 / WR 96 / TE 32, scaled by the teams playing. A
  player needs at least one prior game and a stored row that week. **P is conditional on
  playing.**
- **P0 (availability).** The same ranking among players who appeared in their team's previous
  game. Someone who then sits counts as a zero line. P0 − P is roughly what not knowing who is
  active costs, which is the most an injury input could ever recover.
- **P-all.** Every player with history and a stored row: the board's population. Headline
  numbers only.

| Case | Handling | 2024 count |
|---|---|---:|
| Debut (no prior game) | Not projected, counted | 118 |
| Played for a new team (offseason or trade) | History follows the player. Team features use the week-N team: a roster fact, public before kickoff | 173 |
| Did not play, in P0 | Zero line | 398 |
| Bye, or the cancelled 2022 W17 BUF–CIN game | No projection. History is counted in games, not weeks | — |
| Active, zero stats (snap-only) | **Not stored by our ingest.** Measured on 2024: 849 skill player-games had snaps and no stat row (13%, mostly blocking TEs). Among players averaging ≥3 or ≥5 opportunities per game it is 3.1% or 1.2%. The bias is optimistic, so every MAE here is a little flattering, and it is the same for every method | — |
| Missing `snap_pct` | Left out of the snap EWMA, never read as 0. Positional mean if a player has none | ≤13 rows/season |
| Partial games (left injured) | Kept as they are. A down-weighting experiment is a candidate | — |

### What is projected: 13 `StatKey` stats + 3 usage counts, each under an explicit rule

| Rule | Applies to |
|---|---|
| **Ridge regression** per (position, stat), 24 models | QB: pass_att, pass_yd, pass_td, pass_int, rush_att, rush_yd, rush_td · RB: rush_att, rush_yd, rush_td, targets, rec, rec_yd, rec_td · WR: targets, rec, rec_yd, rec_td, rush_att, rush_yd · TE: targets, rec, rec_yd, rec_td |
| **Shrunk player rate**: long-weighted per-game rate, blended with the positional mean (pseudo-games fitted on training rows) | Every other (position, stat) pair, e.g. QB receiving, WR rush_td, TE rushing, non-QB passing |
| **Positional rate × projected touches** | fum_lost (per pass att + carry + catch), pass_2pt, rush_2pt, rec_2pt |
| **Positional rate per return × return volume** | ret_td |

`pass_cmp` is not projected because nothing scores it. Nothing is silently zero-filled. The
output is a stat line and never a point value; points come from the unmodified `ScoringEngine`.

### Baselines: the player's own history and nothing else

Each baseline projects every quantity and is scored through the same `ScoringEngine` over the
same population.

- **A, previous game.** Crosses seasons, so week 1 uses last season's finale.
- **B, last k games.** k ∈ {3, 4, 5}.
- **C, season to date.** Before his first game of the season, falls back to last season's.
- **D, EWMA across games.** Half-life h ∈ {1, 2, 3, 4, 6, 8}.

The best of A, B and C, and D at its best h, picked on 2024, is what the model must beat.

### Model v1

Ridge regression on standardized columns, solved directly by Cholesky (no library). Each
(position, stat) model takes nested feature groups, all computed as of week N−1:

| Group | Adds |
|---|---|
| G1, own history | EWMA, season-to-date, last game and prior season of the stat; EWMA usage (attempts, carries, targets); snap share; games played; opportunity × shrunk efficiency (e.g. `ewma:targets × yards_per_target`) |
| G2, team | Target, carry and attempt share; the team's EWMA volume; share × team volume |
| G3, line | Implied team total `(total ± spread)/2`, spread from the team's side, home; the stat's EWMA × implied total and × spread |
| G4, opponent | What this week's opponent allowed to the position, per stat, as a multiple of the league mean (shrunk with 4 pseudo-games) |

λ ∈ {0.001, 0.01, 0.1, 1, 10} is picked per model on 2024 by RMSE of its own stat. Predictions
are clamped at 0. Every efficiency is shrunk toward the positional rate (pseudo-counts in
`Rate.java`, fixed and not tuned).

**Complexity has to earn its place.** Start at G1, per position. A richer group, or pooling a
family across positions (receiving RB/WR/TE, rushing QB/RB/WR), is adopted only if its paired
PPR error against the current choice has a week-resampled 95% CI **entirely below 0** on 2024.

### Leakage protections

1. **One door.** Every feature reads through `Timeline.asOf(season, week)`, which returns games
   strictly before week N. That covers the player's history, his team's volume and his
   opponent's allowed stats.
2. **The label stays separate.** Week N supplies only the label (read by the evaluator, never
   passed to the feature builder), the team (roster), the opponent and home side (schedule), and
   the line.
3. **Fitted on the past.** Priors, standardization, λ, shrinkage and bonus bins come from
   seasons before the evaluated one.
4. **Proven by mutation.** `LeakageTests` builds one league twice, once with weeks 4–6 full of
   9999 sentinels, and requires week 4's features, baselines and population to be identical.
   Changing `asOf`'s `<` to `<=` failed all 4 leakage tests, 2 baseline tests and 3 population
   tests. Changing the population's team history to `asOf(season, week + 1)` failed 2.
5. **Known residual leaks, not hidden.**
   - `players.position` is today's position, an anachronism for converted players.
   - nflverse keeps one line per game, very likely the closing line. A Wednesday projection
     would not have it; G1 vs G3 is how much of the gain depends on it.
   - P conditions on playing.

### Threshold bonuses: around the engine, never inside it

`Bonus(stat, gte, points)` is all-or-nothing in `ScoringEngine.score` (`values[stat] >= gte`).
No preset uses one, but the ruleset builder lets members add them.

```
expected = ScoringEngine.score(mean line, same rules without bonuses)
         + Σ bonus.points × P(stat ≥ gte)
```

`ExpectedPoints` lives in the projection package, and `ScoringEngine` is unchanged. With no
bonuses it returns `ScoringEngine.score(mean, rules)` itself, bit for bit
(`ExpectedPointsTests`). P comes from `BonusOdds`: the training rows' projections split into 10
equal-count bins, each keeping the actual results it saw. No distribution is assumed.

### Metrics and the pre-registered ship rule

- MAE, RMSE and bias, unrounded, over P.
- Fantasy points under **0 PPR, Half PPR, PPR** (the seeded presets, read from
  `scoring_profiles` and hash-checked against `ScoringProfiles.preset`) and **My league** (Half
  PPR + 6-pt passing TD).
- Spearman rank correlation within each position-week. It is reported, never optimized.
- One model is trained once. The rulesets only change how its line is scored.

**Ship to 6c only if, on 2025, all four hold** (fixed in code before the test season was read:
`Backtest.MIN_RELATIVE_IMPROVEMENT`, `MAX_ABS_BIAS`):

1. The paired PPR MAE difference against the best baseline has a 95% CI entirely below 0.
   2,000 resamples of whole weeks, seed 20261009. Weeks, because errors in one week share its
   games.
2. The improvement is **≥ 3%**.
3. No position's CI lies entirely above 0.
4. |bias| ≤ 0.5 PPR points.

Otherwise the brief's rule applies: the baseline ships, labelled as one, or the named next
experiment runs.

## Validation, 2024: every choice was made here

These numbers are optimistic by construction. The full generated report is
[`projection-backtest/validate-2024.md`](projection-backtest/validate-2024.md).

**Chosen:**

- **Best baseline: D, EWMA with half-life 4 games.** PPR MAE 5.480. Next best: C 5.560, B(5)
  5.697, A 6.915.
- **Model: G3, every family per position.**

| Challenger | Against | Diff in PPR abs. error | 95% CI | Adopted |
|---|---|---:|---:|---|
| G2 (team shares) | G1 | +0.006 | [−0.005, 0.016] | no |
| G3 (the line) | G1 | −0.028 | [−0.058, −0.000] | yes, by the smallest margin |
| G4 (the opponent) | G3 | −0.002 | [−0.011, 0.008] | no |
| Receiving pooled | G3 | +0.006 | [−0.008, 0.022] | no |
| Rushing pooled | G3 | −0.004 | [−0.016, 0.006] | no |

| 2024, P | Best baseline (D) | Model (G3) | Diff, 95% CI | Improvement |
|---|---:|---:|---:|---:|
| PPR MAE, all | 5.480 | 5.343 | −0.137 [−0.218, −0.060] | +2.5% |
| QB | 6.507 | 6.280 | −0.227 [−0.386, −0.076] | +3.5% |
| RB | 5.149 | 5.062 | −0.087 [−0.198, 0.022] | +1.7% |
| WR | 5.578 | 5.451 | −0.126 [−0.228, −0.027] | +2.3% |
| TE | 4.819 | 4.643 | −0.177 [−0.383, −0.007] | +3.7% |
| Weeks 1–3 | 5.471 | 5.141 | −0.330 [−0.473, −0.094] | +6.0% |
| Weeks 10–18 | 5.471 | 5.410 | −0.062 [−0.152, 0.003] | +1.1% |
| 0 PPR · Half · My league | 4.579 · 5.003 · 5.224 | 4.455 · 4.872 · 5.092 | all CIs below 0 | +2.7% · +2.6% · +2.5% |

**On its own tuning season the model already fails criterion 2** (+2.5% < 3%) while passing 1, 3
and 4 (bias −0.253). Expect 2025 to land near that line.

### Every validate run, and what changed between them

The test season was never read during any of these runs.

| Run | Change | What it showed |
|---|---|---|
| 1 | G1–G3. Shrinkage grid up to 64. Choice by minimum PPR MAE | G3 with **pooled rushing**, which made WR rush_att RMSE 12.7% *worse* than the baseline. Shrinkage hit the grid edge (64) on 10 of 20 pairs |
| 2 | Added G4 (the plan's optional group). Shrinkage grid up to 1024 | Minimum MAE picked G4 + pooled rushing on a 0.003 edge, while G4's RMSE was *worse* than G3's. That is choosing noise |
| 3 | Selection replaced by the CI-gated rule above, which is the owner's "evidence that sophistication helps" | G3, per position. WR rushing now beats the baseline (rush_yd RMSE +3.6%) |
| 4, 5 | No model change. `games` loaded into an ordered map | Byte-identical to each other and to run 3: the result is deterministic |
| 6 | Report text only: a `\|Bias\|` cell broke a Markdown table | One line differs |

## Test, 2025

*Not yet run. It lands in the commit after the freeze.*
