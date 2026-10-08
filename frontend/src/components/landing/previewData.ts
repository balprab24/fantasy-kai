import type { CareerSeason, Position, RankingRow, StatKey } from "@/lib/types";

/**
 * What the landing page's proof shows -- real rows from the real API, never
 * made up, and captured rather than fetched: the landing page is public, and
 * the data behind it is for members. Every capture here is against the 2025
 * regular season, which is over, so none of it goes stale (the hero's poster
 * shows the season being played, and lives in `heroData.ts`, dated). Anyone
 * can re-derive them:
 *
 *   GET /api/v1/rankings?profileId={1,2,3}&season=2025&scope=season&size=200
 *   GET /api/v1/players/16153/career?profileId={1,2,3}                  Nacua
 *   GET /api/v1/players/16153/gamelog?season=2025&profileId={1,2,3}     Nacua
 *
 * The 2025 boards were captured 2026-09-29 (and the 0 PPR and Half PPR top
 * twenty 2026-10-06), Nacua's career and game log 2026-10-07 -- all from the local `spring-boot:run` backend started
 * 2026-09-29 17:46 on `feat/members-only-api` (9afad36, whose tree is
 * identical to `main`'s df27d89; no commit has touched `backend/src/main`
 * since), against the local database. Same season, same stat lines, and
 * `tests/lib.test.ts` holds the days' captures to each other: Nacua's PPR
 * season captured on 10-07 is the 09-29 one, week for week.
 *
 * `espnId` is null on purpose, everywhere here. The board draws ESPN
 * headshots, and whether hotlinking them is acceptable is still an open owner
 * call (docs/map.md §5); the marketing page is the last place to widen that,
 * and it shows no player's likeness at all (owner decision 2026-09-28, kept
 * since).
 */

/**
 * The top 60 of the 2025 PPR season board. Sixty, not the handful any section
 * shows, because tiers are cut by natural breaks over exactly the top 60
 * (`TIER_WINDOW`) and positional ranks are counted from rank 1: a partial
 * board would print confident wrong numbers. The one board's PPR column, and
 * the sign-in pages' still plate, read it.
 */
export const PPR_2025: RankingRow[] = [
  { rank: 1, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 17, points: 416.6, pointsPerGame: 24.51, espnId: null },
  { rank: 2, playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, points: 375.0, pointsPerGame: 23.44, espnId: null },
  { rank: 3, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 17, points: 370.8, pointsPerGame: 21.81, espnId: null },
  { rank: 4, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 17, points: 366.9, pointsPerGame: 21.58, espnId: null },
  { rank: 5, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 16, points: 364.62, pointsPerGame: 22.79, espnId: null },
  { rank: 6, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 17, points: 362.3, pointsPerGame: 21.31, espnId: null },
  { rank: 7, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 17, points: 359.9, pointsPerGame: 21.17, espnId: null },
  { rank: 8, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 17, points: 351.96, pointsPerGame: 20.7, espnId: null },
  { rank: 9, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 17, points: 350.38, pointsPerGame: 20.61, espnId: null },
  { rank: 10, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 17, points: 338.18, pointsPerGame: 19.89, espnId: null },
  { rank: 11, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 17, points: 324.0, pointsPerGame: 19.06, espnId: null },
  { rank: 12, playerId: 35, name: "De'Von Achane", position: "RB", team: "MIA", gamesPlayed: 16, points: 322.8, pointsPerGame: 20.18, espnId: null },
  { rank: 13, playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 17, points: 318.68, pointsPerGame: 18.75, espnId: null },
  { rank: 14, playerId: 14473, name: "Trey McBride", position: "TE", team: "ARI", gamesPlayed: 17, points: 315.9, pointsPerGame: 18.58, espnId: null },
  { rank: 15, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 17, points: 313.78, pointsPerGame: 18.46, espnId: null },
  { rank: 16, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 16, points: 313.6, pointsPerGame: 19.6, espnId: null },
  { rank: 17, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 17, points: 304.84, pointsPerGame: 17.93, espnId: null },
  { rank: 18, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 17, points: 302.2, pointsPerGame: 17.78, espnId: null },
  { rank: 19, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 16, points: 299.06, pointsPerGame: 18.69, espnId: null },
  { rank: 20, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 17, points: 297.06, pointsPerGame: 17.47, espnId: null },
  { rank: 21, playerId: 17541, name: "George Pickens", position: "WR", team: "DAL", gamesPlayed: 17, points: 291.9, pointsPerGame: 17.17, espnId: null },
  { rank: 22, playerId: 9744, name: "Justin Herbert", position: "QB", team: "LAC", gamesPlayed: 16, points: 286.88, pointsPerGame: 17.93, espnId: null },
  { rank: 23, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 14, points: 285.68, pointsPerGame: 20.41, espnId: null },
  { rank: 24, playerId: 2650, name: "Chase Brown", position: "RB", team: "CIN", gamesPlayed: 17, points: 282.6, pointsPerGame: 16.62, espnId: null },
  { rank: 25, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 17, points: 279.5, pointsPerGame: 16.44, espnId: null },
  { rank: 26, playerId: 14420, name: "Baker Mayfield", position: "QB", team: "TB", gamesPlayed: 17, points: 271.92, pointsPerGame: 16.0, espnId: null },
  { rank: 27, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 16, points: 268.0, pointsPerGame: 16.75, espnId: null },
  { rank: 28, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 17, points: 263.3, pointsPerGame: 15.49, espnId: null },
  { rank: 29, playerId: 6602, name: "Travis Etienne", position: "RB", team: "NO", gamesPlayed: 17, points: 253.9, pointsPerGame: 14.94, espnId: null },
  { rank: 30, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 17, points: 245.1, pointsPerGame: 14.42, espnId: null },
  { rank: 31, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 17, points: 243.3, pointsPerGame: 14.31, espnId: null },
  { rank: 32, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 16, points: 242.8, pointsPerGame: 15.18, espnId: null },
  { rank: 33, playerId: 5164, name: "Jaxson Dart", position: "QB", team: "NYG", gamesPlayed: 14, points: 241.58, pointsPerGame: 17.26, espnId: null },
  { rank: 34, playerId: 11030, name: "Josh Jacobs", position: "RB", team: "GB", gamesPlayed: 15, points: 237.1, pointsPerGame: 15.81, espnId: null },
  { rank: 35, playerId: 5158, name: "Sam Darnold", position: "QB", team: "SEA", gamesPlayed: 17, points: 235.42, pointsPerGame: 13.85, espnId: null },
  { rank: 36, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 15, points: 235.14, pointsPerGame: 15.68, espnId: null },
  { rank: 37, playerId: 1110, name: "Saquon Barkley", position: "RB", team: "PHI", gamesPlayed: 16, points: 232.3, pointsPerGame: 14.52, espnId: null },
  { rank: 38, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 16, points: 228.6, pointsPerGame: 14.29, espnId: null },
  { rank: 39, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 14, points: 227.44, pointsPerGame: 16.25, espnId: null },
  { rank: 40, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 16, points: 227.08, pointsPerGame: 14.19, espnId: null },
  { rank: 41, playerId: 11828, name: "Daniel Jones", position: "QB", team: "IND", gamesPlayed: 13, points: 226.44, pointsPerGame: 17.42, espnId: null },
  { rank: 42, playerId: 4415, name: "Nico Collins", position: "WR", team: "HOU", gamesPlayed: 15, points: 226.2, pointsPerGame: 15.08, espnId: null },
  { rank: 43, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 14, points: 222.9, pointsPerGame: 15.92, espnId: null },
  { rank: 44, playerId: 7582, name: "Kenny Gainwell", position: "RB", team: "TB", gamesPlayed: 17, points: 221.3, pointsPerGame: 13.02, espnId: null },
  { rank: 45, playerId: 24441, name: "Michael Wilson", position: "WR", team: "ARI", gamesPlayed: 17, points: 220.6, pointsPerGame: 12.98, espnId: null },
  { rank: 46, playerId: 2624, name: "A.J. Brown", position: "WR", team: "NE", gamesPlayed: 15, points: 220.3, pointsPerGame: 14.69, espnId: null },
  { rank: 47, playerId: 24092, name: "Jameson Williams", position: "WR", team: "DET", gamesPlayed: 17, points: 219.9, pointsPerGame: 12.94, espnId: null },
  { rank: 48, playerId: 21435, name: "Courtland Sutton", position: "WR", team: "DEN", gamesPlayed: 17, points: 219.7, pointsPerGame: 12.92, espnId: null },
  { rank: 49, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 16, points: 218.04, pointsPerGame: 13.63, espnId: null },
  { rank: 50, playerId: 18906, name: "Wan'Dale Robinson", position: "WR", team: "TEN", gamesPlayed: 16, points: 217.9, pointsPerGame: 13.62, espnId: null },
  { rank: 51, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 16, points: 217.1, pointsPerGame: 13.57, espnId: null },
  { rank: 52, playerId: 5957, name: "Rico Dowdle", position: "RB", team: "PIT", gamesPlayed: 17, points: 216.3, pointsPerGame: 12.72, espnId: null },
  { rank: 53, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 13, points: 214.86, pointsPerGame: 16.53, espnId: null },
  { rank: 54, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 15, points: 211.6, pointsPerGame: 14.11, espnId: null },
  { rank: 55, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 17, points: 211.4, pointsPerGame: 12.44, espnId: null },
  { rank: 56, playerId: 17646, name: "Kyle Pitts", position: "TE", team: "ATL", gamesPlayed: 17, points: 210.8, pointsPerGame: 12.4, espnId: null },
  { rank: 57, playerId: 5703, name: "Stefon Diggs", position: "WR", team: "WAS", gamesPlayed: 17, points: 210.3, pointsPerGame: 12.37, espnId: null },
  { rank: 58, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 14, points: 208.54, pointsPerGame: 14.9, espnId: null },
  { rank: 59, playerId: 8789, name: "Breece Hall", position: "RB", team: "NYJ", gamesPlayed: 16, points: 207.66, pointsPerGame: 12.98, espnId: null },
  { rank: 60, playerId: 9387, name: "RJ Harvey", position: "RB", team: "DEN", gamesPlayed: 17, points: 206.6, pointsPerGame: 12.15, espnId: null },
];

/**
 * Where the top sixteen of the PPR board above stood on the 0 PPR board, by
 * player id. From the same two captures (`GET /api/v1/rankings?profileId=1`
 * and `=3`, season 2025, scope season, size 200), 2026-09-29.
 * `tests/lib.test.ts` holds the one board's 0 PPR places to it.
 */
export const PPR_FROM_ZERO_2025: Readonly<Record<number, number>> = {
  14480: 7,
  16153: 20,
  18803: 12,
  7871: 13,
  344: 1,
  21702: 6,
  20739: 22,
  14406: 2,
  20944: 3,
  13137: 4,
  20925: 35,
  35: 19,
  23961: 5,
  14473: 41,
  17878: 8,
  3865: 42,
};

/** The three rulesets the landing speaks in, low to high by what a catch is worth (V3). */
export type Ruleset = "0 PPR" | "Half PPR" | "PPR";

/** In the order every switch on the page shows them. */
export const RULESETS: readonly Ruleset[] = ["0 PPR", "Half PPR", "PPR"];

/** One player on the one board, with his place on each of the three 2025 boards. */
export interface OneBoardPlayer {
  playerId: number;
  name: string;
  position: Position;
  team: string;
  gamesPlayed: number;
  /** Rank on the whole 2025 board under that ruleset, and that board's figures. */
  by: Record<Ruleset, { rank: number; points: number; pointsPerGame: number }>;
}

/**
 * The one board's cast: everyone in the top 8 of the 2025 season board under
 * any of the three rulesets -- twelve players -- each with his place on all
 * three. The switch re-sorts the same stat lines three ways: under 0 PPR the
 * top five are quarterbacks; under PPR, McCaffrey, Nacua, Robinson and Gibbs
 * take over. A player's place on every board is kept, not just the ones he
 * tops, so a move is the real distance (Nacua, 20th to 2nd) rather than "new".
 *
 *   GET /api/v1/rankings?profileId={1,2,3}&season=2025&scope=season&size=200
 *
 * `tests/lib.test.ts` holds the PPR column to `PPR_2025`, the other two to the
 * top twenty below and `PPR_FROM_ZERO_2025`, and each board's top 8 to ranks 1
 * to 8 with no gaps -- which `lib/oneBoard.ts` needs to fill the board's slots.
 */
export const ONE_BOARD_2025: OneBoardPlayer[] = [
  { playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 16, by: { "0 PPR": { rank: 1, points: 364.62, pointsPerGame: 22.79 }, "Half PPR": { rank: 2, points: 364.62, pointsPerGame: 22.79 }, PPR: { rank: 5, points: 364.62, pointsPerGame: 22.79 } } },
  { playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 17, by: { "0 PPR": { rank: 2, points: 350.96, pointsPerGame: 20.64 }, "Half PPR": { rank: 3, points: 351.46, pointsPerGame: 20.67 }, PPR: { rank: 8, points: 351.96, pointsPerGame: 20.7 } } },
  { playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 17, by: { "0 PPR": { rank: 3, points: 350.38, pointsPerGame: 20.61 }, "Half PPR": { rank: 4, points: 350.38, pointsPerGame: 20.61 }, PPR: { rank: 9, points: 350.38, pointsPerGame: 20.61 } } },
  { playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 17, by: { "0 PPR": { rank: 4, points: 338.18, pointsPerGame: 19.89 }, "Half PPR": { rank: 6, points: 338.18, pointsPerGame: 19.89 }, PPR: { rank: 10, points: 338.18, pointsPerGame: 19.89 } } },
  { playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 17, by: { "0 PPR": { rank: 5, points: 316.68, pointsPerGame: 18.63 }, "Half PPR": { rank: 9, points: 317.68, pointsPerGame: 18.69 }, PPR: { rank: 13, points: 318.68, pointsPerGame: 18.75 } } },
  { playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 17, by: { "0 PPR": { rank: 6, points: 316.3, pointsPerGame: 18.61 }, "Half PPR": { rank: 5, points: 339.3, pointsPerGame: 19.96 }, PPR: { rank: 6, points: 362.3, pointsPerGame: 21.31 } } },
  { playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 17, by: { "0 PPR": { rank: 7, points: 314.6, pointsPerGame: 18.51 }, "Half PPR": { rank: 1, points: 365.6, pointsPerGame: 21.51 }, PPR: { rank: 1, points: 416.6, pointsPerGame: 24.51 } } },
  { playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 17, by: { "0 PPR": { rank: 8, points: 313.78, pointsPerGame: 18.46 }, "Half PPR": { rank: 10, points: 313.78, pointsPerGame: 18.46 }, PPR: { rank: 15, points: 313.78, pointsPerGame: 18.46 } } },
  { playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 17, by: { "0 PPR": { rank: 12, points: 291.8, pointsPerGame: 17.16 }, "Half PPR": { rank: 7, points: 331.3, pointsPerGame: 19.49 }, PPR: { rank: 3, points: 370.8, pointsPerGame: 21.81 } } },
  { playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 17, by: { "0 PPR": { rank: 13, points: 289.9, pointsPerGame: 17.05 }, "Half PPR": { rank: 8, points: 328.4, pointsPerGame: 19.32 }, PPR: { rank: 4, points: 366.9, pointsPerGame: 21.58 } } },
  { playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, by: { "0 PPR": { rank: 20, points: 246.0, pointsPerGame: 15.37 }, "Half PPR": { rank: 11, points: 310.5, pointsPerGame: 19.41 }, PPR: { rank: 2, points: 375.0, pointsPerGame: 23.44 } } },
  { playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 17, by: { "0 PPR": { rank: 22, points: 240.9, pointsPerGame: 14.17 }, "Half PPR": { rank: 13, points: 300.4, pointsPerGame: 17.67 }, PPR: { rank: 7, points: 359.9, pointsPerGame: 21.17 } } },
];

/**
 * The top twenty of the 2025 season board under 0 PPR and under Half PPR -- the
 * depth Puka Nacua starts from (20th, then 11th), so his positional rank on
 * the one board is counted from rank 1 rather than guessed. (Under PPR the
 * same twenty are `PPR_2025`'s first rows.) Captured 2026-10-06:
 *
 *   GET /api/v1/rankings?profileId=1&season=2025&scope=season&size=200    0 PPR
 *   GET /api/v1/rankings?profileId=2&season=2025&scope=season&size=200    Half PPR
 *
 * `tests/lib.test.ts` holds both to ranks 1 to 20 with no gaps, to
 * `ONE_BOARD_2025` for every player the two share, and to
 * `PPR_FROM_ZERO_2025`.
 */
export const ZERO_PPR_TOP20_2025: RankingRow[] = [
  { rank: 1, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 16, points: 364.62, pointsPerGame: 22.79, espnId: null },
  { rank: 2, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 17, points: 350.96, pointsPerGame: 20.64, espnId: null },
  { rank: 3, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 17, points: 350.38, pointsPerGame: 20.61, espnId: null },
  { rank: 4, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 17, points: 338.18, pointsPerGame: 19.89, espnId: null },
  { rank: 5, playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 17, points: 316.68, pointsPerGame: 18.63, espnId: null },
  { rank: 6, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 17, points: 316.3, pointsPerGame: 18.61, espnId: null },
  { rank: 7, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 17, points: 314.6, pointsPerGame: 18.51, espnId: null },
  { rank: 8, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 17, points: 313.78, pointsPerGame: 18.46, espnId: null },
  { rank: 9, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 17, points: 304.84, pointsPerGame: 17.93, espnId: null },
  { rank: 10, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 16, points: 299.06, pointsPerGame: 18.69, espnId: null },
  { rank: 11, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 17, points: 297.06, pointsPerGame: 17.47, espnId: null },
  { rank: 12, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 17, points: 291.8, pointsPerGame: 17.16, espnId: null },
  { rank: 13, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 17, points: 289.9, pointsPerGame: 17.05, espnId: null },
  { rank: 14, playerId: 9744, name: "Justin Herbert", position: "QB", team: "LAC", gamesPlayed: 16, points: 286.88, pointsPerGame: 17.93, espnId: null },
  { rank: 15, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 14, points: 284.68, pointsPerGame: 20.33, espnId: null },
  { rank: 16, playerId: 14420, name: "Baker Mayfield", position: "QB", team: "TB", gamesPlayed: 17, points: 271.92, pointsPerGame: 16.0, espnId: null },
  { rank: 17, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 17, points: 269.2, pointsPerGame: 15.84, espnId: null },
  { rank: 18, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 17, points: 264.5, pointsPerGame: 15.56, espnId: null },
  { rank: 19, playerId: 35, name: "De'Von Achane", position: "RB", team: "MIA", gamesPlayed: 16, points: 255.8, pointsPerGame: 15.99, espnId: null },
  { rank: 20, playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, points: 246.0, pointsPerGame: 15.37, espnId: null },
];

export const HALF_PPR_TOP20_2025: RankingRow[] = [
  { rank: 1, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 17, points: 365.6, pointsPerGame: 21.51, espnId: null },
  { rank: 2, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 16, points: 364.62, pointsPerGame: 22.79, espnId: null },
  { rank: 3, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 17, points: 351.46, pointsPerGame: 20.67, espnId: null },
  { rank: 4, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 17, points: 350.38, pointsPerGame: 20.61, espnId: null },
  { rank: 5, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 17, points: 339.3, pointsPerGame: 19.96, espnId: null },
  { rank: 6, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 17, points: 338.18, pointsPerGame: 19.89, espnId: null },
  { rank: 7, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 17, points: 331.3, pointsPerGame: 19.49, espnId: null },
  { rank: 8, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 17, points: 328.4, pointsPerGame: 19.32, espnId: null },
  { rank: 9, playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 17, points: 317.68, pointsPerGame: 18.69, espnId: null },
  { rank: 10, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 17, points: 313.78, pointsPerGame: 18.46, espnId: null },
  { rank: 11, playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, points: 310.5, pointsPerGame: 19.41, espnId: null },
  { rank: 12, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 17, points: 304.84, pointsPerGame: 17.93, espnId: null },
  { rank: 13, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 17, points: 300.4, pointsPerGame: 17.67, espnId: null },
  { rank: 14, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 16, points: 299.06, pointsPerGame: 18.69, espnId: null },
  { rank: 15, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 17, points: 297.06, pointsPerGame: 17.47, espnId: null },
  { rank: 16, playerId: 35, name: "De'Von Achane", position: "RB", team: "MIA", gamesPlayed: 16, points: 289.3, pointsPerGame: 18.08, espnId: null },
  { rank: 17, playerId: 9744, name: "Justin Herbert", position: "QB", team: "LAC", gamesPlayed: 16, points: 286.88, pointsPerGame: 17.93, espnId: null },
  { rank: 18, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 17, points: 285.7, pointsPerGame: 16.81, espnId: null },
  { rank: 19, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 14, points: 285.18, pointsPerGame: 20.37, espnId: null },
  { rank: 20, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 17, points: 272.0, pointsPerGame: 16.0, espnId: null },
];

/**
 * Puka Nacua's 2025 regular season under PPR, as `/career` returns it -- the
 * player the one board follows from 20th to 2nd, opened in the product band.
 * Captured 2026-09-29, and again 2026-10-07 identically:
 *
 *   GET /api/v1/players/16153/career?profileId=3                           PPR
 *
 * He has no game in weeks 7 and 8: the Rams played in week 7 without him, and
 * week 8 was their bye. The chart shows both as gaps.
 */
export const NACUA_2025_PPR: CareerSeason = {
  season: 2025,
  teams: ["LA"],
  age: 24,
  gamesPlayed: 16,
  points: 375.0,
  pointsPerGame: 23.44,
  posRank: 1,
  stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 105, rush_td: 1, rush_2pt: 0, rec: 129, rec_yd: 1715, rec_td: 10, rec_2pt: 0, fum_lost: 1, ret_td: 0 },
  usage: { passAtt: 0, passCmp: 0, rushAtt: 10, targets: 166 },
  weeks: [
    { week: 1, opponent: "HOU", home: true, points: 23.1, posRank: 5 },
    { week: 2, opponent: "TEN", home: false, points: 27.6, posRank: 6 },
    { week: 3, opponent: "PHI", home: false, points: 22.8, posRank: 5 },
    { week: 4, opponent: "IND", home: true, points: 36.0, posRank: 1 },
    { week: 5, opponent: "SF", home: true, points: 24.5, posRank: 5 },
    { week: 6, opponent: "BAL", home: false, points: 4.8, posRank: 65 },
    { week: 9, opponent: "NO", home: true, points: 22.8, posRank: 5 },
    { week: 10, opponent: "SF", home: false, points: 17.4, posRank: 16 },
    { week: 11, opponent: "SEA", home: true, points: 14.3, posRank: 17 },
    { week: 12, opponent: "TB", home: true, points: 16.7, posRank: 15 },
    { week: 13, opponent: "CAR", home: false, points: 13.2, posRank: 23 },
    { week: 14, opponent: "ARI", home: false, points: 35.7, posRank: 2 },
    { week: 15, opponent: "DET", home: true, points: 27.9, posRank: 2 },
    { week: 16, opponent: "SEA", home: false, points: 46.5, posRank: 1 },
    { week: 17, opponent: "ATL", home: false, points: 15.7, posRank: 13 },
    { week: 18, opponent: "ARI", home: true, points: 26.0, posRank: 3 },
  ],
};

/**
 * The three presets' rates, every scorable stat, as V3 seeds them
 * (`V3__seed_scoring_presets.sql`, "Standard", "Half PPR", "Full PPR"). Copied,
 * so everything priced with them below is checked against the API's own
 * answer. The three differ in one rate, `rec` -- which is the landing's whole
 * argument, and why it says so.
 */
/** In the order a box score reads, so a receipt lists its lines that way. */
const v3 = (rec: number): Record<StatKey, number> => ({
  pass_yd: 0.04,
  pass_td: 4,
  pass_int: -2,
  pass_2pt: 2,
  rush_yd: 0.1,
  rush_td: 6,
  rush_2pt: 2,
  rec,
  rec_yd: 0.1,
  rec_td: 6,
  rec_2pt: 2,
  fum_lost: -2,
  ret_td: 6,
});

export const PRESET_RATES: Record<Ruleset, Record<StatKey, number>> = {
  "0 PPR": v3(0),
  "Half PPR": v3(0.5),
  PPR: v3(1),
};

/** A stat line and the points the API gave it, under one ruleset. */
interface Scored {
  stats: Record<StatKey, number>;
  points: number;
}

/**
 * A stat line taken apart: each stat it recorded, its rate, what it is worth.
 * Checked as this module loads: the lines must add up to the total the API
 * returned, or it throws -- and `npm test`, which CI runs, imports this file.
 * The check used to sit in a component, on the grounds that the page was
 * prerendered and a throw failed the build; every page renders per request
 * since 2026-09-29, and there the same throw was a 500 on the landing page.
 */
function receipt({ stats, points }: Scored, rates: Record<StatKey, number>, what: string) {
  const lines = (Object.keys(rates) as StatKey[])
    .filter((stat) => stats[stat] !== 0)
    .map((stat) => ({ stat, count: stats[stat], rate: rates[stat], points: stats[stat] * rates[stat] }));
  const total = lines.reduce((sum, l) => sum + l.points, 0);
  if (Math.abs(total - points) >= 0.05) {
    throw new Error(`previewData: ${what} adds up to ${total.toFixed(2)}, the API said ${points}`);
  }
  return { lines, total };
}


/**
 * The same season under the two other presets -- the band's switch re-prices
 * his whole page with them. Captured 2026-10-07:
 *
 *   GET /api/v1/players/16153/career?profileId=1                           0 PPR
 *   GET /api/v1/players/16153/career?profileId=2                           Half PPR
 *
 * He is WR1 under all three: catches move him up the whole board (20th, 11th,
 * 2nd), not among receivers.
 */
export const NACUA_2025_ZERO_PPR: CareerSeason = {
  season: 2025,
  teams: ["LA"],
  age: 24,
  gamesPlayed: 16,
  points: 246.0,
  pointsPerGame: 15.37,
  posRank: 1,
  stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 105, rush_td: 1, rush_2pt: 0, rec: 129, rec_yd: 1715, rec_td: 10, rec_2pt: 0, fum_lost: 1, ret_td: 0 },
  usage: { passAtt: 0, passCmp: 0, rushAtt: 10, targets: 166 },
  weeks: [
    { week: 1, opponent: "HOU", home: true, points: 13.1, posRank: 8 },
    { week: 2, opponent: "TEN", home: false, points: 19.6, posRank: 6 },
    { week: 3, opponent: "PHI", home: false, points: 11.8, posRank: 17 },
    { week: 4, opponent: "IND", home: true, points: 23.0, posRank: 3 },
    { week: 5, opponent: "SF", home: true, points: 14.5, posRank: 8 },
    { week: 6, opponent: "BAL", home: false, points: 2.8, posRank: 54 },
    { week: 9, opponent: "NO", home: true, points: 15.8, posRank: 7 },
    { week: 10, opponent: "SF", home: false, points: 12.4, posRank: 18 },
    { week: 11, opponent: "SEA", home: true, points: 7.3, posRank: 24 },
    { week: 12, opponent: "TB", home: true, points: 9.7, posRank: 20 },
    { week: 13, opponent: "CAR", home: false, points: 7.2, posRank: 34 },
    { week: 14, opponent: "ARI", home: false, points: 28.7, posRank: 1 },
    { week: 15, opponent: "DET", home: true, points: 18.9, posRank: 4 },
    { week: 16, opponent: "SEA", home: false, points: 34.5, posRank: 1 },
    { week: 17, opponent: "ATL", home: false, points: 10.7, posRank: 18 },
    { week: 18, opponent: "ARI", home: true, points: 16.0, posRank: 3 },
  ],
};

export const NACUA_2025_HALF_PPR: CareerSeason = {
  season: 2025,
  teams: ["LA"],
  age: 24,
  gamesPlayed: 16,
  points: 310.5,
  pointsPerGame: 19.41,
  posRank: 1,
  stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 105, rush_td: 1, rush_2pt: 0, rec: 129, rec_yd: 1715, rec_td: 10, rec_2pt: 0, fum_lost: 1, ret_td: 0 },
  usage: { passAtt: 0, passCmp: 0, rushAtt: 10, targets: 166 },
  weeks: [
    { week: 1, opponent: "HOU", home: true, points: 18.1, posRank: 7 },
    { week: 2, opponent: "TEN", home: false, points: 23.6, posRank: 6 },
    { week: 3, opponent: "PHI", home: false, points: 17.3, posRank: 8 },
    { week: 4, opponent: "IND", home: true, points: 29.5, posRank: 1 },
    { week: 5, opponent: "SF", home: true, points: 19.5, posRank: 7 },
    { week: 6, opponent: "BAL", home: false, points: 3.8, posRank: 63 },
    { week: 9, opponent: "NO", home: true, points: 19.3, posRank: 5 },
    { week: 10, opponent: "SF", home: false, points: 14.9, posRank: 16 },
    { week: 11, opponent: "SEA", home: true, points: 10.8, posRank: 18 },
    { week: 12, opponent: "TB", home: true, points: 13.2, posRank: 16 },
    { week: 13, opponent: "CAR", home: false, points: 10.2, posRank: 25 },
    { week: 14, opponent: "ARI", home: false, points: 32.2, posRank: 1 },
    { week: 15, opponent: "DET", home: true, points: 23.4, posRank: 2 },
    { week: 16, opponent: "SEA", home: false, points: 40.5, posRank: 1 },
    { week: 17, opponent: "ATL", home: false, points: 13.2, posRank: 13 },
    { week: 18, opponent: "ARI", home: true, points: 21.0, posRank: 3 },
  ],
};

export const NACUA_2025_BY_RULESET: Record<Ruleset, CareerSeason> = {
  "0 PPR": NACUA_2025_ZERO_PPR,
  "Half PPR": NACUA_2025_HALF_PPR,
  PPR: NACUA_2025_PPR,
};

/** One regular-season game of his log: the box score, and what each preset made of it. */
export interface LogGame {
  week: number;
  opponent: string;
  home: boolean;
  usage: { passAtt: number; passCmp: number; rushAtt: number; targets: number };
  stats: Record<StatKey, number>;
  points: Record<Ruleset, number>;
}

/**
 * Puka Nacua's 2025 game log, regular season, as `/gamelog` returns it under
 * each preset. Captured 2026-10-07:
 *
 *   GET /api/v1/players/16153/gamelog?season=2025&profileId={1,2,3}
 *
 * The stat line is the box score, the same under all three (the capture was
 * checked for it); the points are each preset's. His three playoff games are
 * left out: the landing's log is the season the board ranks. Each game is
 * priced from the preset's rates as this module loads and must give the API's
 * points (`receipt` below), and `tests/lib.test.ts` holds every week to the
 * three career captures.
 */
export const NACUA_2025_LOG: LogGame[] = [
  { week: 1, opponent: "HOU", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 1, targets: 11 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 1, rush_td: 0, rush_2pt: 0, rec: 10, rec_yd: 130, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 13.1, "Half PPR": 18.1, PPR: 23.1 } },
  { week: 2, opponent: "TEN", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 1, targets: 9 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 45, rush_td: 1, rush_2pt: 0, rec: 8, rec_yd: 91, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 19.6, "Half PPR": 23.6, PPR: 27.6 } },
  { week: 3, opponent: "PHI", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 1, targets: 15 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 6, rush_td: 0, rush_2pt: 0, rec: 11, rec_yd: 112, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 11.8, "Half PPR": 17.3, PPR: 22.8 } },
  { week: 4, opponent: "IND", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 15 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 13, rec_yd: 170, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 23.0, "Half PPR": 29.5, PPR: 36.0 } },
  { week: 5, opponent: "SF", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 12 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 10, rec_yd: 85, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 14.5, "Half PPR": 19.5, PPR: 24.5 } },
  { week: 6, opponent: "BAL", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 3 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 2, rec_yd: 28, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 2.8, "Half PPR": 3.8, PPR: 4.8 } },
  { week: 9, opponent: "NO", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 1, targets: 8 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 3, rush_td: 0, rush_2pt: 0, rec: 7, rec_yd: 95, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 15.8, "Half PPR": 19.3, PPR: 22.8 } },
  { week: 10, opponent: "SF", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 6 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 5, rec_yd: 64, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 12.4, "Half PPR": 14.9, PPR: 17.4 } },
  { week: 11, opponent: "SEA", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 2, targets: 8 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 18, rush_td: 0, rush_2pt: 0, rec: 7, rec_yd: 75, rec_td: 0, rec_2pt: 0, fum_lost: 1, ret_td: 0 }, points: { "0 PPR": 7.3, "Half PPR": 10.8, PPR: 14.3 } },
  { week: 12, opponent: "TB", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 11 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 7, rec_yd: 97, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 9.7, "Half PPR": 13.2, PPR: 16.7 } },
  { week: 13, opponent: "CAR", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 9 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 6, rec_yd: 72, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 7.2, "Half PPR": 10.2, PPR: 13.2 } },
  { week: 14, opponent: "ARI", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 11 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 7, rec_yd: 167, rec_td: 2, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 28.7, "Half PPR": 32.2, PPR: 35.7 } },
  { week: 15, opponent: "DET", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 2, targets: 11 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 8, rush_td: 0, rush_2pt: 0, rec: 9, rec_yd: 181, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 18.9, "Half PPR": 23.4, PPR: 27.9 } },
  { week: 16, opponent: "SEA", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 16 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 12, rec_yd: 225, rec_td: 2, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 34.5, "Half PPR": 40.5, PPR: 46.5 } },
  { week: 17, opponent: "ATL", home: false, usage: { passAtt: 0, passCmp: 0, rushAtt: 0, targets: 10 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 0, rush_td: 0, rush_2pt: 0, rec: 5, rec_yd: 47, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 10.7, "Half PPR": 13.2, PPR: 15.7 } },
  { week: 18, opponent: "ARI", home: true, usage: { passAtt: 0, passCmp: 0, rushAtt: 2, targets: 11 }, stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 24, rush_td: 0, rush_2pt: 0, rec: 10, rec_yd: 76, rec_td: 1, rec_2pt: 0, fum_lost: 0, ret_td: 0 }, points: { "0 PPR": 16.0, "Half PPR": 21.0, PPR: 26.0 } },
];

/**
 * The week the band lights: his most catches in a game, 13 at home to
 * Indianapolis -- 13 points between 0 PPR and PPR, from one rate.
 * `tests/lib.test.ts` holds that it is the most.
 */
export const NACUA_LIT_WEEK = 4;

/**
 * "My league": Half PPR with six points for a passing touchdown instead of
 * four -- the member's own ruleset the landing's hero opens on
 * (`heroData.ts`). Saved as a ruleset through the real API (`POST
 * /api/v1/scoring-profiles`, rules exactly these) on a throwaway local
 * account, 2026-10-07, which was deleted after the capture.
 */
export const MY_LEAGUE_RATES: Record<StatKey, number> = { ...v3(0.5), pass_td: 6 };

/** Each game of the log priced under each preset, checked against the API's points as this module loads. */
export const NACUA_2025_LOG_RECEIPTS = NACUA_2025_LOG.map((game) =>
  Object.fromEntries(
    RULESETS.map((r) => [
      r,
      receipt({ stats: game.stats, points: game.points[r] }, PRESET_RATES[r], `Nacua's 2025 week ${game.week} under ${r}`),
    ]),
  ) as Record<Ruleset, ReturnType<typeof receipt>>,
);
