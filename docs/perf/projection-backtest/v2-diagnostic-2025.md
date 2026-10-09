# Experiment v2 -- diagnostic (2025)

Choices frozen in `frozen-config-v2.json` at commit `24d16d12f4e98c1fc83b7ec4b966958b03b59079`; refit on 2021–2024. **Diagnostic only: nothing is chosen on this season.**

## Data

Local database, regular season only, QB/RB/WR/TE rows. Fingerprint (totals for seasons after 2025 withheld):

- 2020: 5,543 rows · pass_yd 130,405 · rush_yd 60,618 · rec 11,676 · rec_yd 129,810 · targets 17,190
- 2021: 5,866 rows · pass_yd 132,715 · rush_yd 62,374 · rec 12,012 · rec_yd 132,038 · targets 17,924
- 2022: 5,808 rows · pass_yd 126,978 · rush_yd 65,738 · rec 11,525 · rec_yd 126,277 · targets 17,193
- 2023: 5,801 rows · pass_yd 128,459 · rush_yd 61,238 · rec 11,722 · rec_yd 127,961 · targets 17,372
- 2024: 5,864 rows · pass_yd 126,963 · rush_yd 65,062 · rec 11,563 · rec_yd 126,476 · targets 16,920
- 2025: 6,037 rows · pass_yd 122,366 · rush_yd 63,550 · rec 11,130 · rec_yd 121,678 · targets 16,486
- sha256 over 36,355 rows: ac3fe6aee827879bd7f628c49a68036df9fb312cef7bee49ba52df3d4c11e223
- per-season sums match a direct SQL SUM: yes (checked at load)
- v2 dataset -- nflverse files read:
-   stats_player_week_2020.csv: 17,602 rows, sha256 05b992676faccc8940efbf6242cb76358069372d31d7c2e02c1a4acdcd3cbe18
-   snap_counts_2020.csv: 24,999 rows, sha256 512e35f17d076eb5d99b933c4a144eb95dcddf40c0320dbb397cae3c35683716
-   stats_player_week_2021.csv: 18,969 rows, sha256 41915fb49238902ad1f129ebf0405b11a1e710454ae0fe8f7b3e4f9145875f48
-   snap_counts_2021.csv: 26,468 rows, sha256 8e4dae054a4749cf2d4919508d9161a6068fd67509979aefa385bfb3803d0ee5
-   stats_player_week_2022.csv: 18,831 rows, sha256 ad426c3fe5bf1cc30c3f137fdfe96d054e19d400879ee4413129da49fa7b54be
-   snap_counts_2022.csv: 26,381 rows, sha256 0018a4833fbf0f825286c1c27450c6254391b548d6c55bcde728e93e4816815a
-   stats_player_week_2023.csv: 18,643 rows, sha256 f19cb71a5de0dce7fd09376026237c9ee9d5a93fe13815a2ea3ec2d37204cb17
-   snap_counts_2023.csv: 26,540 rows, sha256 303b61aa5c33ffda863f93a750fc14483f397f9187ad502b1ce71e9b516a64c0
-   stats_player_week_2024.csv: 18,983 rows, sha256 3ddc45a84f759aa348ce465ae001752c530575455717657cdfe1f8abfcdb4759
-   snap_counts_2024.csv: 26,615 rows, sha256 a2aa58efe093f8aa0ad5aadf09f81d8ec690a1183bd2dde68d20e7f109a9c335
-   stats_player_week_2025.csv: 19,422 rows, sha256 e5e0615b3d96a3eaebfaee91e55afb4a4e7fe0caf057454177bcd7d6ad4bcfc2
-   snap_counts_2025.csv: 26,613 rows, sha256 3fc2deb0e9ad86d34d4578cb80bb21c95253e088ee22ca028adf46f7485eff1f
- 2020: v2 joins -- 5,543 stored rows matched nflverse, 0 not (air yards NaN); 1 took the week's position; 730 zero-stat appearances added; snap rows not placed: 40 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2021: v2 joins -- 5,866 stored rows matched nflverse, 0 not (air yards NaN); 0 took the week's position; 796 zero-stat appearances added; snap rows not placed: 46 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2022: v2 joins -- 5,808 stored rows matched nflverse, 0 not (air yards NaN); 11 took the week's position; 806 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2023: v2 joins -- 5,801 stored rows matched nflverse, 0 not (air yards NaN); 13 took the week's position; 851 zero-stat appearances added; snap rows not placed: 29 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2024: v2 joins -- 5,864 stored rows matched nflverse, 0 not (air yards NaN); 2 took the week's position; 818 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2025: v2 joins -- 6,037 stored rows matched nflverse, 0 not (air yards NaN); 5 took the week's position; 687 zero-stat appearances added; snap rows not placed: 36 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- games counted as played by their stat rows (score not yet stored): 2026_04_ATL_NO
- nflverse rows with a target or attempt but no air yards: 0
- code: `24d16d1` + uncommitted: backend/src/main/java/com/fantasykai/scoring/RulesetJson.java, backend/src/test/java/com/fantasykai/projection/Evaluation.java, backend/src/test/java/com/fantasykai/projection/ExperimentV2.java
- harness commits since the freeze: none

## Population (v2 dataset)

| Season | Weeks | P (official) | of which zero-stat appearances | P0 | P-all | Debuts excluded |
|---|---:|---:|---:|---:|---:|---:|
| 2021 | 18 | 3,808 | 102 | 3,808 (436 inactive) | 6,490 | 172 |
| 2022 | 18 | 3,794 | 109 | 3,794 (358 inactive) | 6,490 | 124 |
| 2023 | 18 | 3,808 | 105 | 3,808 (320 inactive) | 6,529 | 123 |
| 2024 | 18 | 3,808 | 94 | 3,808 (307 inactive) | 6,557 | 125 |
| 2025 | 18 | 3,807 | 88 | 3,808 (334 inactive) | 6,600 | 124 |

P counts a player who appeared -- a stat row or offensive snaps. Inactive means neither.

## Every method (2025, official population P)

| Method | MAE 0 PPR | MAE Half | MAE PPR | MAE My league | RMSE PPR | Bias PPR | Spearman PPR |
|---|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 4.689 | 5.082 | 5.532 | 5.327 | 7.143 | 0.558 | 0.481 |
| v1 (frozen λ) | 4.544 | 4.930 | 5.374 | 5.159 | 6.925 | 0.344 | 0.514 |
| v1′ | 4.541 | 4.927 | 5.371 | 5.156 | 6.921 | 0.345 | 0.514 |
| v2a: + air yards | 4.542 | 4.928 | 5.373 | 5.157 | 6.923 | 0.359 | 0.514 |
| v2b: + air yards + environment | 4.548 | 4.938 | 5.384 | 5.166 | 6.928 | 0.402 | 0.513 |

## Each model against D EWMA h=4 (PPR, P, week-resampled 95% CI)

| Model | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| v1 (frozen λ) | 3,807 | 5.532 | 5.374 | -0.157 | [-0.208, -0.108] | +2.8% |
| v1′ | 3,807 | 5.532 | 5.371 | -0.161 | [-0.211, -0.113] | +2.9% |
| v2a: + air yards | 3,807 | 5.532 | 5.373 | -0.158 | [-0.209, -0.111] | +2.9% |
| v2b: + air yards + environment | 3,807 | 5.532 | 5.384 | -0.148 | [-0.198, -0.099] | +2.7% |

Candidate: **v1′**.

### By position and by time -- improvement over the baseline, PPR

| Slice | n | Baseline MAE | v1 frozen | v1′ | v2a | v2b | Candidate CI |
|---|---:|---:|---:|---:|---:|---:|---:|
| QB | 543 | 6.798 | +3.1% | +3.4% | +3.4% | +3.1% | [-0.395, -0.045] |
| RB | 1,088 | 5.460 | +3.2% | +3.3% | +3.2% | +3.3% | [-0.271, -0.084] |
| WR | 1,632 | 5.262 | +2.2% | +2.2% | +2.2% | +1.9% | [-0.211, -0.032] |
| TE | 544 | 5.221 | +3.7% | +3.7% | +3.4% | +3.1% | [-0.320, -0.061] |
| Weeks 1–3 | 671 | 5.518 | +5.7% | +5.7% | +5.6% | +5.5% | [-0.376, -0.209] |
| Weeks 4–9 | 1,218 | 5.593 | +2.5% | +2.5% | +2.5% | +2.3% | [-0.214, -0.087] |
| Weeks 10–18 | 1,918 | 5.498 | +2.1% | +2.2% | +2.1% | +1.9% | [-0.162, -0.071] |

### The candidate under every league

| League | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| 0 PPR | 3,807 | 4.689 | 4.541 | -0.148 | [-0.188, -0.109] | +3.2% |
| Half PPR | 3,807 | 5.082 | 4.927 | -0.156 | [-0.199, -0.113] | +3.1% |
| PPR | 3,807 | 5.532 | 5.371 | -0.161 | [-0.211, -0.113] | +2.9% |
| My league | 3,807 | 5.327 | 5.156 | -0.171 | [-0.217, -0.126] | +3.2% |

### The pre-registered ship rule, on the candidate (informative only here)

| # | Criterion | Measured | Result |
|---|---:|---:|---:|
| 1 | PPR MAE CI entirely below 0 | [-0.211, -0.113] | **pass** |
| 2 | Improvement ≥ +3.0% | +2.9% | **FAIL** |
| 3 | No position's CI entirely above 0 | QB -0.395, RB -0.271, WR -0.211, TE -0.320 | **pass** |
| 4 | Absolute bias ≤ 0.5 PPR pts | 0.345 | **pass** |

**Not all four pass.** Weekly clusters in this season: 18.

### How fragile is the candidate's result? (report-only, added after the 2026 run)

| Check | Value |
|---|---:|
| Weeks (clusters) | 18 |
| Mean weekly diff, t interval (df 17) | -0.159 [-0.211, -0.107] = +2.9% [+1.9%, +3.8%] |
| Leave one week out: improvement range | +2.7% (without week 1) to +3.1% (without week 18) |
| Weeks 1–4 only | +5.6% (n 895) |

## Receiving stats -- where air yards should act (RMSE, P)

| Pos | Stat | Baseline | v1′ | v2a | v2b | v2a better than v1′ by |
|---|---:|---:|---:|---:|---:|---:|
| RB | targets | 1.886 | 1.836 | 1.838 | 1.839 | -0.1% |
| RB | rec | 1.603 | 1.548 | 1.550 | 1.549 | -0.1% |
| RB | rec_yd | 16.712 | 16.107 | 16.178 | 16.168 | -0.4% |
| RB | rec_td | 0.310 | 0.296 | 0.296 | 0.296 | +0.0% |
| WR | targets | 2.796 | 2.750 | 2.750 | 2.754 | +0.0% |
| WR | rec | 2.046 | 2.009 | 2.009 | 2.017 | +0.0% |
| WR | rec_yd | 31.824 | 31.371 | 31.357 | 31.423 | +0.0% |
| WR | rec_td | 0.508 | 0.488 | 0.487 | 0.487 | +0.1% |
| TE | targets | 2.636 | 2.543 | 2.557 | 2.559 | -0.5% |
| TE | rec | 2.204 | 2.131 | 2.146 | 2.147 | -0.7% |
| TE | rec_yd | 27.523 | 26.629 | 26.680 | 26.678 | -0.2% |
| TE | rec_td | 0.556 | 0.532 | 0.532 | 0.533 | -0.0% |

## Availability -- diagnostic, never the acceptance population

| Method | n all / appeared / inactive | MAE all projected | MAE appeared | MAE inactive | Inactive share of error | Knowing who is active would remove |
|---|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 3,808 / 3,474 / 334 | 5.856 | 5.553 | 9.009 | 13.5% | 0.790 |
| v1′ | 3,808 / 3,474 / 334 | 5.650 | 5.386 | 8.404 | 13.0% | 0.737 |

All projected = P0: the top players by trailing opportunity who played their team's previous game, before kickoff. An inactive player scores 0, so all of his projection is error.

## Bias -- does the environment term remove the seasonal swing?

| Method | PPR bias | PPR median error | QB | RB | WR | TE | QB pass_yd | WR rec_yd |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 0.558 | 1.694 | 0.637 | 0.275 | 0.774 | 0.396 | 7.414 | 2.987 |
| v1 (frozen λ) | 0.344 | 1.576 | 0.452 | 0.064 | 0.567 | 0.126 | 11.421 | 2.357 |
| v1′ | 0.345 | 1.563 | 0.463 | 0.063 | 0.567 | 0.126 | 11.686 | 2.357 |
| v2a: + air yards | 0.359 | 1.571 | 0.463 | 0.066 | 0.579 | 0.184 | 11.686 | 2.380 |
| v2b: + air yards + environment | 0.402 | 1.645 | 0.500 | 0.026 | 0.668 | 0.256 | 12.973 | 3.338 |

Bias is the mean of projected minus actual; the median error is its median. Position columns are PPR bias; the last two are raw-stat bias in yards.

