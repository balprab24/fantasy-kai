# Projection backtest -- validation (2024)

Every choice below was made on this season. Its numbers are optimistic by construction; the test season is the one that counts.

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
- code: `0943f20 + uncommitted changes`

## Baseline grid (2024, P, PPR)

| Baseline | MAE | RMSE | Bias |
|---|---:|---:|---:|
| A previous game | 6.915 | 9.130 | 0.295 |
| B last 3 games | 5.857 | 7.620 | 0.330 |
| B last 4 games | 5.767 | 7.478 | 0.322 |
| B last 5 games | 5.697 | 7.368 | 0.251 |
| C season to date | 5.560 | 7.345 | -0.251 |
| D EWMA h=1 | 5.819 | 7.589 | 0.289 |
| D EWMA h=2 | 5.570 | 7.227 | 0.198 |
| D EWMA h=3 | 5.500 | 7.130 | 0.120 |
| D EWMA h=4 | 5.480 | 7.099 | 0.065 |
| D EWMA h=6 | 5.482 | 7.093 | 0.004 |
| D EWMA h=8 | 5.499 | 7.108 | -0.023 |

Chosen: rolling k = 5, EWMA half-life = 4.0 games, best baseline = **D EWMA h=4**.

## Feature group × pooling (2024, P, PPR)

| Group | Receiving | Rushing | MAE | RMSE | Bias |
|---|---:|---:|---:|---:|---:|
| G1 | per position | per position | 5.371 | 6.985 | -0.336 |
| G1 | per position | pooled | 5.374 | 6.981 | -0.317 |
| G1 | pooled | per position | 5.377 | 6.990 | -0.331 |
| G1 | pooled | pooled | 5.378 | 6.984 | -0.311 |
| G2 | per position | per position | 5.377 | 6.988 | -0.327 |
| G2 | per position | pooled | 5.375 | 6.982 | -0.316 |
| G2 | pooled | per position | 5.383 | 6.992 | -0.311 |
| G2 | pooled | pooled | 5.380 | 6.984 | -0.300 |
| G3 | per position | per position | 5.343 | 6.935 | -0.253 |
| G3 | per position | pooled | 5.339 | 6.926 | -0.231 |
| G3 | pooled | per position | 5.350 | 6.935 | -0.233 |
| G3 | pooled | pooled | 5.344 | 6.925 | -0.211 |
| G4 | per position | per position | 5.341 | 6.945 | -0.299 |
| G4 | per position | pooled | 5.336 | 6.934 | -0.274 |
| G4 | pooled | per position | 5.348 | 6.943 | -0.269 |
| G4 | pooled | pooled | 5.340 | 6.929 | -0.244 |

How the choice was made -- each challenger against the choice so far, paired PPR absolute error, 95% CI from 2000 week resamples:

| Challenger | Against | Diff | 95% CI | Adopted |
|---|---:|---:|---:|---:|
| G2 | G1 | 0.006 | [-0.005, 0.016] | no |
| G3 | G1 | -0.028 | [-0.058, -0.000] | yes |
| G4 | G3 | -0.002 | [-0.011, 0.008] | no |
| G3 rec-pooled | G3 | 0.006 | [-0.008, 0.022] | no |
| G3 rush-pooled | G3 | -0.004 | [-0.016, 0.006] | no |

Chosen: **G3**, receiving per position, rushing per position.

## λ chosen per ridge model (validation RMSE of its own stat)

| Model | G1 | G2 | G3 | G4 |
|---|---:|---:|---:|---:|
| QB:pass_att | 0.001 | 0.1 | 0.01 | 0.01 |
| QB:pass_yd | 0.1 | 0.1 | 0.1 | 0.1 |
| QB:pass_td | 0.001 | 0.1 | 0.1 | 0.1 |
| QB:pass_int | 10 | 1 | 1 | 1 |
| QB:rush_att | 0.001 | 0.001 | 0.01 | 0.1 |
| QB:rush_yd | 0.1 | 1 | 1 | 1 |
| QB:rush_td | 0.001 | 0.01 | 0.01 | 0.1 |
| RB:rush_att | 0.001 | 0.01 | 0.01 | 0.01 |
| RB:rush_yd | 0.001 | 0.01 | 0.1 | 0.1 |
| RB:rush_td | 0.01 | 0.1 | 0.001 | 0.001 |
| RB:targets | 0.001 | 0.1 | 0.1 | 0.1 |
| RB:rec | 0.01 | 0.1 | 0.01 | 0.01 |
| RB:rec_yd | 0.1 | 0.1 | 0.01 | 0.01 |
| RB:rec_td | 0.1 | 1 | 1 | 1 |
| WR:targets | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rec | 0.1 | 0.1 | 0.001 | 0.001 |
| WR:rec_yd | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rec_td | 1 | 1 | 0.1 | 0.1 |
| WR:rush_att | 0.1 | 0.1 | 0.1 | 0.1 |
| WR:rush_yd | 1 | 1 | 1 | 1 |
| TE:targets | 0.001 | 0.01 | 0.01 | 0.01 |
| TE:rec | 0.001 | 0.01 | 0.01 | 0.01 |
| TE:rec_yd | 0.001 | 0.01 | 0.01 | 0.01 |
| TE:rec_td | 1 | 0.001 | 0.1 | 0.01 |
| RUSH*:rush_att | 0.001 | 0.01 | 0.01 | 0.01 |
| RUSH*:rush_yd | 0.001 | 0.01 | 0.01 | 0.01 |
| RUSH*:rush_td | 0.01 | 0.1 | 0.001 | 0.001 |
| REC*:targets | 0.01 | 0.1 | 0.1 | 0.1 |
| REC*:rec | 0.01 | 0.01 | 0.01 | 0.01 |
| REC*:rec_yd | 0.1 | 0.1 | 0.1 | 0.1 |
| REC*:rec_td | 0.1 | 0.001 | 0.1 | 0.1 |

## Population

| Season | Weeks | P | P0 | P-all | Debuts excluded | Played for a new team | Rows with no line |
|---|---:|---:|---:|---:|---:|---:|---:|
| 2021 | 18 | 3,808 | 3,808 (515 did not play) | 5,701 | 165 | 166 | 0 |
| 2022 | 18 | 3,794 | 3,794 (436 did not play) | 5,691 | 117 | 178 | 0 |
| 2023 | 18 | 3,808 | 3,808 (396 did not play) | 5,686 | 115 | 162 | 0 |
| 2024 | 18 | 3,808 | 3,808 (398 did not play) | 5,746 | 118 | 173 | 0 |

P is the primary population and every number below is over it unless labelled otherwise. Debuts have no history to project from.

## Fantasy points, every method (2024, P)

| Method | MAE 0 PPR | MAE Half | MAE PPR | MAE My league | RMSE PPR | Bias PPR | Spearman PPR |
|---|---:|---:|---:|---:|---:|---:|---:|
| A previous game | 5.794 | 6.321 | 6.915 | 6.601 | 9.130 | 0.295 | 0.365 |
| B last 5 games | 4.762 | 5.202 | 5.697 | 5.439 | 7.368 | 0.251 | 0.466 |
| C season to date | 4.649 | 5.072 | 5.560 | 5.295 | 7.345 | -0.251 | 0.473 |
| D EWMA h=4 | 4.579 | 5.003 | 5.480 | 5.224 | 7.099 | 0.065 | 0.499 |
| Model G1 | 4.481 | 4.900 | 5.371 | 5.124 | 6.985 | -0.336 | 0.513 |
| Model G2 | 4.487 | 4.907 | 5.377 | 5.131 | 6.988 | -0.327 | 0.517 |
| Model G3 | 4.455 | 4.872 | 5.343 | 5.092 | 6.935 | -0.253 | 0.525 |
| Model G4 | 4.456 | 4.871 | 5.341 | 5.092 | 6.945 | -0.299 | 0.524 |

Best baseline (chosen on 2024): **D EWMA h=4**. Model: **Model G3**. Spearman is the mean rank correlation within each position-week.

## Model against the best baseline

Paired difference in absolute PPR error (model − baseline; negative means the model is closer), 95% CI from 2000 bootstrap resamples of whole weeks.

| Slice | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| All | 3,808 | 5.480 | 5.343 | -0.137 | [-0.218, -0.060] | +2.5% |
| QB | 544 | 6.507 | 6.280 | -0.227 | [-0.386, -0.076] | +3.5% |
| RB | 1,088 | 5.149 | 5.062 | -0.087 | [-0.198, 0.022] | +1.7% |
| WR | 1,632 | 5.578 | 5.451 | -0.126 | [-0.228, -0.027] | +2.3% |
| TE | 544 | 4.819 | 4.643 | -0.177 | [-0.383, -0.007] | +3.7% |
| Weeks 1–3 | 672 | 5.471 | 5.141 | -0.330 | [-0.473, -0.094] | +6.0% |
| Weeks 4–9 | 1,260 | 5.496 | 5.352 | -0.145 | [-0.258, -0.036] | +2.6% |
| Weeks 10–18 | 1,876 | 5.471 | 5.410 | -0.062 | [-0.152, 0.003] | +1.1% |

### Every league, same comparison

| League | n | Baseline MAE | Model MAE | Diff | 95% CI | Improvement |
|---|---:|---:|---:|---:|---:|---:|
| 0 PPR | 3,808 | 4.579 | 4.455 | -0.124 | [-0.193, -0.060] | +2.7% |
| Half PPR | 3,808 | 5.003 | 4.872 | -0.132 | [-0.209, -0.058] | +2.6% |
| PPR | 3,808 | 5.480 | 5.343 | -0.137 | [-0.218, -0.060] | +2.5% |
| My league | 3,808 | 5.224 | 5.092 | -0.132 | [-0.214, -0.054] | +2.5% |

### The pre-registered ship rule (informative only here)

| # | Criterion | Measured | Result |
|---|---:|---:|---:|
| 1 | PPR MAE CI entirely below 0 | [-0.218, -0.060] | **pass** |
| 2 | Improvement ≥ +3.0% | +2.5% | **FAIL** |
| 3 | No position's CI entirely above 0 | QB -0.386, RB -0.198, WR -0.228, TE -0.383 | **pass** |
| 4 | Absolute bias ≤ 0.5 PPR pts | -0.253 | **pass** |

**Not all four pass.**

## Fantasy points by position (PPR, P)

| Position | Method | n | MAE | RMSE | Bias | Spearman |
|---|---:|---:|---:|---:|---:|---:|
| QB | A previous game | 544 | 8.113 | 10.175 | 0.228 | 0.265 |
| QB | B last 5 games | 544 | 6.709 | 8.440 | 0.138 | 0.336 |
| QB | C season to date | 544 | 6.606 | 8.409 | -0.651 | 0.342 |
| QB | D EWMA h=4 | 544 | 6.507 | 8.100 | -0.062 | 0.374 |
| QB | Model G1 | 544 | 6.321 | 7.906 | -0.312 | 0.384 |
| QB | Model G2 | 544 | 6.352 | 7.933 | -0.362 | 0.386 |
| QB | Model G3 | 544 | 6.280 | 7.850 | -0.304 | 0.411 |
| QB | Model G4 | 544 | 6.295 | 7.870 | -0.345 | 0.407 |
| RB | A previous game | 1,088 | 6.220 | 8.266 | 0.283 | 0.509 |
| RB | B last 5 games | 1,088 | 5.353 | 6.880 | 0.372 | 0.598 |
| RB | C season to date | 1,088 | 5.246 | 6.783 | 0.232 | 0.616 |
| RB | D EWMA h=4 | 1,088 | 5.149 | 6.622 | 0.215 | 0.620 |
| RB | Model G1 | 1,088 | 5.084 | 6.587 | -0.156 | 0.627 |
| RB | Model G2 | 1,088 | 5.116 | 6.603 | -0.080 | 0.628 |
| RB | Model G3 | 1,088 | 5.062 | 6.498 | 0.000 | 0.639 |
| RB | Model G4 | 1,088 | 5.046 | 6.511 | -0.060 | 0.640 |
| WR | A previous game | 1,632 | 7.157 | 9.600 | 0.325 | 0.360 |
| WR | B last 5 games | 1,632 | 5.824 | 7.598 | 0.259 | 0.453 |
| WR | C season to date | 1,632 | 5.613 | 7.569 | -0.232 | 0.478 |
| WR | D EWMA h=4 | 1,632 | 5.578 | 7.325 | 0.085 | 0.495 |
| WR | Model G1 | 1,632 | 5.476 | 7.199 | -0.460 | 0.513 |
| WR | Model G2 | 1,632 | 5.471 | 7.193 | -0.444 | 0.516 |
| WR | Model G3 | 1,632 | 5.451 | 7.170 | -0.384 | 0.516 |
| WR | Model G4 | 1,632 | 5.453 | 7.179 | -0.441 | 0.515 |
| TE | A previous game | 544 | 6.383 | 8.171 | 0.293 | 0.196 |
| TE | B last 5 games | 544 | 4.993 | 6.396 | 0.094 | 0.368 |
| TE | C season to date | 544 | 4.985 | 6.558 | -0.875 | 0.306 |
| TE | D EWMA h=4 | 544 | 4.819 | 6.203 | -0.163 | 0.397 |
| TE | Model G1 | 544 | 4.679 | 6.060 | -0.351 | 0.417 |
| TE | Model G2 | 544 | 4.641 | 6.041 | -0.438 | 0.428 |
| TE | Model G3 | 544 | 4.643 | 6.026 | -0.319 | 0.434 |
| TE | Model G4 | 544 | 4.644 | 6.027 | -0.308 | 0.437 |

## Raw stats by position (P): best baseline against the model

RMSE is the proper score for a mean projection. MAE rewards the median, which is 0 for touchdowns, so read MAE on TDs with that in mind.

| Pos | Stat | Actual mean | MAE base | MAE model | RMSE base | RMSE model | RMSE better by | Model bias |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| QB | pass_att | 29.570 | 8.239 | 7.764 | 10.594 | 10.100 | +4.7% | 0.789 |
| QB | pass_yd | 214.142 | 68.580 | 65.220 | 86.488 | 82.601 | +4.5% | 2.269 |
| QB | pass_td | 1.388 | 0.925 | 0.906 | 1.156 | 1.119 | +3.2% | -0.061 |
| QB | pass_int | 0.643 | 0.717 | 0.714 | 0.887 | 0.845 | +4.7% | 0.055 |
| QB | rush_att | 3.796 | 1.804 | 1.776 | 2.326 | 2.275 | +2.2% | 0.234 |
| QB | rush_yd | 18.292 | 13.031 | 12.739 | 17.813 | 17.482 | +1.9% | -0.401 |
| QB | rush_td | 0.178 | 0.257 | 0.264 | 0.443 | 0.423 | +4.6% | 0.006 |
| QB | fum_lost | 0.180 | 0.295 | 0.316 | 0.417 | 0.405 | +2.8% | 0.026 |
| RB | rush_att | 9.940 | 4.041 | 3.921 | 5.178 | 5.018 | +3.1% | 0.125 |
| RB | rush_yd | 43.482 | 23.969 | 23.299 | 30.997 | 30.472 | +1.7% | -0.659 |
| RB | rush_td | 0.328 | 0.402 | 0.413 | 0.565 | 0.550 | +2.7% | -0.007 |
| RB | targets | 2.357 | 1.442 | 1.424 | 1.842 | 1.800 | +2.3% | 0.130 |
| RB | rec | 1.878 | 1.228 | 1.209 | 1.580 | 1.538 | +2.6% | 0.070 |
| RB | rec_yd | 14.143 | 11.799 | 11.489 | 16.135 | 15.710 | +2.6% | -0.148 |
| RB | rec_td | 0.064 | 0.125 | 0.125 | 0.261 | 0.250 | +4.2% | 0.009 |
| RB | fum_lost | 0.055 | 0.103 | 0.105 | 0.242 | 0.230 | +4.9% | 0.003 |
| WR | targets | 5.493 | 2.307 | 2.239 | 2.956 | 2.891 | +2.2% | -0.062 |
| WR | rec | 3.504 | 1.680 | 1.648 | 2.184 | 2.151 | +1.5% | -0.095 |
| WR | rec_yd | 44.426 | 25.841 | 24.967 | 33.702 | 32.858 | +2.5% | -0.772 |
| WR | rec_td | 0.299 | 0.412 | 0.404 | 0.558 | 0.537 | +3.8% | -0.041 |
| WR | rush_att | 0.216 | 0.263 | 0.277 | 0.517 | 0.521 | -0.9% | -0.010 |
| WR | rush_yd | 1.007 | 1.759 | 1.857 | 3.937 | 3.797 | +3.6% | 0.210 |
| WR | fum_lost | 0.025 | 0.045 | 0.057 | 0.162 | 0.156 | +3.7% | 0.010 |
| TE | targets | 4.919 | 2.187 | 2.139 | 2.781 | 2.732 | +1.8% | -0.136 |
| TE | rec | 3.597 | 1.777 | 1.716 | 2.279 | 2.228 | +2.2% | -0.184 |
| TE | rec_yd | 37.233 | 21.512 | 20.458 | 27.324 | 26.436 | +3.3% | -1.492 |
| TE | rec_td | 0.230 | 0.329 | 0.352 | 0.472 | 0.460 | +2.5% | 0.004 |
| TE | fum_lost | 0.028 | 0.050 | 0.047 | 0.171 | 0.164 | +4.0% | -0.007 |

## Availability and the board's population (PPR)

| Population | n | Baseline MAE | Model MAE | Baseline bias | Model bias |
|---|---:|---:|---:|---:|---:|
| P (played) | 3,808 | 5.480 | 5.343 | 0.065 | -0.253 |
| P0 (played team's last game; DNP = 0) | 3,808 | 5.845 | 5.697 | 0.851 | 0.541 |
| P-all (every player with history who played) | 5,746 | 4.509 | 4.450 | 0.006 | -0.056 |

P0 − P is roughly what not knowing who is active costs; an injury report is the input that could recover some of it.

## Threshold bonuses: expected value against the step

Bonus league = Half PPR + 3 pts each for 100 rushing yards, 100 receiving yards and 300 passing yards. Same projected lines, three ways to score them.

| Treatment | MAE | RMSE | Bias |
|---|---:|---:|---:|
| Expected bonus (the brief's decision) | 5.151 | 6.834 | -0.225 |
| Bonus applied to the mean line | 5.131 | 6.859 | -0.515 |
| Bonus ignored | 5.131 | 6.860 | -0.517 |

### Are the probabilities calibrated?

| Bonus | Pos | n | Hit rate | Mean P | Brier (expected) | Brier (step on mean) |
|---|---:|---:|---:|---:|---:|---:|
| rush_yd ≥ 100 | QB | 544 | 0.4% | 1.1% | 0.0038 | 0.0037 |
| rush_yd ≥ 100 | RB | 1,088 | 10.1% | 8.9% | 0.0793 | 0.0993 |
| rush_yd ≥ 100 | TE | 544 | 0.2% | 0.1% | 0.0018 | 0.0018 |
| rec_yd ≥ 100 | RB | 1,088 | 0.2% | 0.2% | 0.0018 | 0.0018 |
| rec_yd ≥ 100 | WR | 1,632 | 9.1% | 9.1% | 0.0793 | 0.0913 |
| rec_yd ≥ 100 | TE | 544 | 3.7% | 3.5% | 0.0334 | 0.0368 |
| pass_yd ≥ 300 | QB | 544 | 14.7% | 17.7% | 0.1220 | 0.1471 |

Reliability, every bonus and position pooled:

| P bin | n | Mean P | Observed |
|---|---:|---:|---:|
| 0.0–0.1 | 9,918 | 0.7% | 0.8% |
| 0.1–0.2 | 1,021 | 15.5% | 14.7% |
| 0.2–0.3 | 252 | 25.0% | 31.7% |
| 0.3–0.4 | 233 | 33.0% | 22.3% |

## Rare-stat shrinkage (pseudo-games, fitted on training rows)

QB:rec 1024 · QB:rec_yd 1024 · QB:rec_td 1024 · QB:targets 1024 · RB:pass_yd 256 · RB:pass_td 16 · RB:pass_int 1024 · RB:pass_att 16 · WR:pass_yd 64 · WR:pass_td 1024 · WR:pass_int 1024 · WR:rush_td 4 · WR:pass_att 64 · TE:pass_yd 16 · TE:pass_td 16 · TE:pass_int 16 · TE:rush_yd 1 · TE:rush_td 4 · TE:pass_att 16 · TE:rush_att 1
