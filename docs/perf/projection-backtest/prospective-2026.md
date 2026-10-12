# Projection backtest -- prospective (2026)

Choices frozen in `frozen-config.json` at commit `934206507dacebbb4e0b9fd8db93eea8c188f59b`; refit on 2021–2025 and evaluated on 2026.

## Data

Local database, regular season only, QB/RB/WR/TE rows. Fingerprint:

- 2020: 5,543 rows · pass_yd 130,405 · rush_yd 60,618 · rec 11,676 · rec_yd 129,810 · targets 17,190
- 2021: 5,866 rows · pass_yd 132,715 · rush_yd 62,374 · rec 12,012 · rec_yd 132,038 · targets 17,924
- 2022: 5,808 rows · pass_yd 126,978 · rush_yd 65,738 · rec 11,525 · rec_yd 126,277 · targets 17,193
- 2023: 5,801 rows · pass_yd 128,459 · rush_yd 61,238 · rec 11,722 · rec_yd 127,961 · targets 17,372
- 2024: 5,864 rows · pass_yd 126,963 · rush_yd 65,062 · rec 11,563 · rec_yd 126,476 · targets 16,920
- 2025: 6,037 rows · pass_yd 122,366 · rush_yd 63,550 · rec 11,130 · rec_yd 121,678 · targets 16,486
- 2026: 1,436 rows · pass_yd 30,027 · rush_yd 14,242 · rec 2,698 · rec_yd 29,903 · targets 3,987
- sha256 over 36,355 rows: ac3fe6aee827879bd7f628c49a68036df9fb312cef7bee49ba52df3d4c11e223
- per-season sums match a direct SQL SUM: yes (checked at load)
- code: `9342065 + uncommitted changes`

## Population

| Season | Weeks | P | P0 | P-all | Debuts excluded | Played for a new team | Rows with no line |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2021 | 18 | 3,808 | 3,808 (515 did not play) | 5,701 | 165 | 166 | 0 |
| 2022 | 18 | 3,794 | 3,794 (436 did not play) | 5,691 | 117 | 178 | 0 |
| 2023 | 18 | 3,808 | 3,808 (396 did not play) | 5,686 | 115 | 162 | 0 |
| 2024 | 18 | 3,808 | 3,808 (398 did not play) | 5,746 | 118 | 173 | 0 |
| 2025 | 18 | 3,807 | 3,808 (406 did not play) | 5,914 | 123 | 179 | 0 |
| 2026 | 4 | 882 | 882 (125 did not play) | 1,363 | 73 | 96 | 0 |

P is the primary population and every number below is over it unless labelled otherwise. Debuts have no history to project from.

## Fantasy points, every method (2026, P)

| Method | MAE 0 PPR | MAE Half | MAE PPR | MAE My league | RMSE PPR | Bias PPR | Spearman PPR |
|---|---:|---:|---:|---:|---:|---:|---:|
| A previous game | 5.698 | 6.333 | 7.036 | 6.632 | 9.341 | -0.078 | 0.326 |
| B last 5 games | 4.876 | 5.328 | 5.819 | 5.530 | 7.590 | 0.159 | 0.400 |
| C season to date | 5.181 | 5.674 | 6.231 | 5.925 | 8.233 | 0.171 | 0.414 |
| D EWMA h=4 | 4.700 | 5.144 | 5.629 | 5.355 | 7.247 | 0.189 | 0.433 |
| Model G1 | 4.589 | 5.030 | 5.527 | 5.245 | 7.212 | -0.472 | 0.467 |
| Model G2 | 4.578 | 5.017 | 5.517 | 5.233 | 7.189 | -0.413 | 0.466 |
| Model G3 | 4.531 | 4.969 | 5.465 | 5.183 | 7.111 | -0.369 | 0.483 |
| Model G4 | 4.530 | 4.970 | 5.467 | 5.182 | 7.119 | -0.432 | 0.482 |

Best baseline (chosen on 2024): **D EWMA h=4**. Model: **Model G3**. Spearman is the mean rank correlation within each position-week.

## Model against the best baseline

Paired difference in absolute PPR error (model − baseline; negative means the model is closer), 95% CI from 2000 bootstrap resamples of whole weeks.

| Slice | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| All | 882 | 5.629 | 5.465 | -0.165 | [-0.276, -0.057] | +2.9% |
| QB | 126 | 6.191 | 6.250 | 0.058 | [-0.361, 0.591] | -0.9% |
| RB | 252 | 5.530 | 5.335 | -0.195 | [-0.542, 0.178] | +3.5% |
| WR | 378 | 5.479 | 5.310 | -0.169 | [-0.342, -0.002] | +3.1% |
| TE | 126 | 5.717 | 5.401 | -0.316 | [-0.631, 0.027] | +5.5% |
| Weeks 1–3 | 672 | 5.629 | 5.489 | -0.140 | [-0.305, -0.048] | +2.5% |
| Weeks 4–9 | 210 | 5.633 | 5.387 | -0.246 | [-0.246, -0.246] | +4.4% |
| Weeks 10–18 | 0 | NaN | NaN | NaN | [NaN, NaN] | NaN% |

### Every league, same comparison

| League | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| 0 PPR | 882 | 4.700 | 4.531 | -0.169 | [-0.258, -0.093] | +3.6% |
| Half PPR | 882 | 5.144 | 4.969 | -0.175 | [-0.272, -0.082] | +3.4% |
| PPR | 882 | 5.629 | 5.465 | -0.165 | [-0.276, -0.057] | +2.9% |
| My league | 882 | 5.355 | 5.183 | -0.173 | [-0.290, -0.059] | +3.2% |

### The pre-registered ship rule (informative only here)

| # | Criterion | Measured | Result |
|---|---:|---:|---:|
| 1 | PPR MAE CI entirely below 0 | [-0.276, -0.057] | **pass** |
| 2 | Improvement ≥ +3.0% | +2.9% | **FAIL** |
| 3 | No position's CI entirely above 0 | QB -0.361, RB -0.542, WR -0.342, TE -0.631 | **pass** |
| 4 | Absolute bias ≤ 0.5 PPR pts | -0.369 | **pass** |

**Not all four pass.**

## Fantasy points by position (PPR, P)

| Position | Method | n | MAE | RMSE | Bias | Spearman |
|---|---:|---:|---:|---:|---:|---:|
| QB | A previous game | 126 | 8.041 | 10.353 | -0.242 | 0.186 |
| QB | B last 5 games | 126 | 6.197 | 8.058 | 0.021 | 0.238 |
| QB | C season to date | 126 | 7.156 | 9.300 | 0.334 | 0.223 |
| QB | D EWMA h=4 | 126 | 6.191 | 7.744 | -0.016 | 0.282 |
| QB | Model G1 | 126 | 6.401 | 7.866 | -0.719 | 0.232 |
| QB | Model G2 | 126 | 6.359 | 7.818 | -0.689 | 0.234 |
| QB | Model G3 | 126 | 6.250 | 7.667 | -0.751 | 0.304 |
| QB | Model G4 | 126 | 6.256 | 7.665 | -0.856 | 0.293 |
| RB | A previous game | 252 | 6.623 | 9.106 | -0.303 | 0.486 |
| RB | B last 5 games | 252 | 5.835 | 7.567 | 0.233 | 0.513 |
| RB | C season to date | 252 | 5.906 | 7.757 | 0.134 | 0.614 |
| RB | D EWMA h=4 | 252 | 5.530 | 7.142 | 0.209 | 0.573 |
| RB | Model G1 | 252 | 5.422 | 7.114 | -0.405 | 0.608 |
| RB | Model G2 | 252 | 5.420 | 7.103 | -0.335 | 0.608 |
| RB | Model G3 | 252 | 5.335 | 7.009 | -0.222 | 0.611 |
| RB | Model G4 | 252 | 5.359 | 7.047 | -0.244 | 0.611 |
| WR | A previous game | 378 | 6.849 | 9.103 | -0.126 | 0.319 |
| WR | B last 5 games | 378 | 5.611 | 7.522 | -0.114 | 0.438 |
| WR | C season to date | 378 | 6.064 | 8.208 | -0.045 | 0.402 |
| WR | D EWMA h=4 | 378 | 5.479 | 7.230 | 0.067 | 0.466 |
| WR | Model G1 | 378 | 5.341 | 7.168 | -0.472 | 0.512 |
| WR | Model G2 | 378 | 5.322 | 7.130 | -0.403 | 0.515 |
| WR | Model G3 | 378 | 5.310 | 7.083 | -0.378 | 0.524 |
| WR | Model G4 | 378 | 5.299 | 7.085 | -0.471 | 0.523 |
| TE | A previous game | 126 | 7.418 | 9.446 | 0.683 | 0.168 |
| TE | B last 5 games | 126 | 6.033 | 7.355 | 0.965 | 0.226 |
| TE | C season to date | 126 | 6.453 | 8.097 | 0.731 | 0.240 |
| TE | D EWMA h=4 | 126 | 5.717 | 6.991 | 0.719 | 0.208 |
| TE | Model G1 | 126 | 5.421 | 6.847 | -0.362 | 0.281 |
| TE | Model G2 | 126 | 5.458 | 6.873 | -0.324 | 0.267 |
| TE | Model G3 | 126 | 5.401 | 6.813 | -0.257 | 0.281 |
| TE | Model G4 | 126 | 5.395 | 6.789 | -0.270 | 0.288 |

## Raw stats by position (P): best baseline against the model

RMSE is the proper score for a mean projection. MAE rewards the median, which is 0 for touchdowns, so read MAE on TDs with that in mind.

| Pos | Stat | Actual mean | MAE base | MAE model | RMSE base | RMSE model | RMSE better by | Model bias |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| QB | pass_att | 31.119 | 7.451 | 7.537 | 9.981 | 10.097 | -1.2% | -1.506 |
| QB | pass_yd | 223.214 | 64.179 | 62.463 | 80.231 | 81.234 | -1.3% | -9.887 |
| QB | pass_td | 1.500 | 0.918 | 0.905 | 1.154 | 1.122 | +2.8% | -0.116 |
| QB | pass_int | 0.635 | 0.618 | 0.635 | 0.751 | 0.732 | +2.5% | 0.010 |
| QB | rush_att | 3.603 | 1.928 | 1.870 | 2.409 | 2.351 | +2.4% | 0.165 |
| QB | rush_yd | 15.421 | 11.514 | 11.263 | 14.332 | 14.004 | +2.3% | 1.106 |
| QB | rush_td | 0.183 | 0.285 | 0.271 | 0.436 | 0.427 | +2.1% | 0.004 |
| QB | fum_lost | 0.190 | 0.325 | 0.326 | 0.516 | 0.500 | +3.2% | 0.004 |
| RB | rush_att | 10.206 | 4.192 | 4.041 | 5.275 | 5.196 | +1.5% | -0.248 |
| RB | rush_yd | 43.016 | 23.614 | 22.914 | 30.498 | 30.198 | +1.0% | -0.335 |
| RB | rush_td | 0.341 | 0.429 | 0.433 | 0.620 | 0.608 | +1.9% | -0.023 |
| RB | targets | 2.516 | 1.560 | 1.511 | 2.085 | 2.032 | +2.5% | -0.145 |
| RB | rec | 1.980 | 1.353 | 1.285 | 1.813 | 1.755 | +3.2% | -0.104 |
| RB | rec_yd | 13.540 | 12.116 | 11.712 | 16.218 | 15.779 | +2.7% | 0.334 |
| RB | rec_td | 0.075 | 0.150 | 0.132 | 0.299 | 0.277 | +7.3% | -0.005 |
| RB | fum_lost | 0.071 | 0.118 | 0.118 | 0.262 | 0.256 | +2.3% | -0.015 |
| WR | targets | 5.201 | 2.362 | 2.269 | 2.993 | 2.909 | +2.8% | -0.087 |
| WR | rec | 3.341 | 1.724 | 1.672 | 2.233 | 2.187 | +2.0% | -0.143 |
| WR | rec_yd | 43.627 | 26.178 | 25.359 | 34.482 | 33.945 | +1.6% | -2.693 |
| WR | rec_td | 0.254 | 0.381 | 0.378 | 0.534 | 0.505 | +5.4% | 0.000 |
| WR | rush_att | 0.132 | 0.227 | 0.228 | 0.416 | 0.387 | +6.9% | 0.035 |
| WR | rush_yd | 0.746 | 1.511 | 1.518 | 3.844 | 3.684 | +4.2% | 0.176 |
| WR | fum_lost | 0.021 | 0.042 | 0.050 | 0.155 | 0.145 | +6.7% | 0.009 |
| TE | targets | 5.056 | 2.277 | 2.175 | 2.900 | 2.813 | +3.0% | -0.234 |
| TE | rec | 3.603 | 1.882 | 1.803 | 2.443 | 2.376 | +2.7% | -0.114 |
| TE | rec_yd | 36.873 | 22.887 | 22.412 | 30.179 | 29.456 | +2.4% | -0.289 |
| TE | rec_td | 0.294 | 0.431 | 0.414 | 0.543 | 0.534 | +1.7% | -0.031 |
| TE | fum_lost | 0.024 | 0.041 | 0.044 | 0.156 | 0.153 | +2.0% | -0.002 |

## Availability and the board's population (PPR)

| Population | n | Baseline MAE | Model MAE | Baseline bias | Model bias |
|---|---:|---:|---:|---:|---:|
| P (played) | 882 | 5.629 | 5.465 | 0.189 | -0.369 |
| P0 (played team's last game; DNP = 0) | 882 | 5.911 | 5.709 | 1.095 | 0.706 |
| P-all (every player with history who played) | 1,363 | 4.561 | 4.476 | 0.015 | -0.296 |

P0 − P is roughly what not knowing who is active costs; an injury report is the input that could recover some of it.

## Threshold bonuses: expected value against the step

Bonus league = Half PPR + 3 pts each for 100 rushing yards, 100 receiving yards and 300 passing yards. Same projected lines, three ways to score them.

| Treatment | MAE | RMSE | Bias |
|---|---:|---:|---:|
| Expected bonus (the brief's decision) | 5.246 | 6.953 | -0.320 |
| Bonus applied to the mean line | 5.227 | 6.994 | -0.574 |
| Bonus ignored | 5.224 | 6.992 | -0.578 |

### Are the probabilities calibrated?

| Bonus | Pos | n | Hit rate | Mean P | Brier (expected) | Brier (step on mean) |
|---|---:|---:|---:|---:|---:|---:|
| rush_yd ≥ 100 | RB | 252 | 7.1% | 8.9% | 0.0612 | 0.0754 |
| rec_yd ≥ 100 | WR | 378 | 8.7% | 7.5% | 0.0688 | 0.0873 |
| rec_yd ≥ 100 | TE | 126 | 4.8% | 3.3% | 0.0480 | 0.0476 |
| pass_yd ≥ 300 | QB | 126 | 15.9% | 15.3% | 0.1281 | 0.1587 |

Reliability, every bonus and position pooled:

| P bin | n | Mean P | Observed |
|---|---:|---:|---:|
| 0.0–0.1 | 2,336 | 0.8% | 0.9% |
| 0.1–0.2 | 220 | 14.4% | 11.8% |
| 0.2–0.3 | 83 | 27.5% | 30.1% |
| 0.3–0.4 | 7 | 33.5% | 57.1% |

## Rare-stat shrinkage (pseudo-games, fitted on training rows)

QB:rec 1024 · QB:rec_yd 1024 · QB:rec_td 1024 · QB:targets 256 · RB:pass_yd 1024 · RB:pass_td 16 · RB:pass_int 1024 · RB:pass_att 16 · WR:pass_yd 64 · WR:pass_td 1024 · WR:pass_int 1024 · WR:rush_td 16 · WR:pass_att 64 · TE:pass_yd 16 · TE:pass_td 16 · TE:pass_int 16 · TE:rush_yd 1 · TE:rush_td 4 · TE:pass_att 16 · TE:rush_att 1
