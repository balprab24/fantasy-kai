# Trade model calibration — league plan S3's pre-registered gate, 2026-10-30

**Result: FAILED. Trades is held** (owner decision 5, 2026-10-29: no public beta fallback).

Does the weekly win probability — production's projection, `ProjectionUncertainty`'s σ and
the quarterback-receiver stack correlation (`leagues/WinProbability`) — predict who wins a
fantasy matchup as often as it says? Reproduce with `./scripts/backtest.sh calibration`
(`projection.WinCalibration`).

## The bar, fixed before running

From the plan's S3 section, committed with the plan before this harness existed:
- a Brier score at least 10% below the constant-0.5 baseline (0.25), so **at most 0.225**;
- **and** a reliability slope within **[0.8, 1.2]** across 10 bins.

## Method (every choice a constant in the harness before its first run)

- **Seasons and leagues:**
  - 2021–2025, with 20 synthetic 12-team leagues a season;
  - regular weeks 1–14, scored Half PPR;
  - 8,400 matchups.
- **Pool:** skill players with at least 4 games the previous season and a game this season.
  That is a stated mild leak: it drops retirees, as a real draft would, but also anyone hurt all
  year.
- **Draft:**
  - a 13-round snake on last season's points per game, times 1 + N(0, 0.15) per league;
  - caps of 2 QB, 5 RB, 6 WR and 2 TE;
  - every team fills its starting positions.
- **Lineups:** 1 QB, 2 RB, 2 WR, 1 TE and 1 flex, chosen each week by `LineupOptimizer` on
  production's projection, cut at the week by `PlayerHistory.before`.
- **What a manager knows before kickoff:**
  - The schedule is used: a bye projects 0.
  - Injury designations are **not** used, because this dataset does not carry them. A starter
    who sits scores 0, so the model is tested without the availability rules production applies.
- **Scoring:** random pairings each week; a tie counts 0.5.
- **In-sample term:** σ (two numbers a position) was fitted on these same seasons. The
  projections are out of sample.

**Run:** the first and only one, at `619961d` plus the uncommitted S3 harness. The harness and
model hashes are printed in the report: `WinCalibration.java f5700d3c927a`,
`WinProbability.java c8207d3b585a`, `ProjectionUncertainty.java dff5555a3db9`,
`LineupOptimizer.java 43f39c3ff6be`.

## Results

| Season | Matchups | Brier |
|---|---:|---:|
| 2021 | 1,680 | 0.2371 |
| 2022 | 1,680 | 0.2280 |
| 2023 | 1,680 | 0.2329 |
| 2024 | 1,680 | 0.2394 |
| 2025 | 1,680 | 0.2287 |
| **All** | **8,400** | **0.2332** |

The baseline (always 0.5) is 0.2500, so the model is **6.7% better. The bar is 10%: fail.**

| Predicted | Matchups | Mean predicted | Observed |
|---|---:|---:|---:|
| 0.0–0.1 | 25 | 0.074 | 0.120 |
| 0.1–0.2 | 198 | 0.165 | 0.202 |
| 0.2–0.3 | 583 | 0.258 | 0.324 |
| 0.3–0.4 | 1,390 | 0.354 | 0.363 |
| 0.4–0.5 | 2,021 | 0.451 | 0.459 |
| 0.5–0.6 | 1,947 | 0.549 | 0.542 |
| 0.6–0.7 | 1,386 | 0.646 | 0.640 |
| 0.7–0.8 | 627 | 0.741 | 0.696 |
| 0.8–0.9 | 200 | 0.836 | 0.710 |
| 0.9–1.0 | 23 | 0.936 | 1.000 |

**Reliability slope: 0.837** (intercept 0.082), weighted by count over non-empty bins. The bar
is 0.8–1.2: **pass.**

## What it says

- **The odds are honest where nearly all matchups sit** (0.3–0.7, 80% of games): predicted and
  observed agree within about 0.01.
- **They are too confident at the ends.** At 0.8–0.9 the favourite won 71% of the time, not 84%;
  at 0.2–0.3 the underdog won 32%, not 26%. That is the shape of variance left out: injuries the
  harness cannot see, K and DST, correlations across a game.
- **The Brier bar fails on sharpness, not honesty.** *(Diagnostic added after seeing the result,
  and not part of the gate.)* A model that is perfectly calibrated at these predictions' own
  confidence would score about Σp(1−p)/n ≈ **0.227** (from the bin means; an approximation that
  ignores spread within bins), still above 0.225. Passing needs predictions that are sharper and
  still right, which means a better projection or more information, not a recalibration.

## Decision

Held, as pre-registered. Neither the harness nor the bar is changed after seeing this; either
change is a new, separately pre-registered attempt and the owner's call. Candidates for that
attempt are listed for the owner, not chosen here:
- give the harness what production knows (weekly rosters and injury reports for 2021–2025);
- add K and DST variance;
- improve the projection itself (the research backlog in `projection-accuracy.md`);
- revisit whether a 10% Brier gain is attainable for weekly fantasy matchups in balanced leagues
  at all.

That last one is an argument about the bar, made after seeing it fail. It is recorded as such.

---

# Attempt 2 — pre-registered 2026-10-30, before any code for it was written or run

The owner chose a second attempt (2026-10-30) that gives the harness what production actually
knows, against **the same bar**: Brier ≤ 0.225 **and** reliability slope in [0.8, 1.2], 10 bins.
Fail either and Trades stays held. Run as `./scripts/backtest.sh calibration-v2`.
`calibration` keeps reproducing attempt 1 unchanged.

**Unchanged from attempt 1:**
- seasons, seeds, the pool, the skill draft, caps, weeks 1–14, Half PPR;
- the skill lineup (1 QB, 2 RB, 2 WR, 1 TE, 1 flex) and random weekly pairings;
- production's projection and σ fit, and the 0.37 stack correlation.

**Changed, and nothing else:**
1. **Availability, through production's own `AvailabilityRules.classify`.**
   - A skill player projects 0 in week *w* when classified OUT, RESERVE or NOT_ON_ROSTER from:
     - **week *w*'s injury report** (pregame by nature), and
     - **week *w − 1*'s roster status** (nflverse's weekly roster is each week's *final*
       snapshot, so this week's would leak mid-week activations).
   - Week 1 has no previous roster, so it uses the report alone.
   - Stated cost: a player activated from reserve during the week is projected 0 when he plays.
   - Source: nflverse `roster_weekly_YYYY.csv` and `injuries_YYYY.csv`, 2021–2025, matched on
     `gsis_id`, REG rows only. As in `AvailabilityBacktest`, the most restrictive of two reports
     in a week wins.
2. **K and DST, as production treats them.**
   - Every team also starts 1 K and 1 DST, assigned at random (seeded), distinct within a
     league. K comes from the pool of kickers with 4+ games the previous season and a game this
     season; DST from all 32 teams.
   - On a bye, a team streams: it starts a random K or DST, from those playing that week.
   - **Scored** with the captured Sleeper league's defaults:
     - K: FG made 0–39 3, 40–49 4, 50+ 5, FG missed −1, PAT 1, PAT missed −1;
     - DST: sack 1, interception 2, fumble recovered 2, safety 2, defensive TD 6, forced fumble 1,
       blocked kick 2, and points allowed 0 → 10, 1–6 → 7, 7–13 → 4, 14–20 → 1, 21–27 → 0,
       28–34 → −1, 35+ → −4.
     - A DST's stats are its players' summed defensive columns; points allowed is the opponent's
       final score.
   - **Modelled** as production will: one constant mean for every team, which cancels, and a
     constant variance. σ_K and σ_DST are the standard deviation of every K-week and DST-week
     2021–2025 around their overall mean, measured in this run. That is in-sample, like the σ fit.
   - `WinProbability` gains this as added variance. Production adopts the measured constants only
     if this attempt passes.

**Fixed as of writing:** the files to be added or changed are `projection/WinCalibration.java`
(a v2 mode), `leagues/WinProbability.java` (the added variance), `scripts/backtest.sh` (the stage
and its nflverse downloads) and `projection/Backtest.java` (the stage). The report prints their
hashes. This attempt's first run is its result, whatever it shows.

## Attempt 2 — result: FAILED, narrowly. Trades stays held.

**Run:** the first and only one, at `fb90be5` (the pre-registration commit) plus the attempt-2
code. Hashes: `WinCalibration.java d0344170cef8`, `WinProbability.java 622766a79c68`,
`ProjectionUncertainty.java dff5555a3db9`, `LineupOptimizer.java 43f39c3ff6be`,
`Backtest.java 100fbfdae95f`, `backtest.sh 9dadacb98b5d`. nflverse files (sha256 prefixes) are in
the report.

**Reproducibility:** before this run, `calibration` (attempt 1) was rerun on the new code and its
report matched attempt 1's line for line, apart from the head. The attempt-2 changes leave
attempt 1's path untouched.

**Measured inputs:**
- availability set 25,182 skill candidate-weeks to 0, counted per league;
- σ_K = 4.675 over 2,716 kicker-weeks;
- σ_DST = 5.743 over 2,718 team-weeks.

| Season | Matchups | Brier |
|---|---:|---:|
| 2021 | 1,680 | 0.2276 |
| 2022 | 1,680 | 0.2292 |
| 2023 | 1,680 | 0.2235 |
| 2024 | 1,680 | 0.2304 |
| 2025 | 1,680 | 0.2217 |
| **All** | **8,400** | **0.2265** |

That is **9.4% better than the 0.25 baseline. The bar is 10% (≤ 0.225): fail, by 0.0015.**

| Predicted | Matchups | Mean predicted | Observed |
|---|---:|---:|---:|
| 0.0–0.1 | 71 | 0.065 | 0.056 |
| 0.1–0.2 | 301 | 0.157 | 0.150 |
| 0.2–0.3 | 660 | 0.256 | 0.259 |
| 0.3–0.4 | 1,331 | 0.355 | 0.381 |
| 0.4–0.5 | 1,800 | 0.452 | 0.460 |
| 0.5–0.6 | 1,854 | 0.549 | 0.540 |
| 0.6–0.7 | 1,294 | 0.646 | 0.628 |
| 0.7–0.8 | 697 | 0.742 | 0.697 |
| 0.8–0.9 | 305 | 0.842 | 0.748 |
| 0.9–1.0 | 87 | 0.929 | 0.920 |

**Reliability slope: 0.889** (intercept 0.049). **Pass.**

**What changed from attempt 1:**
- **Sharper:** 158 matchups predicted below 0.1 or above 0.9, against 48.
- **Better calibrated at the ends:** 0.9–1.0 predicted 0.929, observed 0.920.
- **Still too confident in the 0.7–0.9 band:** 0.842 predicted, 0.748 observed.

In two seasons out of five the score clears the bar (2023 0.2235, 2025 0.2217). Over all five it
does not.

**Decision:** held, as pre-registered. Two attempts have now been run against this bar. Running
further variants until one passes would turn the bar into a search, so any third attempt, or any
change to the bar, is the owner's decision. It should be pre-registered here before it runs, and
this record should count it as the third look.

---

# Attempt 3 — pre-registered 2026-10-30, before any code for it was written or run

The owner chose a third attempt (2026-10-30): a real model change, **fitted on 2021–2023 and
tested on 2024–2025 only**. This is the third look at the gate, and this record counts it so.

**Why a calibration change has room to work** *(a diagnostic from attempt 2's reported bins,
stated before running)*:
- At attempt 2's own confidence, a perfectly calibrated model would score about Σp(1−p)/n ≈
  **0.221**, from the bin means, an approximation. That is under the bar.
- Attempt 1's equivalent was about 0.227, over it.
- Attempt 2's miss is mostly overconfidence: its reliability slope is 0.889, and at 0.8–0.9 it
  predicted 0.842 against 0.748 observed.

**The model change: one spread multiplier, k.**
- P(A beats B) = Φ(gap / (k · √(Var_A + Var_B))).
- k > 1 widens every forecast spread and pulls predictions toward 0.5.
- It is one number, fitted, not chosen.

**Everything is fitted on the training seasons, 2021–2023, and nothing on 2024–2025:**
1. the skill σ = a + b·μ per position, by `UncertaintyFit`'s method restricted to 2021–2023;
2. σ_K and σ_DST, attempt 2's measurement restricted to 2021–2023;
3. **k**, by grid search over 1.00, 1.05, …, 2.00, minimising Brier on the 2021–2023 synthetic
   leagues (attempt 2's leagues, those seasons only). Ties go to the smaller k.

**The test, unchanged in every other respect.**
- Attempt 2's harness: availability, K/DST, seeds, drafts, lineups, pairings and Half PPR.
- Run on **2024–2025 only**: 2 × 20 leagues × 14 weeks × 6, so 3,360 matchups.
- The test seasons are the two most recent, a temporal holdout. Attempt 2 had already shown
  them at 0.2304 and 0.2217 (mean ≈ 0.2261, a fail), so choosing them does not pass by itself.

**The bar: unchanged.** Brier ≤ 0.225 **and** reliability slope in [0.8, 1.2] across 10 bins, on
the 2024–2025 matchups. Fail either and Trades stays held.

**If it passes, production adopts exactly the tested configuration**, without refitting on more
data: the 2021–2023 skill σ fit, σ_K, σ_DST and k. That replaces the 2021–2025 σ constants now in
`ProjectionUncertainty`. No untested combination ships.

**Files, fixed as of writing:**
- `projection/WinCalibration.java`: a v3 mode, plus training and test seasons;
- `projection/UncertaintyFit.java`: its fit, callable for a season subset; its own stage's
  output must stay identical;
- `leagues/WinProbability.java`: k, and an injectable σ;
- `projection/Backtest.java` and `scripts/backtest.sh`: the stage `calibration-v3`.

Attempts 1 and 2 must still reproduce line for line. This attempt's first run is its result.
