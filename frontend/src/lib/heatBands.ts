/**
 * Where a box-score figure falls among the league's: the cut-offs the player
 * view's heat tables shade by -- green for the top quarter, then yellow,
 * orange, and red for the bottom quarter. Written by scripts/heat-bands.mjs
 * from scripts/heat-bands.sql; do not edit by hand.
 *
 * Measured over every regular-season game, 2023-2025, by each season's
 * fantasy-relevant players by volume: the top 24 quarterbacks by pass
 * attempts, 48 running backs by carries plus catches, 72 receivers and 24
 * tight ends by targets. QB 1127 games / 72 player-seasons; RB 2199 games / 144 player-seasons; WR 3281 games / 216 player-seasons; TE 1109 games / 72 player-seasons.
 *
 * Each entry is [green, yellow, orange]: a figure at or above the first is
 * green, at or above the second yellow, at or above the third orange, and
 * anything below is red. Interceptions run the other way (at or below).
 * `week` is one game; `perGame` is a season's per-game average.
 */
export const HEAT_SEASONS = "2023-2025";

export const HEAT_WEEK: Record<string, Record<string, [number, number, number]>> = {
  "QB": {
    "pass_cmp": [25, 22, 18],
    "pass_att": [38, 32, 27],
    "pass_yd": [282, 234, 183],
    "pass_td": [3, 2, 1],
    "pass_int": [0, 0, 1],
    "rush_att": [6, 4, 2],
    "rush_yd": [29, 12, 3],
    "rush_td": [1, 1, 0]
  },
  "RB": {
    "snap": [70, 56, 40],
    "rush_att": [17, 13, 8],
    "rush_yd": [76, 48, 27],
    "rush_td": [1, 1, 0],
    "targets": [5, 3, 2],
    "rec": [4, 2, 1],
    "rec_yd": [27, 12, 3],
    "rec_td": [1, 1, 0]
  },
  "WR": {
    "snap": [90, 81, 68],
    "targets": [9, 7, 5],
    "rec": [6, 4, 3],
    "rec_yd": [76, 47, 25],
    "rec_td": [1, 1, 0]
  },
  "TE": {
    "snap": [87, 77, 64],
    "targets": [8, 6, 4],
    "rec": [6, 4, 3],
    "rec_yd": [61, 39, 22],
    "rec_td": [1, 1, 0]
  }
};

export const HEAT_PER_GAME: Record<string, Record<string, [number, number, number]>> = {
  "QB": {
    "pass_cmp": [22.54, 20.68, 19.29],
    "pass_att": [34.06, 32.03, 29.62],
    "pass_yd": [254.07, 232, 207.47],
    "pass_td": [1.71, 1.51, 1.25],
    "pass_int": [0.52, 0.65, 0.77],
    "rush_att": [4.84, 3.4, 2.4],
    "rush_yd": [26.35, 15.64, 8.57],
    "rush_td": [0.29, 0.13, 0]
  },
  "RB": {
    "rush_att": [15.2, 12.97, 9.41],
    "rush_yd": [66.82, 55.05, 40.31],
    "rush_td": [0.54, 0.35, 0.23],
    "targets": [3.71, 2.92, 1.85],
    "rec": [2.94, 2.29, 1.45],
    "rec_yd": [21.92, 16.62, 11.15],
    "rec_td": [0.14, 0.07, 0]
  },
  "WR": {
    "targets": [8, 6.43, 5.06],
    "rec": [5.12, 3.93, 3.08],
    "rec_yd": [66.51, 52.39, 38.79],
    "rec_td": [0.47, 0.33, 0.21]
  },
  "TE": {
    "targets": [6.27, 5.5, 4.66],
    "rec": [4.78, 3.93, 3.29],
    "rec_yd": [48.91, 41.83, 34.4],
    "rec_td": [0.38, 0.25, 0.15]
  }
};

/** Stats where fewer is better. */
export const HEAT_INVERTED: readonly string[] = ["pass_int"];
