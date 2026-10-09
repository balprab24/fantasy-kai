# Experiment v2 -- prospective (2026)

Choices frozen in `frozen-config-v2.json` at commit `24d16d12f4e98c1fc83b7ec4b966958b03b59079`; refit on 2021–2025. **This is the acceptance test.**

## Data

Local database, regular season only, QB/RB/WR/TE rows. Fingerprint (totals for seasons after 2026 withheld):

- 2020: 5,543 rows · pass_yd 130,405 · rush_yd 60,618 · rec 11,676 · rec_yd 129,810 · targets 17,190
- 2021: 5,866 rows · pass_yd 132,715 · rush_yd 62,374 · rec 12,012 · rec_yd 132,038 · targets 17,924
- 2022: 5,808 rows · pass_yd 126,978 · rush_yd 65,738 · rec 11,525 · rec_yd 126,277 · targets 17,193
- 2023: 5,801 rows · pass_yd 128,459 · rush_yd 61,238 · rec 11,722 · rec_yd 127,961 · targets 17,372
- 2024: 5,864 rows · pass_yd 126,963 · rush_yd 65,062 · rec 11,563 · rec_yd 126,476 · targets 16,920
- 2025: 6,037 rows · pass_yd 122,366 · rush_yd 63,550 · rec 11,130 · rec_yd 121,678 · targets 16,486
- 2026: 1,436 rows · pass_yd 30,027 · rush_yd 14,242 · rec 2,698 · rec_yd 29,903 · targets 3,987
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
-   stats_player_week_2026.csv: 4,517 rows, sha256 8304933b6ee2bc2ed132a8932cd579e479ebb5477deb677487544cc51bf67606
-   snap_counts_2026.csv: 6,064 rows, sha256 fd2656dbe119dfe49f67e3df980b35b1f999ee49b76aeb5fb89797dea95379c6
- 2020: v2 joins -- 5,543 stored rows matched nflverse, 0 not (air yards NaN); 1 took the week's position; 730 zero-stat appearances added; snap rows not placed: 40 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2021: v2 joins -- 5,866 stored rows matched nflverse, 0 not (air yards NaN); 0 took the week's position; 796 zero-stat appearances added; snap rows not placed: 46 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2022: v2 joins -- 5,808 stored rows matched nflverse, 0 not (air yards NaN); 11 took the week's position; 806 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2023: v2 joins -- 5,801 stored rows matched nflverse, 0 not (air yards NaN); 13 took the week's position; 851 zero-stat appearances added; snap rows not placed: 29 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2024: v2 joins -- 5,864 stored rows matched nflverse, 0 not (air yards NaN); 2 took the week's position; 818 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2025: v2 joins -- 6,037 stored rows matched nflverse, 0 not (air yards NaN); 5 took the week's position; 687 zero-stat appearances added; snap rows not placed: 36 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2026: v2 joins -- 1,436 stored rows matched nflverse, 0 not (air yards NaN); 0 took the week's position; 158 zero-stat appearances added; snap rows not placed: 7 unknown pfr id, 0 team not a side, 24 unfinished game; 0 team-games without passing air yards
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
| 2026 | 4 | 896 | 33 | 896 (104 inactive) | 1,518 | 76 |

P counts a player who appeared -- a stat row or offensive snaps. Inactive means neither.

## Every method (2026, official population P)

| Method | MAE 0 PPR | MAE Half | MAE PPR | MAE My league | RMSE PPR | Bias PPR | Spearman PPR |
|---|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 4.741 | 5.195 | 5.688 | 5.409 | 7.322 | 0.300 | 0.454 |
| v1 (frozen λ) | 4.556 | 5.000 | 5.503 | 5.220 | 7.187 | -0.283 | 0.506 |
| v1′ | 4.555 | 4.998 | 5.501 | 5.218 | 7.187 | -0.284 | 0.506 |
| v2a: + air yards | 4.549 | 4.995 | 5.500 | 5.216 | 7.180 | -0.271 | 0.507 |
| v2b: + air yards + environment | 4.543 | 4.992 | 5.497 | 5.212 | 7.179 | -0.297 | 0.508 |

## Each model against D EWMA h=4 (PPR, P, week-resampled 95% CI)

| Model | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| v1 (frozen λ) | 896 | 5.688 | 5.503 | -0.185 | [-0.301, -0.095] | +3.2% |
| v1′ | 896 | 5.688 | 5.501 | -0.187 | [-0.303, -0.097] | +3.3% |
| v2a: + air yards | 896 | 5.688 | 5.500 | -0.188 | [-0.300, -0.099] | +3.3% |
| v2b: + air yards + environment | 896 | 5.688 | 5.497 | -0.191 | [-0.312, -0.095] | +3.4% |

Candidate: **v1′**.

### By position and by time -- improvement over the baseline, PPR

| Slice | n | Baseline MAE | v1 frozen | v1′ | v2a | v2b | Candidate CI |
|---|---:|---:|---:|---:|---:|---:|---:|
| QB | 128 | 6.271 | -2.1% | -1.8% | -1.8% | -2.1% | [-0.418, 0.725] |
| RB | 256 | 5.529 | +3.2% | +3.2% | +3.1% | +3.3% | [-0.507, 0.154] |
| WR | 384 | 5.582 | +4.1% | +4.1% | +4.2% | +4.4% | [-0.431, -0.064] |
| TE | 128 | 5.742 | +6.6% | +6.6% | +6.7% | +6.5% | [-0.654, -0.125] |
| Week 1 | 224 | 6.020 | +2.2% | +2.1% | +2.1% | +2.1% | [-0.125, -0.125] |
| Week 2 | 224 | 5.616 | +6.4% | +6.5% | +6.3% | +6.6% | [-0.363, -0.363] |
| Week 3 | 224 | 5.408 | +1.2% | +1.3% | +1.3% | +1.1% | [-0.069, -0.069] |
| Week 4 | 224 | 5.709 | +3.2% | +3.3% | +3.5% | +3.6% | [-0.191, -0.191] |

### The candidate under every league

| League | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| 0 PPR | 896 | 4.741 | 4.555 | -0.186 | [-0.302, -0.105] | +3.9% |
| Half PPR | 896 | 5.195 | 4.998 | -0.197 | [-0.316, -0.112] | +3.8% |
| PPR | 896 | 5.688 | 5.501 | -0.187 | [-0.303, -0.097] | +3.3% |
| My league | 896 | 5.409 | 5.218 | -0.191 | [-0.313, -0.088] | +3.5% |

### The pre-registered ship rule, on the candidate -- binding

| # | Criterion | Measured | Result |
|---|---:|---:|---:|
| 1 | PPR MAE CI entirely below 0 | [-0.303, -0.097] | **pass** |
| 2 | Improvement ≥ +3.0% | +3.3% | **pass** |
| 3 | No position's CI entirely above 0 | QB -0.418, RB -0.507, WR -0.431, TE -0.654 | **pass** |
| 4 | Absolute bias ≤ 0.5 PPR pts | -0.284 | **pass** |

**All four pass.** Weekly clusters in this season: 4.

### How fragile is the candidate's result? (report-only, added after the 2026 run)

| Check | Value |
|---|---:|
| Weeks (clusters) | 4 |
| Mean weekly diff, t interval (df 3) | -0.187 [-0.390, 0.015] = +3.3% [-0.3%, +6.9%] |
| Leave one week out: improvement range | +2.2% (without week 2) to +3.9% (without week 3) |
| Weeks 1–4 only | +3.3% (n 896) |

## Receiving stats -- where air yards should act (RMSE, P)

| Pos | Stat | Baseline | v1′ | v2a | v2b | v2a better than v1′ by |
|---|---:|---:|---:|---:|---:|---:|
| RB | targets | 2.075 | 2.019 | 2.019 | 2.021 | -0.0% |
| RB | rec | 1.805 | 1.748 | 1.747 | 1.748 | +0.1% |
| RB | rec_yd | 16.114 | 15.741 | 15.653 | 15.658 | +0.6% |
| RB | rec_td | 0.296 | 0.275 | 0.274 | 0.275 | +0.0% |
| WR | targets | 3.038 | 2.936 | 2.937 | 2.939 | -0.0% |
| WR | rec | 2.239 | 2.187 | 2.194 | 2.194 | -0.3% |
| WR | rec_yd | 34.951 | 34.341 | 34.328 | 34.330 | +0.0% |
| WR | rec_td | 0.538 | 0.509 | 0.509 | 0.509 | +0.0% |
| TE | targets | 2.844 | 2.754 | 2.751 | 2.755 | +0.1% |
| TE | rec | 2.439 | 2.363 | 2.356 | 2.359 | +0.3% |
| TE | rec_yd | 30.491 | 29.633 | 29.592 | 29.616 | +0.1% |
| TE | rec_td | 0.532 | 0.518 | 0.517 | 0.513 | +0.0% |

## Availability -- diagnostic, never the acceptance population

| Method | n all / appeared / inactive | MAE all projected | MAE appeared | MAE inactive | Inactive share of error | Knowing who is active would remove |
|---|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 896 / 792 / 104 | 5.934 | 5.704 | 7.691 | 15.0% | 0.893 |
| v1′ | 896 / 792 / 104 | 5.699 | 5.511 | 7.131 | 14.5% | 0.828 |

All projected = P0: the top players by trailing opportunity who played their team's previous game, before kickoff. An inactive player scores 0, so all of his projection is error.

## Bias -- does the environment term remove the seasonal swing?

| Method | PPR bias | PPR median error | QB | RB | WR | TE | QB pass_yd | WR rec_yd |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 0.300 | 1.690 | 0.115 | 0.284 | 0.130 | 1.026 | -3.362 | -0.471 |
| v1 (frozen λ) | -0.283 | 1.383 | -0.657 | -0.122 | -0.376 | 0.050 | -8.314 | -2.778 |
| v1′ | -0.284 | 1.377 | -0.683 | -0.114 | -0.376 | 0.050 | -8.977 | -2.778 |
| v2a: + air yards | -0.271 | 1.395 | -0.683 | -0.119 | -0.349 | 0.071 | -8.977 | -2.719 |
| v2b: + air yards + environment | -0.297 | 1.338 | -0.713 | -0.141 | -0.401 | 0.122 | -8.311 | -2.575 |

Bias is the mean of projected minus actual; the median error is its median. Position columns are PPR bias; the last two are raw-stat bias in yards.

