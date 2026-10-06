import type { CareerSeason, Position, RankingRow, StatKey } from "@/lib/types";

/**
 * What the landing page shows -- real rows from the real API, never made up,
 * and captured rather than fetched: the landing page is public, and the data
 * behind it is for members. Every capture is against the 2025 regular season,
 * which is over, so none of it goes stale. Anyone can re-derive them:
 *
 *   GET /api/v1/rankings?profileId=3&season=2025&scope=season&size=200   PPR
 *   GET /api/v1/rankings?profileId=1&season=2025&scope=season&size=200   0 PPR
 *   GET /api/v1/players/14480/career?profileId=3                          McCaffrey, PPR
 *
 * `PPR_VS_ZERO_2025` and `MCCAFFREY_2025_PPR` were captured 2026-09-28 from a
 * local backend built from `feat/player-workspace` (caafea1); everything else
 * here 2026-09-29, from one built from `feat/members-only-api` (9afad36, whose
 * tree is identical to `main`'s df27d89). Same season, same stat lines, and
 * `tests/lib.test.ts` holds the two days' captures to each other.
 *
 * `espnId` is null on purpose, everywhere here. The board draws ESPN
 * headshots, and whether hotlinking them is acceptable is still an open owner
 * call (docs/map.md §5); the marketing page is the last place to widen that,
 * and it shows no player's likeness at all (owner decision 2026-09-28, kept
 * 2026-09-29), so it shows the monogram fallback the board already has.
 */

/**
 * The top 60 of the 2025 PPR season board -- PPR because it is the ruleset the
 * whole landing page speaks in: the hero opens on it, the receipt prices it,
 * the rule-change chart ends on it, so McCaffrey is 416.6 everywhere a visitor
 * reads his total. Sixty, not the handful on screen, because tiers are cut by
 * natural breaks over exactly the top 60 (`TIER_WINDOW`): fewer rows would
 * draw different tiers than the board does.
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
 * The top 60 of the 2025 wide-receiver board under PPR -- the board the
 * landing's product band shows, filtered the way a member filters it, so it
 * is the product doing something the hero does not. Sixty, because tiers are
 * cut over exactly the top 60 (`TIER_WINDOW`). Captured 2026-10-05 from the
 * local backend named at `MCCAFFREY_2025_WEEK7` below:
 *
 *   GET /api/v1/rankings?profileId=3&season=2025&scope=season&position=WR&size=200
 *
 * The app loads 200 rows, not 60. Checked on the capture: the shown rows'
 * per-game ranks and colours, and the tiers, are the same over 60 rows as
 * over the full first page. `tests/lib.test.ts` holds every receiver here who
 * is also in `PPR_2025` to the same points, in the same order.
 */
export const WR_PPR_2025: RankingRow[] = [
  { rank: 1, playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, points: 375.0, pointsPerGame: 23.44, espnId: null },
  { rank: 2, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 17, points: 359.9, pointsPerGame: 21.17, espnId: null },
  { rank: 3, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 17, points: 324.0, pointsPerGame: 19.06, espnId: null },
  { rank: 4, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 16, points: 313.6, pointsPerGame: 19.6, espnId: null },
  { rank: 5, playerId: 17541, name: "George Pickens", position: "WR", team: "DAL", gamesPlayed: 17, points: 291.9, pointsPerGame: 17.17, espnId: null },
  { rank: 6, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 16, points: 268.0, pointsPerGame: 16.75, espnId: null },
  { rank: 7, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 17, points: 243.3, pointsPerGame: 14.31, espnId: null },
  { rank: 8, playerId: 4415, name: "Nico Collins", position: "WR", team: "HOU", gamesPlayed: 15, points: 226.2, pointsPerGame: 15.08, espnId: null },
  { rank: 9, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 14, points: 222.9, pointsPerGame: 15.92, espnId: null },
  { rank: 10, playerId: 24441, name: "Michael Wilson", position: "WR", team: "ARI", gamesPlayed: 17, points: 220.6, pointsPerGame: 12.98, espnId: null },
  { rank: 11, playerId: 2624, name: "A.J. Brown", position: "WR", team: "NE", gamesPlayed: 15, points: 220.3, pointsPerGame: 14.69, espnId: null },
  { rank: 12, playerId: 24092, name: "Jameson Williams", position: "WR", team: "DET", gamesPlayed: 17, points: 219.9, pointsPerGame: 12.94, espnId: null },
  { rank: 13, playerId: 21435, name: "Courtland Sutton", position: "WR", team: "DEN", gamesPlayed: 17, points: 219.7, pointsPerGame: 12.92, espnId: null },
  { rank: 14, playerId: 18906, name: "Wan'Dale Robinson", position: "WR", team: "TEN", gamesPlayed: 16, points: 217.9, pointsPerGame: 13.62, espnId: null },
  { rank: 15, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 15, points: 211.6, pointsPerGame: 14.11, espnId: null },
  { rank: 16, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 17, points: 211.4, pointsPerGame: 12.44, espnId: null },
  { rank: 17, playerId: 5703, name: "Stefon Diggs", position: "WR", team: "WAS", gamesPlayed: 17, points: 210.3, pointsPerGame: 12.37, espnId: null },
  { rank: 18, playerId: 17639, name: "Michael Pittman", position: "WR", team: "PIT", gamesPlayed: 17, points: 202.4, pointsPerGame: 11.91, espnId: null },
  { rank: 19, playerId: 13617, name: "Drake London", position: "WR", team: "ATL", gamesPlayed: 12, points: 201.9, pointsPerGame: 16.83, espnId: null },
  { rank: 20, playerId: 20474, name: "DeVonta Smith", position: "WR", team: "PHI", gamesPlayed: 17, points: 201.8, pointsPerGame: 11.87, espnId: null },
  { rank: 21, playerId: 11174, name: "Justin Jefferson", position: "WR", team: "MIN", gamesPlayed: 17, points: 201.5, pointsPerGame: 11.85, espnId: null },
  { rank: 22, playerId: 12945, name: "CeeDee Lamb", position: "WR", team: "DAL", gamesPlayed: 13, points: 200.9, pointsPerGame: 15.45, espnId: null },
  { rank: 23, playerId: 6357, name: "Emeka Egbuka", position: "WR", team: "TB", gamesPlayed: 17, points: 195.7, pointsPerGame: 11.51, espnId: null },
  { rank: 24, playerId: 22943, name: "Jaylen Waddle", position: "WR", team: "DEN", gamesPlayed: 16, points: 194.12, pointsPerGame: 12.13, espnId: null },
  { rank: 25, playerId: 19397, name: "Deebo Samuel Sr.", position: "WR", team: "SF", gamesPlayed: 16, points: 188.2, pointsPerGame: 11.76, espnId: null },
  { rank: 26, playerId: 15163, name: "DK Metcalf", position: "WR", team: "PIT", gamesPlayed: 15, points: 187.2, pointsPerGame: 12.48, espnId: null },
  { rank: 27, playerId: 23279, name: "Parker Washington", position: "WR", team: "JAX", gamesPlayed: 16, points: 184.7, pointsPerGame: 11.54, espnId: null },
  { rank: 28, playerId: 17553, name: "Alec Pierce", position: "WR", team: "IND", gamesPlayed: 15, points: 183.3, pointsPerGame: 12.22, espnId: null },
  { rank: 29, playerId: 347, name: "Keenan Allen", position: "WR", team: "IND", gamesPlayed: 17, points: 182.7, pointsPerGame: 10.75, espnId: null },
  { rank: 30, playerId: 14576, name: "Ladd McConkey", position: "WR", team: "LAC", gamesPlayed: 16, points: 180.9, pointsPerGame: 11.31, espnId: null },
  { rank: 31, playerId: 7359, name: "Troy Franklin", position: "WR", team: "DEN", gamesPlayed: 17, points: 177.1, pointsPerGame: 10.42, espnId: null },
  { rank: 32, playerId: 15181, name: "Jakobi Meyers", position: "WR", team: "JAX", gamesPlayed: 16, points: 175.8, pointsPerGame: 10.99, espnId: null },
  { rank: 33, playerId: 11256, name: "Jauan Jennings", position: "WR", team: "MIN", gamesPlayed: 15, points: 173.3, pointsPerGame: 11.55, espnId: null },
  { rank: 34, playerId: 11732, name: "Quentin Johnston", position: "WR", team: "LAC", gamesPlayed: 13, points: 171.2, pointsPerGame: 13.17, espnId: null },
  { rank: 35, playerId: 15664, name: "DJ Moore", position: "WR", team: "BUF", gamesPlayed: 17, points: 170.18, pointsPerGame: 10.01, espnId: null },
  { rank: 36, playerId: 19898, name: "Khalil Shakir", position: "WR", team: "BUF", gamesPlayed: 16, points: 166.4, pointsPerGame: 10.4, espnId: null },
  { rank: 37, playerId: 5920, name: "Romeo Doubs", position: "WR", team: "NE", gamesPlayed: 16, points: 165.4, pointsPerGame: 10.34, espnId: null },
  { rank: 38, playerId: 22487, name: "Tre Tucker", position: "WR", team: "LV", gamesPlayed: 17, points: 161.7, pointsPerGame: 9.51, espnId: null },
  { rank: 39, playerId: 19895, name: "Rashid Shaheed", position: "WR", team: "SEA", gamesPlayed: 18, points: 156.6, pointsPerGame: 8.7, espnId: null },
  { rank: 40, playerId: 18535, name: "Rashee Rice", position: "WR", team: "KC", gamesPlayed: 8, points: 150.1, pointsPerGame: 18.76, espnId: null },
  { rank: 41, playerId: 16657, name: "Rome Odunze", position: "WR", team: "CHI", gamesPlayed: 12, points: 146.1, pointsPerGame: 12.17, espnId: null },
  { rank: 42, playerId: 21861, name: "Brian Thomas Jr.", position: "WR", team: "JAX", gamesPlayed: 14, points: 138.8, pointsPerGame: 9.91, espnId: null },
  { rank: 43, playerId: 2783, name: "Marquise Brown", position: "WR", team: "PHI", gamesPlayed: 16, points: 137.7, pointsPerGame: 8.61, espnId: null },
  { rank: 44, playerId: 5974, name: "Josh Downs", position: "WR", team: "IND", gamesPlayed: 16, points: 136.4, pointsPerGame: 8.53, espnId: null },
  { rank: 45, playerId: 118, name: "Jordan Addison", position: "WR", team: "MIN", gamesPlayed: 14, points: 135.1, pointsPerGame: 9.65, espnId: null },
  { rank: 46, playerId: 23372, name: "Christian Watson", position: "WR", team: "GB", gamesPlayed: 10, points: 132.4, pointsPerGame: 13.24, espnId: null },
  { rank: 47, playerId: 9867, name: "Jayden Higgins", position: "WR", team: "HOU", gamesPlayed: 17, points: 129.5, pointsPerGame: 7.62, espnId: null },
  { rank: 48, playerId: 3072, name: "Luther Burden III", position: "WR", team: "CHI", gamesPlayed: 15, points: 127.9, pointsPerGame: 8.53, espnId: null },
  { rank: 49, playerId: 9316, name: "Marvin Harrison Jr.", position: "WR", team: "ARI", gamesPlayed: 12, points: 127.8, pointsPerGame: 10.65, espnId: null },
  { rank: 50, playerId: 5705, name: "Chimere Dike", position: "WR", team: "TEN", gamesPlayed: 17, points: 126.1, pointsPerGame: 7.42, espnId: null },
  { rank: 51, playerId: 2144, name: "Kayshon Boutte", position: "WR", team: "HOU", gamesPlayed: 14, points: 124.1, pointsPerGame: 8.86, espnId: null },
  { rank: 52, playerId: 11311, name: "Jerry Jeudy", position: "WR", team: "CLE", gamesPlayed: 17, points: 120.7, pointsPerGame: 7.1, espnId: null },
  { rank: 53, playerId: 23313, name: "Malik Washington", position: "WR", team: "MIA", gamesPlayed: 17, points: 116.7, pointsPerGame: 6.86, espnId: null },
  { rank: 54, playerId: 829, name: "Elic Ayomanor", position: "WR", team: "TEN", gamesPlayed: 16, points: 116.5, pointsPerGame: 7.28, espnId: null },
  { rank: 55, playerId: 12862, name: "Cooper Kupp", position: "WR", team: "SEA", gamesPlayed: 16, points: 116.3, pointsPerGame: 7.27, espnId: null },
  { rank: 56, playerId: 14933, name: "Terry McLaurin", position: "WR", team: "WAS", gamesPlayed: 10, points: 114.2, pointsPerGame: 11.42, espnId: null },
  { rank: 57, playerId: 7085, name: "Ryan Flournoy", position: "WR", team: "DAL", gamesPlayed: 15, points: 114.0, pointsPerGame: 7.6, espnId: null },
  { rank: 58, playerId: 10168, name: "Mack Hollins", position: "WR", team: "NE", gamesPlayed: 14, points: 113.4, pointsPerGame: 8.1, espnId: null },
  { rank: 59, playerId: 24735, name: "Xavier Worthy", position: "WR", team: "KC", gamesPlayed: 14, points: 109.9, pointsPerGame: 7.85, espnId: null },
  { rank: 60, playerId: 4325, name: "Keon Coleman", position: "WR", team: "BUF", gamesPlayed: 12, points: 102.4, pointsPerGame: 8.53, espnId: null },
];

/**
 * Where the top sixteen of the WR board above stood on the 0 PPR WR board,
 * for its "vs 0 PPR" column. From the same request with `profileId=1`,
 * 2026-10-05. A missing row would print "was 200+" for a receiver who was
 * 14th, so `tests/lib.test.ts` holds every shown rank to having one.
 */
export const WR_PPR_FROM_ZERO_2025: Readonly<Record<number, number>> = {
  16153: 1,
  20739: 2,
  20925: 3,
  3865: 5,
  17541: 4,
  16743: 6,
  7100: 8,
  4415: 9,
  63: 7,
  24441: 13,
  2624: 14,
  24092: 10,
  21435: 12,
  18906: 23,
  9871: 11,
  14975: 15,
};

/**
 * Where the top sixteen of the PPR board above stood on the 0 PPR board, by
 * player id -- the landing's board slice shows the move between the two, and
 * it shows ranks 6 to 16. From the same two captures (`GET
 * /api/v1/rankings?profileId=1` and `=3`, season 2025, scope season, size
 * 200), 2026-09-29. A row missing here would print "was 200+" for a player who
 * was 41st -- a default claiming an absence -- so `tests/lib.test.ts` holds
 * every sliced rank to having one.
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

/**
 * The top of the 2025 PPR board, each with its place under 0 PPR: the same
 * season and the same stat lines, one rate changed (a point per catch).
 * `zeroPprRank` is the rank on the 0 PPR board above.
 */
export const PPR_VS_ZERO_2025: { row: RankingRow; zeroPprRank: number }[] = [
  { row: { rank: 1, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 17, points: 416.6, pointsPerGame: 24.51, espnId: null }, zeroPprRank: 7 },
  { row: { rank: 2, playerId: 16153, name: "Puka Nacua", position: "WR", team: "LA", gamesPlayed: 16, points: 375.0, pointsPerGame: 23.44, espnId: null }, zeroPprRank: 20 },
  { row: { rank: 3, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 17, points: 370.8, pointsPerGame: 21.81, espnId: null }, zeroPprRank: 12 },
  { row: { rank: 4, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 17, points: 366.9, pointsPerGame: 21.58, espnId: null }, zeroPprRank: 13 },
  { row: { rank: 5, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 16, points: 364.62, pointsPerGame: 22.79, espnId: null }, zeroPprRank: 1 },
  { row: { rank: 6, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 17, points: 362.3, pointsPerGame: 21.31, espnId: null }, zeroPprRank: 6 },
];

/** Christian McCaffrey's 2025 regular season under PPR, as `/career` returns it. */
export const MCCAFFREY_2025_PPR: CareerSeason = {
  season: 2025,
  teams: ["SF"],
  age: 29,
  gamesPlayed: 17,
  points: 416.6,
  pointsPerGame: 24.51,
  posRank: 1,
  stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 1202, rush_td: 10, rush_2pt: 0, rec: 102, rec_yd: 924, rec_td: 7, rec_2pt: 0, fum_lost: 0, ret_td: 0 },
  usage: { passAtt: 1, passCmp: 0, rushAtt: 311, targets: 129 },
  weeks: [
    { week: 1, opponent: "SEA", home: false, points: 23.2, posRank: 3 },
    { week: 2, opponent: "NO", home: false, points: 22.7, posRank: 5 },
    { week: 3, opponent: "ARI", home: true, points: 24.0, posRank: 6 },
    { week: 4, opponent: "JAX", home: true, points: 26.1, posRank: 8 },
    { week: 5, opponent: "LA", home: false, points: 27.9, posRank: 4 },
    { week: 6, opponent: "TB", home: false, points: 24.1, posRank: 7 },
    { week: 7, opponent: "ATL", home: true, points: 39.1, posRank: 1 },
    { week: 8, opponent: "HOU", home: false, points: 9.8, posRank: 25 },
    { week: 9, opponent: "NYG", home: false, points: 34.3, posRank: 1 },
    { week: 10, opponent: "LA", home: true, points: 17.6, posRank: 10 },
    { week: 11, opponent: "ARI", home: false, points: 35.1, posRank: 1 },
    { week: 12, opponent: "CAR", home: true, points: 27.2, posRank: 2 },
    { week: 13, opponent: "CLE", home: false, points: 17.4, posRank: 11 },
    { week: 15, opponent: "TEN", home: true, points: 15.7, posRank: 14 },
    { week: 16, opponent: "IND", home: false, points: 32.6, posRank: 2 },
    { week: 17, opponent: "CHI", home: true, points: 28.1, posRank: 4 },
    { week: 18, opponent: "SEA", home: true, points: 11.7, posRank: 18 },
  ],
};

/**
 * McCaffrey's 2025 regular season under the two other presets, for the hero's
 * ruleset switch: the same stat lines, scored three ways. Captured 2026-09-29
 * (the backend named at the top of this file) -- the season is over, so these
 * do not go stale:
 *
 *   GET /api/v1/players/14480/career?profileId=1                           0 PPR
 *   GET /api/v1/players/14480/career?profileId=2                           Half PPR
 *   GET /api/v1/rankings?profileId={1,2,3}&season=2025&scope=season&size=200
 *
 * The last gives his overall place on each board: 7th under 0 PPR, 1st under
 * Half PPR and PPR -- the same 7th that `PPR_VS_ZERO_2025` records. Under 0 PPR
 * he is also RB2, not RB1: the rule moves his positional rank too.
 * `tests/lib.test.ts` holds each season's weeks to its total.
 */
export interface HeroSeason {
  ruleset: "0 PPR" | "Half PPR" | "PPR";
  points: number;
  pointsPerGame: number;
  gamesPlayed: number;
  /** Among running backs, by season points. */
  posRank: number;
  /** On the whole 2025 board under this ruleset. */
  overallRank: number;
  weeks: { week: number; points: number; posRank: number | null }[];
}

export const MCCAFFREY_2025_ZERO_PPR: HeroSeason = {
  ruleset: "0 PPR",
  points: 314.6,
  pointsPerGame: 18.51,
  gamesPlayed: 17,
  posRank: 2,
  overallRank: 7,
  weeks: [
      { week: 1, points: 14.2, posRank: 9 },
      { week: 2, points: 16.7, posRank: 7 },
      { week: 3, points: 14.0, posRank: 12 },
      { week: 4, points: 20.1, posRank: 9 },
      { week: 5, points: 19.9, posRank: 6 },
      { week: 6, points: 17.1, posRank: 9 },
      { week: 7, points: 32.1, posRank: 2 },
      { week: 8, points: 6.8, posRank: 28 },
      { week: 9, points: 29.3, posRank: 1 },
      { week: 10, points: 9.6, posRank: 21 },
      { week: 11, points: 30.1, posRank: 2 },
      { week: 12, points: 20.2, posRank: 4 },
      { week: 13, points: 13.4, posRank: 16 },
      { week: 15, points: 14.7, posRank: 13 },
      { week: 16, points: 26.6, posRank: 4 },
      { week: 17, points: 24.1, posRank: 5 },
      { week: 18, points: 5.7, posRank: 30 },
  ],
};

export const MCCAFFREY_2025_HALF_PPR: HeroSeason = {
  ruleset: "Half PPR",
  points: 365.6,
  pointsPerGame: 21.51,
  gamesPlayed: 17,
  posRank: 1,
  overallRank: 1,
  weeks: [
      { week: 1, points: 18.7, posRank: 4 },
      { week: 2, points: 19.7, posRank: 5 },
      { week: 3, points: 19.0, posRank: 8 },
      { week: 4, points: 23.1, posRank: 8 },
      { week: 5, points: 23.9, posRank: 6 },
      { week: 6, points: 20.6, posRank: 9 },
      { week: 7, points: 35.6, posRank: 1 },
      { week: 8, points: 8.3, posRank: 27 },
      { week: 9, points: 31.8, posRank: 1 },
      { week: 10, points: 13.6, posRank: 16 },
      { week: 11, points: 32.6, posRank: 2 },
      { week: 12, points: 23.7, posRank: 3 },
      { week: 13, points: 15.4, posRank: 12 },
      { week: 15, points: 15.2, posRank: 14 },
      { week: 16, points: 29.6, posRank: 3 },
      { week: 17, points: 26.1, posRank: 4 },
      { week: 18, points: 8.7, posRank: 23 },
  ],
};

/**
 * A captured value that must be there. `posRank` is nullable in the career
 * shape (v1 ranks four positions), and a `?? 1` here would print "RB1" on the
 * hero whether or not the capture said so -- a default hiding an absence.
 * Throws instead, which fails `npm test` on import.
 */
function captured<T>(value: T | null | undefined, what: string): T {
  if (value === null || value === undefined) {
    throw new Error(`previewData: ${what} is missing from the capture`);
  }
  return value;
}

/** The PPR season is the career capture above, restated in the hero's shape. */
export const MCCAFFREY_2025_FULL_PPR: HeroSeason = {
  ruleset: "PPR",
  points: MCCAFFREY_2025_PPR.points,
  pointsPerGame: MCCAFFREY_2025_PPR.pointsPerGame,
  gamesPlayed: MCCAFFREY_2025_PPR.gamesPlayed,
  posRank: captured(MCCAFFREY_2025_PPR.posRank, "McCaffrey's 2025 PPR positional rank"),
  overallRank: 1,
  weeks: MCCAFFREY_2025_PPR.weeks.map(({ week, points, posRank }) => ({ week, points, posRank })),
};

/** In the order the switch shows them: what a reception is worth, low to high. */
export const MCCAFFREY_2025_BY_RULESET: HeroSeason[] = [
  MCCAFFREY_2025_ZERO_PPR,
  MCCAFFREY_2025_HALF_PPR,
  MCCAFFREY_2025_FULL_PPR,
];

/** The three rulesets the landing speaks in, low to high by what a catch is worth. */
export type Ruleset = HeroSeason["ruleset"];

/** One player on the hero's board, with his place on each of the three 2025 boards. */
export interface HeroBoardPlayer {
  playerId: number;
  name: string;
  position: Position;
  team: string;
  gamesPlayed: number;
  /** Rank on the whole 2025 board under that ruleset, and that board's figures. */
  by: Record<Ruleset, { rank: number; points: number; pointsPerGame: number }>;
}

/**
 * The hero's board: everyone in the top 8 of the 2025 season board under any
 * of the three rulesets -- twelve players -- each with his place on all three.
 * The switch re-sorts the same stat lines three ways: under 0 PPR the top five
 * are quarterbacks; under PPR, McCaffrey, Nacua, Robinson and Gibbs take over.
 * A player's place on every board is kept, not just the ones he tops, so a
 * move is the real distance (Nacua, 20th to 2nd) rather than "new".
 *
 * From the same three captures as `PPR_VS_ZERO_2025` and the hero's seasons
 * (2026-09-29, the backend named at the top of this file):
 *
 *   GET /api/v1/rankings?profileId={1,2,3}&season=2025&scope=season&size=200
 *
 * `tests/lib.test.ts` holds the PPR column to `PPR_2025`, the 0 PPR ranks to
 * `PPR_FROM_ZERO_2025`, McCaffrey to `MCCAFFREY_2025_BY_RULESET`, and each
 * board's top 8 to ranks 1 to 8 with no gaps -- which `lib/heroBoard.ts` needs
 * to derive a positional rank without guessing.
 */
export const HERO_BOARD_2025: HeroBoardPlayer[] = [
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
 * The top twenty of the 2025 season board under 0 PPR and under Half PPR --
 * the landing's three boards name every place down to 20th, the depth Puka
 * Nacua starts from, so the reader sees who he passes rather than an empty
 * ruler. (Under PPR the same twenty are `PPR_2025`'s first rows.) Captured
 * 2026-10-06 from the local backend on :8080 -- the `spring-boot:run` process
 * started 2026-09-29 17:46 that `MCCAFFREY_2025_WEEK7` was captured from --
 * against the local database:
 *
 *   GET /api/v1/rankings?profileId=1&season=2025&scope=season&size=200    0 PPR
 *   GET /api/v1/rankings?profileId=2&season=2025&scope=season&size=200    Half PPR
 *
 * `tests/lib.test.ts` holds both to ranks 1 to 20 with no gaps, to
 * `HERO_BOARD_2025` for every player the two share, and to
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

/** One finished season on a player's career line. */
export interface CareerLine {
  season: number;
  gamesPlayed: number;
  points: number;
  pointsPerGame: number;
  /** Among his position, by season points under the ruleset. */
  posRank: number;
}

/**
 * Puka Nacua's finished regular seasons under PPR, as `/career` returns them:
 * WR4 as a rookie, WR26 across eleven games in 2024, WR1 in 2025. Captured
 * 2026-09-29 (the backend named at the top of this file):
 *
 *   GET /api/v1/players/16153/career?profileId=3                           PPR
 *
 * The capture also carried 2026, one game old; it is left out, because the
 * landing only shows seasons that are over and cannot go stale.
 */
export const NACUA_SEASONS_PPR: CareerLine[] = [
  { season: 2023, gamesPlayed: 17, points: 298.5, pointsPerGame: 17.56, posRank: 4 },
  { season: 2024, gamesPlayed: 11, points: 206.6, pointsPerGame: 18.78, posRank: 26 },
  { season: 2025, gamesPlayed: 16, points: 375.0, pointsPerGame: 23.44, posRank: 1 },
];

/**
 * Puka Nacua's 2025 regular season under PPR, as `/career` returns it -- the
 * landing's "every player, week by week" chart, and the same player whose
 * 20th-to-2nd the rule-change chart draws. Captured 2026-09-29:
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
 * McCaffrey's PPR season taken apart. Summed per stat over the season rather
 * than per game, which is exact here only because the presets have no
 * threshold bonuses -- with bonuses the board scores each game first, which is
 * why it always does.
 */
export const MCCAFFREY_2025_PPR_RECEIPT = receipt(MCCAFFREY_2025_PPR, PRESET_RATES.PPR, "McCaffrey's 2025 PPR season");

/**
 * One game: Christian McCaffrey's 2025 week 7, at home to Atlanta -- the line
 * the landing's "every game" section scores. Captured 2026-10-05 from a local
 * backend running `9afad36`'s source (no commit has touched `backend/src/main`
 * since), against the local database:
 *
 *   GET /api/v1/players/14480/gamelog?profileId={1,2,3}&season=2025
 *
 * The stat line is the box score, so it is the same under all three profiles;
 * the points are each profile's. Below, each preset's V3 rates are applied to
 * the line and must give that profile's points, and `tests/lib.test.ts` holds
 * the points to the week 7 already captured in the three seasons above.
 */
export const MCCAFFREY_2025_WEEK7 = {
  season: 2025,
  week: 7,
  opponent: "ATL",
  home: true,
  usage: { passAtt: 0, passCmp: 0, rushAtt: 24, targets: 8 },
  stats: { pass_yd: 0, pass_td: 0, pass_int: 0, pass_2pt: 0, rush_yd: 129, rush_td: 2, rush_2pt: 0, rec: 7, rec_yd: 72, rec_td: 0, rec_2pt: 0, fum_lost: 0, ret_td: 0 },
  points: { "0 PPR": 32.1, "Half PPR": 35.6, PPR: 39.1 } satisfies Record<Ruleset, number>,
};

/**
 * The 49ers' bye in 2025: the one week of 1-18 with no SF game, checked against
 * the 2025 schedule in the local database (2026-10-05). McCaffrey's only week
 * without a game is this one, which `tests/lib.test.ts` holds -- so a week
 * missing for any other reason can never be labelled a bye by default.
 */
export const SF_2025_BYE_WEEK = 14;

/** The week 7 line priced under each preset, each checked against the API's points as this module loads. */
export const MCCAFFREY_2025_WEEK7_RECEIPTS = Object.fromEntries(
  (Object.keys(PRESET_RATES) as Ruleset[]).map((r) => [
    r,
    receipt(
      { stats: MCCAFFREY_2025_WEEK7.stats, points: MCCAFFREY_2025_WEEK7.points[r] },
      PRESET_RATES[r],
      `McCaffrey's 2025 week 7 under ${r}`,
    ),
  ]),
) as Record<Ruleset, ReturnType<typeof receipt>>;
