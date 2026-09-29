import type { CareerSeason, RankingRow } from "@/lib/types";

/**
 * What the landing page's phone shows -- real rows from the real API, never
 * made up, and captured rather than fetched: the landing page is public and
 * static, and the data behind it is for members.
 *
 * Captured 2026-09-28 from a local backend built from `feat/player-workspace`
 * (caafea1), against the 2025 regular season, which is over -- so these do not
 * go stale. Anyone can re-derive them:
 *
 *   GET /api/v1/rankings?profileId=2&season=2025&scope=season&size=200   Half PPR
 *   GET /api/v1/rankings?profileId=3&season=2025&scope=season&size=200   PPR
 *   GET /api/v1/rankings?profileId=1&season=2025&scope=season&size=200   0 PPR
 *   GET /api/v1/players/14480/career?profileId=3                          McCaffrey, PPR
 *
 * `espnId` is null on purpose. The board draws ESPN headshots, and whether
 * hotlinking them is acceptable is still an open owner call (docs/map.md §5);
 * the marketing page is the last place to widen that, so the phone shows the
 * monogram fallback the board already has.
 */

/**
 * The top 60 of the 2025 Half PPR season board -- Half PPR because it is the
 * board a visitor sees first (`useSelectedProfile`). Sixty, not the handful on
 * screen, because tiers are cut by natural breaks over exactly the top 60
 * (`TIER_WINDOW`): fewer rows would draw different tiers than the board does.
 */
export const HALF_PPR_2025: RankingRow[] = [
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
  { rank: 21, playerId: 14420, name: "Baker Mayfield", position: "QB", team: "TB", gamesPlayed: 17, points: 271.92, pointsPerGame: 16.0, espnId: null },
  { rank: 22, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 17, points: 265.5, pointsPerGame: 15.62, espnId: null },
  { rank: 23, playerId: 14473, name: "Trey McBride", position: "TE", team: "ARI", gamesPlayed: 17, points: 252.9, pointsPerGame: 14.88, espnId: null },
  { rank: 24, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 16, points: 251.1, pointsPerGame: 15.69, espnId: null },
  { rank: 25, playerId: 2650, name: "Chase Brown", position: "RB", team: "CIN", gamesPlayed: 17, points: 248.1, pointsPerGame: 14.59, espnId: null },
  { rank: 26, playerId: 17541, name: "George Pickens", position: "WR", team: "DAL", gamesPlayed: 17, points: 245.4, pointsPerGame: 14.44, espnId: null },
  { rank: 27, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 17, points: 245.3, pointsPerGame: 14.43, espnId: null },
  { rank: 28, playerId: 5164, name: "Jaxson Dart", position: "QB", team: "NYG", gamesPlayed: 14, points: 241.58, pointsPerGame: 17.26, espnId: null },
  { rank: 29, playerId: 6602, name: "Travis Etienne", position: "RB", team: "NO", gamesPlayed: 17, points: 235.9, pointsPerGame: 13.88, espnId: null },
  { rank: 30, playerId: 5158, name: "Sam Darnold", position: "QB", team: "SEA", gamesPlayed: 17, points: 235.42, pointsPerGame: 13.85, espnId: null },
  { rank: 31, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 15, points: 235.14, pointsPerGame: 15.68, espnId: null },
  { rank: 32, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 14, points: 227.44, pointsPerGame: 16.25, espnId: null },
  { rank: 33, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 16, points: 226.58, pointsPerGame: 14.16, espnId: null },
  { rank: 34, playerId: 11828, name: "Daniel Jones", position: "QB", team: "IND", gamesPlayed: 13, points: 226.44, pointsPerGame: 17.42, espnId: null },
  { rank: 35, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 16, points: 225.3, pointsPerGame: 14.08, espnId: null },
  { rank: 36, playerId: 11030, name: "Josh Jacobs", position: "RB", team: "GB", gamesPlayed: 15, points: 219.1, pointsPerGame: 14.61, espnId: null },
  { rank: 37, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 16, points: 218.04, pointsPerGame: 13.63, espnId: null },
  { rank: 38, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 16, points: 218.0, pointsPerGame: 13.63, espnId: null },
  { rank: 39, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 17, points: 217.6, pointsPerGame: 12.8, espnId: null },
  { rank: 40, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 13, points: 214.86, pointsPerGame: 16.53, espnId: null },
  { rank: 41, playerId: 1110, name: "Saquon Barkley", position: "RB", team: "PHI", gamesPlayed: 16, points: 213.8, pointsPerGame: 13.36, espnId: null },
  { rank: 42, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 16, points: 211.6, pointsPerGame: 13.23, espnId: null },
  { rank: 43, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 14, points: 208.54, pointsPerGame: 14.9, espnId: null },
  { rank: 44, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 17, points: 200.3, pointsPerGame: 11.78, espnId: null },
  { rank: 45, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 16, points: 197.1, pointsPerGame: 12.32, espnId: null },
  { rank: 46, playerId: 5957, name: "Rico Dowdle", position: "RB", team: "PIT", gamesPlayed: 17, points: 196.8, pointsPerGame: 11.58, espnId: null },
  { rank: 47, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 14, points: 192.9, pointsPerGame: 13.78, espnId: null },
  { rank: 48, playerId: 4415, name: "Nico Collins", position: "WR", team: "HOU", gamesPlayed: 15, points: 190.7, pointsPerGame: 12.71, espnId: null },
  { rank: 49, playerId: 8789, name: "Breece Hall", position: "RB", team: "NYJ", gamesPlayed: 16, points: 189.66, pointsPerGame: 11.85, espnId: null },
  { rank: 50, playerId: 9669, name: "TreVeyon Henderson", position: "RB", team: "NE", gamesPlayed: 17, points: 188.7, pointsPerGame: 11.1, espnId: null },
  { rank: 51, playerId: 24092, name: "Jameson Williams", position: "WR", team: "DET", gamesPlayed: 17, points: 187.4, pointsPerGame: 11.02, espnId: null },
  { rank: 52, playerId: 23182, name: "Cam Ward", position: "QB", team: "TEN", gamesPlayed: 17, points: 186.66, pointsPerGame: 10.98, espnId: null },
  { rank: 53, playerId: 7582, name: "Kenny Gainwell", position: "RB", team: "TB", gamesPlayed: 17, points: 184.8, pointsPerGame: 10.87, espnId: null },
  { rank: 54, playerId: 9387, name: "RJ Harvey", position: "RB", team: "DEN", gamesPlayed: 17, points: 183.1, pointsPerGame: 10.77, espnId: null },
  { rank: 55, playerId: 21435, name: "Courtland Sutton", position: "WR", team: "DEN", gamesPlayed: 17, points: 182.7, pointsPerGame: 10.75, espnId: null },
  { rank: 56, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 15, points: 182.1, pointsPerGame: 12.14, espnId: null },
  { rank: 57, playerId: 24441, name: "Michael Wilson", position: "WR", team: "ARI", gamesPlayed: 17, points: 181.6, pointsPerGame: 10.68, espnId: null },
  { rank: 58, playerId: 2624, name: "A.J. Brown", position: "WR", team: "NE", gamesPlayed: 15, points: 181.3, pointsPerGame: 12.09, espnId: null },
  { rank: 59, playerId: 18022, name: "Brock Purdy", position: "QB", team: "SF", gamesPlayed: 9, points: 177.38, pointsPerGame: 19.71, espnId: null },
  { rank: 60, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 17, points: 176.4, pointsPerGame: 10.38, espnId: null },
];

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
