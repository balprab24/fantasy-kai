# Experiment v2 -- validation (2024)

λ per variant and the candidate are chosen here; optimistic by construction. 2025 and 2026 are not read by this stage.

## Data

Local database, regular season only, QB/RB/WR/TE rows. Fingerprint (totals for seasons after 2024 withheld):

- 2020: 5,543 rows · pass_yd 130,405 · rush_yd 60,618 · rec 11,676 · rec_yd 129,810 · targets 17,190
- 2021: 5,866 rows · pass_yd 132,715 · rush_yd 62,374 · rec 12,012 · rec_yd 132,038 · targets 17,924
- 2022: 5,808 rows · pass_yd 126,978 · rush_yd 65,738 · rec 11,525 · rec_yd 126,277 · targets 17,193
- 2023: 5,801 rows · pass_yd 128,459 · rush_yd 61,238 · rec 11,722 · rec_yd 127,961 · targets 17,372
- 2024: 5,864 rows · pass_yd 126,963 · rush_yd 65,062 · rec 11,563 · rec_yd 126,476 · targets 16,920
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
- 2020: v2 joins -- 5,543 stored rows matched nflverse, 0 not (air yards NaN); 1 took the week's position; 730 zero-stat appearances added; snap rows not placed: 40 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2021: v2 joins -- 5,866 stored rows matched nflverse, 0 not (air yards NaN); 0 took the week's position; 796 zero-stat appearances added; snap rows not placed: 46 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2022: v2 joins -- 5,808 stored rows matched nflverse, 0 not (air yards NaN); 11 took the week's position; 806 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2023: v2 joins -- 5,801 stored rows matched nflverse, 0 not (air yards NaN); 13 took the week's position; 851 zero-stat appearances added; snap rows not placed: 29 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- 2024: v2 joins -- 5,864 stored rows matched nflverse, 0 not (air yards NaN); 2 took the week's position; 818 zero-stat appearances added; snap rows not placed: 31 unknown pfr id, 0 team not a side, 0 unfinished game; 0 team-games without passing air yards
- games counted as played by their stat rows (score not yet stored): 2026_04_ATL_NO
- nflverse rows with a target or attempt but no air yards: 0
- code: `24d16d1` + uncommitted: backend/src/main/java/com/fantasykai/scoring/RulesetJson.java, backend/src/test/java/com/fantasykai/projection/Evaluation.java, backend/src/test/java/com/fantasykai/projection/ExperimentV2.java

## The candidate (v1's CI-gated rule)

| Challenger | Against | Diff PPR abs. error | 95% CI | Adopted |
|---|---:|---:|---:|---:|
| v2a: + air yards | v1′ | 0.003 | [-0.001, 0.007] | no |
| v2b: + air yards + environment | v1′ | 0.029 | [0.014, 0.046] | no |

**Candidate: v1′**

## λ chosen per ridge model (2024 RMSE of its own stat)

| Model | v1 frozen | v1′ | v2a | v2b |
|---|---:|---:|---:|---:|
| QB:pass_att | 0.01 | 0.001 | 0.001 | 1 |
| QB:pass_yd | 0.1 | 0.01 | 0.01 | 0.1 |
| QB:pass_td | 0.1 | 0.1 | 0.1 | 0.1 |
| QB:pass_int | 1 | 1 | 1 | 1 |
| QB:rush_att | 0.01 | 0.01 | 0.01 | 0.01 |
| QB:rush_yd | 1 | 1 | 1 | 1 |
| QB:rush_td | 0.01 | 0.01 | 0.01 | 0.01 |
| RB:rush_att | 0.01 | 0.01 | 0.01 | 0.1 |
| RB:rush_yd | 0.1 | 0.1 | 0.1 | 0.1 |
| RB:rush_td | 0.001 | 0.001 | 0.001 | 0.001 |
| RB:targets | 0.1 | 0.1 | 0.1 | 0.1 |
| RB:rec | 0.01 | 0.01 | 0.01 | 0.01 |
| RB:rec_yd | 0.01 | 0.001 | 0.01 | 0.01 |
| RB:rec_td | 1 | 1 | 1 | 1 |
| WR:targets | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rec | 0.001 | 0.001 | 0.001 | 0.1 |
| WR:rec_yd | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rec_td | 0.1 | 0.1 | 0.1 | 0.01 |
| WR:rush_att | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rush_yd | 1 | 1 | 1 | 1 |
| TE:targets | 0.01 | 0.01 | 0.01 | 0.01 |
| TE:rec | 0.01 | 0.01 | 0.01 | 0.01 |
| TE:rec_yd | 0.01 | 0.01 | 0.01 | 0.01 |
| TE:rec_td | 0.1 | 0.1 | 0.1 | 1 |

## Population (v2 dataset)

| Season | Weeks | P (official) | of which zero-stat appearances | P0 | P-all | Debuts excluded |
|---|---:|---:|---:|---:|---:|---:|
| 2021 | 18 | 3,808 | 102 | 3,808 (436 inactive) | 6,490 | 172 |
| 2022 | 18 | 3,794 | 109 | 3,794 (358 inactive) | 6,490 | 124 |
| 2023 | 18 | 3,808 | 105 | 3,808 (320 inactive) | 6,529 | 123 |
| 2024 | 18 | 3,808 | 94 | 3,808 (307 inactive) | 6,557 | 125 |

P counts a player who appeared -- a stat row or offensive snaps. Inactive means neither.

## Every method (2024, official population P)

| Method | MAE 0 PPR | MAE Half | MAE PPR | MAE My league | RMSE PPR | Bias PPR | Spearman PPR |
|---|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 4.576 | 5.007 | 5.493 | 5.228 | 7.129 | 0.058 | 0.507 |
| v1 (frozen λ) | 4.450 | 4.875 | 5.355 | 5.096 | 6.963 | -0.300 | 0.532 |
| v1′ | 4.448 | 4.873 | 5.353 | 5.094 | 6.961 | -0.302 | 0.532 |
| v2a: + air yards | 4.449 | 4.877 | 5.356 | 5.097 | 6.964 | -0.287 | 0.532 |
| v2b: + air yards + environment | 4.471 | 4.901 | 5.381 | 5.122 | 6.959 | -0.100 | 0.533 |

## Each model against D EWMA h=4 (PPR, P, week-resampled 95% CI)

| Model | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| v1 (frozen λ) | 3,808 | 5.493 | 5.355 | -0.138 | [-0.224, -0.060] | +2.5% |
| v1′ | 3,808 | 5.493 | 5.353 | -0.140 | [-0.226, -0.061] | +2.5% |
| v2a: + air yards | 3,808 | 5.493 | 5.356 | -0.136 | [-0.221, -0.059] | +2.5% |
| v2b: + air yards + environment | 3,808 | 5.493 | 5.381 | -0.111 | [-0.200, -0.034] | +2.0% |

Candidate: **v1′**.

### By position and by time -- improvement over the baseline, PPR

| Slice | n | Baseline MAE | v1 frozen | v1′ | v2a | v2b | Candidate CI |
|---|---:|---:|---:|---:|---:|---:|---:|
| QB | 544 | 6.508 | +3.7% | +3.9% | +3.9% | +3.5% | [-0.410, -0.095] |
| RB | 1,088 | 5.127 | +1.3% | +1.3% | +1.4% | +1.5% | [-0.181, 0.044] |
| WR | 1,632 | 5.599 | +2.7% | +2.7% | +2.6% | +1.8% | [-0.250, -0.049] |
| TE | 544 | 4.890 | +2.9% | +2.9% | +2.6% | +2.1% | [-0.341, 0.029] |
| Weeks 1–3 | 672 | 5.462 | +6.4% | +6.5% | +6.3% | +5.2% | [-0.487, -0.105] |
| Weeks 4–9 | 1,260 | 5.518 | +2.5% | +2.6% | +2.5% | +2.1% | [-0.277, -0.023] |
| Weeks 10–18 | 1,876 | 5.486 | +1.1% | +1.1% | +1.1% | +0.9% | [-0.138, -0.004] |

### The candidate under every league

| League | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| 0 PPR | 3,808 | 4.576 | 4.448 | -0.128 | [-0.197, -0.061] | +2.8% |
| Half PPR | 3,808 | 5.007 | 4.873 | -0.134 | [-0.213, -0.060] | +2.7% |
| PPR | 3,808 | 5.493 | 5.353 | -0.140 | [-0.226, -0.061] | +2.5% |
| My league | 3,808 | 5.228 | 5.094 | -0.134 | [-0.218, -0.053] | +2.6% |

### The pre-registered ship rule, on the candidate (informative only here)

| # | Criterion | Measured | Result |
|---|---:|---:|---:|
| 1 | PPR MAE CI entirely below 0 | [-0.226, -0.061] | **pass** |
| 2 | Improvement ≥ +3.0% | +2.5% | **FAIL** |
| 3 | No position's CI entirely above 0 | QB -0.410, RB -0.181, WR -0.250, TE -0.341 | **pass** |
| 4 | Absolute bias ≤ 0.5 PPR pts | -0.302 | **pass** |

**Not all four pass.** Weekly clusters in this season: 18.

### How fragile is the candidate's result? (report-only, added after the 2026 run)

| Check | Value |
|---|---:|
| Weeks (clusters) | 18 |
| Mean weekly diff, t interval (df 17) | -0.141 [-0.229, -0.053] = +2.6% [+1.0%, +4.2%] |
| Leave one week out: improvement range | +2.1% (without week 1) to +2.8% (without week 7) |
| Weeks 1–4 only | +5.1% (n 896) |

## Receiving stats -- where air yards should act (RMSE, P)

| Pos | Stat | Baseline | v1′ | v2a | v2b | v2a better than v1′ by |
|---|---:|---:|---:|---:|---:|---:|
| RB | targets | 1.829 | 1.791 | 1.793 | 1.798 | -0.1% |
| RB | rec | 1.568 | 1.527 | 1.527 | 1.526 | +0.0% |
| RB | rec_yd | 16.085 | 15.674 | 15.670 | 15.688 | +0.0% |
| RB | rec_td | 0.261 | 0.250 | 0.250 | 0.250 | -0.0% |
| WR | targets | 3.006 | 2.933 | 2.934 | 2.931 | -0.0% |
| WR | rec | 2.209 | 2.171 | 2.170 | 2.167 | +0.0% |
| WR | rec_yd | 33.702 | 32.827 | 32.843 | 32.813 | -0.0% |
| WR | rec_td | 0.555 | 0.534 | 0.535 | 0.534 | -0.1% |
| TE | targets | 2.806 | 2.760 | 2.760 | 2.760 | -0.0% |
| TE | rec | 2.284 | 2.243 | 2.240 | 2.236 | +0.1% |
| TE | rec_yd | 27.421 | 26.639 | 26.646 | 26.632 | -0.0% |
| TE | rec_td | 0.478 | 0.468 | 0.468 | 0.468 | +0.0% |

## Availability -- diagnostic, never the acceptance population

| Method | n all / appeared / inactive | MAE all projected | MAE appeared | MAE inactive | Inactive share of error | Knowing who is active would remove |
|---|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 3,808 / 3,501 / 307 | 5.815 | 5.508 | 9.320 | 12.9% | 0.751 |
| v1′ | 3,808 / 3,501 / 307 | 5.657 | 5.388 | 8.727 | 12.4% | 0.704 |

All projected = P0: the top players by trailing opportunity who played their team's previous game, before kickoff. An inactive player scores 0, so all of his projection is error.

## Bias -- does the environment term remove the seasonal swing?

| Method | PPR bias | PPR median error | QB | RB | WR | TE | QB pass_yd | WR rec_yd |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| D EWMA h=4 | 0.058 | 1.120 | 0.004 | 0.228 | 0.040 | -0.173 | 2.817 | 0.408 |
| v1 (frozen λ) | -0.300 | 1.006 | -0.364 | 0.034 | -0.488 | -0.343 | 1.331 | -1.231 |
| v1′ | -0.302 | 0.988 | -0.368 | 0.029 | -0.488 | -0.343 | 1.211 | -1.231 |
| v2a: + air yards | -0.287 | 1.000 | -0.368 | 0.042 | -0.470 | -0.318 | 1.211 | -1.179 |
| v2b: + air yards + environment | -0.100 | 1.166 | -0.233 | 0.006 | -0.118 | -0.122 | 7.233 | 0.533 |

Bias is the mean of projected minus actual; the median error is its median. Position columns are PPR bias; the last two are raw-stat bias in yards.

