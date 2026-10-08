import type { RankingRow, ScoringProfile } from "@/lib/types";

/**
 * The hero's board: the real 2026 rankings under each scoring its switch
 * offers -- captured, because the landing page is public and the board is for
 * members (north-star §2). Captured 2026-10-07 from the local backend (the
 * process running `9afad36`'s source that `previewData.ts`'s 2025 captures
 * came from), against the local mirror, which held the 2026 regular season
 * through week 4 (64 games):
 *
 *   GET /api/v1/rankings?profileId=1&season=2026&scope=season&size=200   0 PPR
 *   GET /api/v1/rankings?profileId=2&season=2026&scope=season&size=200   Half PPR
 *   GET /api/v1/rankings?profileId=3&season=2026&scope=season&size=200   PPR
 *   GET /api/v1/rankings?profileId=<My league>&season=2026&scope=season&size=200
 *
 * "My league" is Half PPR with six points a passing touchdown -- the ruleset
 * the page's settings section prices (`MY_LEAGUE_RATES` in `previewData.ts`),
 * saved through the real API on a throwaway local account that was deleted
 * after. Each board is kept to its top 60: tiers are cut by natural breaks
 * over exactly that many (`TIER_WINDOW`), and every player the hero can show
 * stands inside every board's 60 (the deepest is 29th, which
 * `tests/lib.test.ts` holds), so a move is always the real distance.
 *
 * Unlike every 2025 capture, this goes stale as the season moves. The page
 * says which week it runs through, read off the rows (`HERO_THROUGH_WEEK`)
 * rather than a date written by hand, and a re-capture is these requests
 * again. `espnId` is null everywhere: the landing shows no player's likeness.
 */

/** The season the board shows: the requests' own parameter. */
export const HERO_SEASON = 2026;

/** Players on the whole board (not shown; kept so a re-capture can be checked against it). */
export const HERO_TOTAL = 474;

/**
 * What "My league" is, said wherever it is shown: Half PPR with six points a
 * passing touchdown. `tests/lib.test.ts` holds it to `MY_LEAGUE_RATES`, the
 * rules the ruleset was saved with, so the words cannot drift from the board.
 */
export const HERO_LEAGUE_DETAIL = "Half PPR · 6-pt passing TDs";

/** The scorings the hero's switch offers, in its order. */
export type HeroRuleset = "0 PPR" | "Half PPR" | "PPR" | "My league";
export const HERO_RULESETS: readonly HeroRuleset[] = ["0 PPR", "Half PPR", "PPR", "My league"];

/** The 2026 board's top 60 under each, as the API returned them. */
export const HERO_BOARDS_2026: Record<HeroRuleset, RankingRow[]> = {
  "0 PPR": [
    { rank: 1, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 4, points: 111.46, pointsPerGame: 27.87, espnId: null },
    { rank: 2, playerId: 18022, name: "Brock Purdy", position: "QB", team: "SF", gamesPlayed: 4, points: 100.48, pointsPerGame: 25.12, espnId: null },
    { rank: 3, playerId: 23056, name: "Kenneth Walker III", position: "RB", team: "KC", gamesPlayed: 4, points: 98.1, pointsPerGame: 24.53, espnId: null },
    { rank: 4, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 4, points: 94.0, pointsPerGame: 23.5, espnId: null },
    { rank: 5, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 91.4, pointsPerGame: 22.85, espnId: null },
    { rank: 6, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 4, points: 90.62, pointsPerGame: 22.66, espnId: null },
    { rank: 7, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 4, points: 86.06, pointsPerGame: 21.52, espnId: null },
    { rank: 8, playerId: 20079, name: "Tyler Shough", position: "QB", team: "NO", gamesPlayed: 4, points: 85.32, pointsPerGame: 21.33, espnId: null },
    { rank: 9, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 4, points: 84.66, pointsPerGame: 21.17, espnId: null },
    { rank: 10, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 4, points: 83.8, pointsPerGame: 20.95, espnId: null },
    { rank: 11, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 4, points: 83.58, pointsPerGame: 20.9, espnId: null },
    { rank: 12, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 4, points: 81.2, pointsPerGame: 20.3, espnId: null },
    { rank: 13, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 4, points: 79.08, pointsPerGame: 19.77, espnId: null },
    { rank: 14, playerId: 4715, name: "Kirk Cousins", position: "QB", team: "LV", gamesPlayed: 4, points: 78.84, pointsPerGame: 19.71, espnId: null },
    { rank: 15, playerId: 3167, name: "Joe Burrow", position: "QB", team: "CIN", gamesPlayed: 4, points: 76.64, pointsPerGame: 19.16, espnId: null },
    { rank: 16, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 4, points: 76.2, pointsPerGame: 19.05, espnId: null },
    { rank: 17, playerId: 12945, name: "CeeDee Lamb", position: "WR", team: "DAL", gamesPlayed: 4, points: 75.2, pointsPerGame: 18.8, espnId: null },
    { rank: 18, playerId: 10441, name: "Chuba Hubbard", position: "RB", team: "CAR", gamesPlayed: 4, points: 69.0, pointsPerGame: 17.25, espnId: null },
    { rank: 19, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 4, points: 68.7, pointsPerGame: 17.18, espnId: null },
    { rank: 20, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 4, points: 68.24, pointsPerGame: 17.06, espnId: null },
    { rank: 21, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 4, points: 67.02, pointsPerGame: 16.76, espnId: null },
    { rank: 22, playerId: 23375, name: "Deshaun Watson", position: "QB", team: "CLE", gamesPlayed: 4, points: 66.9, pointsPerGame: 16.73, espnId: null },
    { rank: 23, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 4, points: 66.8, pointsPerGame: 16.7, espnId: null },
    { rank: 24, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 4, points: 65.12, pointsPerGame: 16.28, espnId: null },
    { rank: 25, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 4, points: 64.84, pointsPerGame: 16.21, espnId: null },
    { rank: 26, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 4, points: 61.66, pointsPerGame: 15.42, espnId: null },
    { rank: 27, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 4, points: 61.16, pointsPerGame: 15.29, espnId: null },
    { rank: 28, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 4, points: 60.5, pointsPerGame: 15.13, espnId: null },
    { rank: 29, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 4, points: 60.3, pointsPerGame: 15.08, espnId: null },
    { rank: 30, playerId: 20508, name: "Geno Smith", position: "QB", team: "NYJ", gamesPlayed: 4, points: 59.68, pointsPerGame: 14.92, espnId: null },
    { rank: 31, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 4, points: 59.02, pointsPerGame: 14.75, espnId: null },
    { rank: 32, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 4, points: 58.0, pointsPerGame: 14.5, espnId: null },
    { rank: 33, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 4, points: 57.26, pointsPerGame: 14.32, espnId: null },
    { rank: 34, playerId: 23372, name: "Christian Watson", position: "WR", team: "GB", gamesPlayed: 4, points: 57.1, pointsPerGame: 14.28, espnId: null },
    { rank: 35, playerId: 23182, name: "Cam Ward", position: "QB", team: "TEN", gamesPlayed: 4, points: 56.74, pointsPerGame: 14.18, espnId: null },
    { rank: 36, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 4, points: 55.1, pointsPerGame: 13.78, espnId: null },
    { rank: 37, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 4, points: 54.9, pointsPerGame: 13.73, espnId: null },
    { rank: 38, playerId: 15567, name: "Kyle Monangai", position: "RB", team: "CHI", gamesPlayed: 4, points: 54.3, pointsPerGame: 13.58, espnId: null },
    { rank: 39, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 4, points: 53.5, pointsPerGame: 13.37, espnId: null },
    { rank: 40, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 4, points: 51.0, pointsPerGame: 12.75, espnId: null },
    { rank: 41, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 3, points: 49.2, pointsPerGame: 16.4, espnId: null },
    { rank: 42, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 4, points: 48.5, pointsPerGame: 12.13, espnId: null },
    { rank: 43, playerId: 12621, name: "George Kittle", position: "TE", team: "SF", gamesPlayed: 4, points: 48.4, pointsPerGame: 12.1, espnId: null },
    { rank: 44, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 4, points: 47.76, pointsPerGame: 11.94, espnId: null },
    { rank: 45, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 4, points: 46.1, pointsPerGame: 11.53, espnId: null },
    { rank: 46, playerId: 18805, name: "Brian Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 45.5, pointsPerGame: 11.38, espnId: null },
    { rank: 47, playerId: 22604, name: "Bhayshul Tuten", position: "RB", team: "JAX", gamesPlayed: 4, points: 45.1, pointsPerGame: 11.28, espnId: null },
    { rank: 48, playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 2, points: 44.98, pointsPerGame: 22.49, espnId: null },
    { rank: 49, playerId: 19397, name: "Deebo Samuel Sr.", position: "WR", team: "SF", gamesPlayed: 4, points: 43.9, pointsPerGame: 10.98, espnId: null },
    { rank: 50, playerId: 9744, name: "Justin Herbert", position: "QB", team: "LAC", gamesPlayed: 4, points: 43.44, pointsPerGame: 10.86, espnId: null },
    { rank: 51, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 4, points: 43.2, pointsPerGame: 10.8, espnId: null },
    { rank: 52, playerId: 11751, name: "Aaron Jones", position: "RB", team: "MIN", gamesPlayed: 4, points: 42.5, pointsPerGame: 10.63, espnId: null },
    { rank: 53, playerId: 2124, name: "Denzel Boston", position: "WR", team: "CLE", gamesPlayed: 4, points: 42.4, pointsPerGame: 10.6, espnId: null },
    { rank: 54, playerId: 24343, name: "Malik Willis", position: "QB", team: "MIA", gamesPlayed: 4, points: 41.68, pointsPerGame: 10.42, espnId: null },
    { rank: 55, playerId: 5158, name: "Sam Darnold", position: "QB", team: "SEA", gamesPlayed: 3, points: 41.5, pointsPerGame: 13.83, espnId: null },
    { rank: 56, playerId: 8954, name: "Omarion Hampton", position: "RB", team: "LAC", gamesPlayed: 4, points: 40.7, pointsPerGame: 10.18, espnId: null },
    { rank: 57, playerId: 8073, name: "Matthew Golden", position: "WR", team: "GB", gamesPlayed: 4, points: 40.0, pointsPerGame: 10.0, espnId: null },
    { rank: 58, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 4, points: 39.2, pointsPerGame: 9.8, espnId: null },
    { rank: 59, playerId: 11550, name: "Juwan Johnson", position: "TE", team: "NO", gamesPlayed: 4, points: 39.2, pointsPerGame: 9.8, espnId: null },
    { rank: 60, playerId: 24399, name: "Garrett Wilson", position: "WR", team: "NYJ", gamesPlayed: 4, points: 39.0, pointsPerGame: 9.75, espnId: null },
  ],
  "Half PPR": [
    { rank: 1, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 4, points: 111.96, pointsPerGame: 27.99, espnId: null },
    { rank: 2, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 4, points: 105.0, pointsPerGame: 26.25, espnId: null },
    { rank: 3, playerId: 23056, name: "Kenneth Walker III", position: "RB", team: "KC", gamesPlayed: 4, points: 104.1, pointsPerGame: 26.03, espnId: null },
    { rank: 4, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 4, points: 100.66, pointsPerGame: 25.17, espnId: null },
    { rank: 5, playerId: 18022, name: "Brock Purdy", position: "QB", team: "SF", gamesPlayed: 4, points: 100.48, pointsPerGame: 25.12, espnId: null },
    { rank: 6, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 98.4, pointsPerGame: 24.6, espnId: null },
    { rank: 7, playerId: 12945, name: "CeeDee Lamb", position: "WR", team: "DAL", gamesPlayed: 4, points: 93.7, pointsPerGame: 23.43, espnId: null },
    { rank: 8, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 4, points: 90.62, pointsPerGame: 22.66, espnId: null },
    { rank: 9, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 4, points: 87.3, pointsPerGame: 21.83, espnId: null },
    { rank: 10, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 4, points: 86.06, pointsPerGame: 21.52, espnId: null },
    { rank: 11, playerId: 20079, name: "Tyler Shough", position: "QB", team: "NO", gamesPlayed: 4, points: 85.32, pointsPerGame: 21.33, espnId: null },
    { rank: 12, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 4, points: 83.58, pointsPerGame: 20.9, espnId: null },
    { rank: 13, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 4, points: 81.2, pointsPerGame: 20.3, espnId: null },
    { rank: 14, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 4, points: 81.2, pointsPerGame: 20.3, espnId: null },
    { rank: 15, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 4, points: 79.2, pointsPerGame: 19.8, espnId: null },
    { rank: 16, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 4, points: 79.08, pointsPerGame: 19.77, espnId: null },
    { rank: 17, playerId: 4715, name: "Kirk Cousins", position: "QB", team: "LV", gamesPlayed: 4, points: 78.84, pointsPerGame: 19.71, espnId: null },
    { rank: 18, playerId: 3167, name: "Joe Burrow", position: "QB", team: "CIN", gamesPlayed: 4, points: 76.64, pointsPerGame: 19.16, espnId: null },
    { rank: 19, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 4, points: 75.8, pointsPerGame: 18.95, espnId: null },
    { rank: 20, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 4, points: 74.3, pointsPerGame: 18.58, espnId: null },
    { rank: 21, playerId: 10441, name: "Chuba Hubbard", position: "RB", team: "CAR", gamesPlayed: 4, points: 74.0, pointsPerGame: 18.5, espnId: null },
    { rank: 22, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 4, points: 72.6, pointsPerGame: 18.15, espnId: null },
    { rank: 23, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 4, points: 68.24, pointsPerGame: 17.06, espnId: null },
    { rank: 24, playerId: 23372, name: "Christian Watson", position: "WR", team: "GB", gamesPlayed: 4, points: 67.1, pointsPerGame: 16.78, espnId: null },
    { rank: 25, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 4, points: 67.02, pointsPerGame: 16.76, espnId: null },
    { rank: 26, playerId: 23375, name: "Deshaun Watson", position: "QB", team: "CLE", gamesPlayed: 4, points: 66.9, pointsPerGame: 16.73, espnId: null },
    { rank: 27, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 4, points: 66.0, pointsPerGame: 16.5, espnId: null },
    { rank: 28, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 4, points: 65.12, pointsPerGame: 16.28, espnId: null },
    { rank: 29, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 4, points: 64.84, pointsPerGame: 16.21, espnId: null },
    { rank: 30, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 4, points: 64.4, pointsPerGame: 16.1, espnId: null },
    { rank: 31, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 4, points: 63.5, pointsPerGame: 15.88, espnId: null },
    { rank: 32, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 4, points: 62.0, pointsPerGame: 15.5, espnId: null },
    { rank: 33, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 4, points: 61.66, pointsPerGame: 15.42, espnId: null },
    { rank: 34, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 4, points: 61.5, pointsPerGame: 15.38, espnId: null },
    { rank: 35, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 4, points: 61.16, pointsPerGame: 15.29, espnId: null },
    { rank: 36, playerId: 20508, name: "Geno Smith", position: "QB", team: "NYJ", gamesPlayed: 4, points: 59.68, pointsPerGame: 14.92, espnId: null },
    { rank: 37, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 4, points: 59.02, pointsPerGame: 14.75, espnId: null },
    { rank: 38, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 4, points: 58.6, pointsPerGame: 14.65, espnId: null },
    { rank: 39, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 4, points: 58.5, pointsPerGame: 14.62, espnId: null },
    { rank: 40, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 3, points: 58.2, pointsPerGame: 19.4, espnId: null },
    { rank: 41, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 4, points: 57.26, pointsPerGame: 14.32, espnId: null },
    { rank: 42, playerId: 15567, name: "Kyle Monangai", position: "RB", team: "CHI", gamesPlayed: 4, points: 56.8, pointsPerGame: 14.2, espnId: null },
    { rank: 43, playerId: 23182, name: "Cam Ward", position: "QB", team: "TEN", gamesPlayed: 4, points: 56.74, pointsPerGame: 14.18, espnId: null },
    { rank: 44, playerId: 12621, name: "George Kittle", position: "TE", team: "SF", gamesPlayed: 4, points: 56.4, pointsPerGame: 14.1, espnId: null },
    { rank: 45, playerId: 14473, name: "Trey McBride", position: "TE", team: "ARI", gamesPlayed: 4, points: 52.7, pointsPerGame: 13.18, espnId: null },
    { rank: 46, playerId: 24399, name: "Garrett Wilson", position: "WR", team: "NYJ", gamesPlayed: 4, points: 51.0, pointsPerGame: 12.75, espnId: null },
    { rank: 47, playerId: 19397, name: "Deebo Samuel Sr.", position: "WR", team: "SF", gamesPlayed: 4, points: 50.9, pointsPerGame: 12.73, espnId: null },
    { rank: 48, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.43, espnId: null },
    { rank: 49, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.43, espnId: null },
    { rank: 50, playerId: 11550, name: "Juwan Johnson", position: "TE", team: "NO", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.42, espnId: null },
    { rank: 51, playerId: 8073, name: "Matthew Golden", position: "WR", team: "GB", gamesPlayed: 4, points: 49.0, pointsPerGame: 12.25, espnId: null },
    { rank: 52, playerId: 2124, name: "Denzel Boston", position: "WR", team: "CLE", gamesPlayed: 4, points: 48.9, pointsPerGame: 12.23, espnId: null },
    { rank: 53, playerId: 22604, name: "Bhayshul Tuten", position: "RB", team: "JAX", gamesPlayed: 4, points: 48.6, pointsPerGame: 12.15, espnId: null },
    { rank: 54, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 4, points: 47.76, pointsPerGame: 11.94, espnId: null },
    { rank: 55, playerId: 2650, name: "Chase Brown", position: "RB", team: "CIN", gamesPlayed: 4, points: 47.5, pointsPerGame: 11.88, espnId: null },
    { rank: 56, playerId: 13617, name: "Drake London", position: "WR", team: "ATL", gamesPlayed: 4, points: 47.4, pointsPerGame: 11.85, espnId: null },
    { rank: 57, playerId: 11751, name: "Aaron Jones", position: "RB", team: "MIN", gamesPlayed: 4, points: 47.0, pointsPerGame: 11.75, espnId: null },
    { rank: 58, playerId: 18805, name: "Brian Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 47.0, pointsPerGame: 11.75, espnId: null },
    { rank: 59, playerId: 12901, name: "Sam LaPorta", position: "TE", team: "DET", gamesPlayed: 4, points: 45.8, pointsPerGame: 11.45, espnId: null },
    { rank: 60, playerId: 22823, name: "Devaughn Vele", position: "WR", team: "NO", gamesPlayed: 4, points: 45.1, pointsPerGame: 11.27, espnId: null },
  ],
  PPR: [
    { rank: 1, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 4, points: 116.66, pointsPerGame: 29.17, espnId: null },
    { rank: 2, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 4, points: 116.0, pointsPerGame: 29.0, espnId: null },
    { rank: 3, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 4, points: 112.46, pointsPerGame: 28.12, espnId: null },
    { rank: 4, playerId: 12945, name: "CeeDee Lamb", position: "WR", team: "DAL", gamesPlayed: 4, points: 112.2, pointsPerGame: 28.05, espnId: null },
    { rank: 5, playerId: 23056, name: "Kenneth Walker III", position: "RB", team: "KC", gamesPlayed: 4, points: 110.1, pointsPerGame: 27.53, espnId: null },
    { rank: 6, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 105.4, pointsPerGame: 26.35, espnId: null },
    { rank: 7, playerId: 18022, name: "Brock Purdy", position: "QB", team: "SF", gamesPlayed: 4, points: 100.48, pointsPerGame: 25.12, espnId: null },
    { rank: 8, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 4, points: 91.3, pointsPerGame: 22.83, espnId: null },
    { rank: 9, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 4, points: 90.8, pointsPerGame: 22.7, espnId: null },
    { rank: 10, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 4, points: 90.62, pointsPerGame: 22.66, espnId: null },
    { rank: 11, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 4, points: 90.1, pointsPerGame: 22.53, espnId: null },
    { rank: 12, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 4, points: 89.7, pointsPerGame: 22.43, espnId: null },
    { rank: 13, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 4, points: 86.2, pointsPerGame: 21.55, espnId: null },
    { rank: 14, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 4, points: 86.06, pointsPerGame: 21.52, espnId: null },
    { rank: 15, playerId: 20079, name: "Tyler Shough", position: "QB", team: "NO", gamesPlayed: 4, points: 85.32, pointsPerGame: 21.33, espnId: null },
    { rank: 16, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 4, points: 83.58, pointsPerGame: 20.9, espnId: null },
    { rank: 17, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 4, points: 81.8, pointsPerGame: 20.45, espnId: null },
    { rank: 18, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 4, points: 81.2, pointsPerGame: 20.3, espnId: null },
    { rank: 19, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 4, points: 79.08, pointsPerGame: 19.77, espnId: null },
    { rank: 20, playerId: 10441, name: "Chuba Hubbard", position: "RB", team: "CAR", gamesPlayed: 4, points: 79.0, pointsPerGame: 19.75, espnId: null },
    { rank: 21, playerId: 4715, name: "Kirk Cousins", position: "QB", team: "LV", gamesPlayed: 4, points: 78.84, pointsPerGame: 19.71, espnId: null },
    { rank: 22, playerId: 23372, name: "Christian Watson", position: "WR", team: "GB", gamesPlayed: 4, points: 77.1, pointsPerGame: 19.28, espnId: null },
    { rank: 23, playerId: 3167, name: "Joe Burrow", position: "QB", team: "CIN", gamesPlayed: 4, points: 76.64, pointsPerGame: 19.16, espnId: null },
    { rank: 24, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 4, points: 74.5, pointsPerGame: 18.63, espnId: null },
    { rank: 25, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 4, points: 74.0, pointsPerGame: 18.5, espnId: null },
    { rank: 26, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 4, points: 73.9, pointsPerGame: 18.48, espnId: null },
    { rank: 27, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 4, points: 73.0, pointsPerGame: 18.25, espnId: null },
    { rank: 28, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 4, points: 71.1, pointsPerGame: 17.78, espnId: null },
    { rank: 29, playerId: 14473, name: "Trey McBride", position: "TE", team: "ARI", gamesPlayed: 4, points: 69.2, pointsPerGame: 17.3, espnId: null },
    { rank: 30, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 4, points: 68.24, pointsPerGame: 17.06, espnId: null },
    { rank: 31, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 3, points: 67.2, pointsPerGame: 22.4, espnId: null },
    { rank: 32, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 4, points: 67.02, pointsPerGame: 16.76, espnId: null },
    { rank: 33, playerId: 23375, name: "Deshaun Watson", position: "QB", team: "CLE", gamesPlayed: 4, points: 66.9, pointsPerGame: 16.73, espnId: null },
    { rank: 34, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 4, points: 66.5, pointsPerGame: 16.63, espnId: null },
    { rank: 35, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 4, points: 65.12, pointsPerGame: 16.28, espnId: null },
    { rank: 36, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 4, points: 64.84, pointsPerGame: 16.21, espnId: null },
    { rank: 37, playerId: 12621, name: "George Kittle", position: "TE", team: "SF", gamesPlayed: 4, points: 64.4, pointsPerGame: 16.1, espnId: null },
    { rank: 38, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 4, points: 63.5, pointsPerGame: 15.87, espnId: null },
    { rank: 39, playerId: 24399, name: "Garrett Wilson", position: "WR", team: "NYJ", gamesPlayed: 4, points: 63.0, pointsPerGame: 15.75, espnId: null },
    { rank: 40, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 4, points: 61.66, pointsPerGame: 15.42, espnId: null },
    { rank: 41, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 4, points: 61.16, pointsPerGame: 15.29, espnId: null },
    { rank: 42, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 4, points: 60.2, pointsPerGame: 15.05, espnId: null },
    { rank: 43, playerId: 11550, name: "Juwan Johnson", position: "TE", team: "NO", gamesPlayed: 4, points: 60.2, pointsPerGame: 15.05, espnId: null },
    { rank: 44, playerId: 20508, name: "Geno Smith", position: "QB", team: "NYJ", gamesPlayed: 4, points: 59.68, pointsPerGame: 14.92, espnId: null },
    { rank: 45, playerId: 15567, name: "Kyle Monangai", position: "RB", team: "CHI", gamesPlayed: 4, points: 59.3, pointsPerGame: 14.83, espnId: null },
    { rank: 46, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 4, points: 59.02, pointsPerGame: 14.75, espnId: null },
    { rank: 47, playerId: 2650, name: "Chase Brown", position: "RB", team: "CIN", gamesPlayed: 4, points: 58.0, pointsPerGame: 14.5, espnId: null },
    { rank: 48, playerId: 8073, name: "Matthew Golden", position: "WR", team: "GB", gamesPlayed: 4, points: 58.0, pointsPerGame: 14.5, espnId: null },
    { rank: 49, playerId: 19397, name: "Deebo Samuel Sr.", position: "WR", team: "SF", gamesPlayed: 4, points: 57.9, pointsPerGame: 14.48, espnId: null },
    { rank: 50, playerId: 13617, name: "Drake London", position: "WR", team: "ATL", gamesPlayed: 4, points: 57.4, pointsPerGame: 14.35, espnId: null },
    { rank: 51, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 4, points: 57.26, pointsPerGame: 14.32, espnId: null },
    { rank: 52, playerId: 12901, name: "Sam LaPorta", position: "TE", team: "DET", gamesPlayed: 4, points: 56.8, pointsPerGame: 14.2, espnId: null },
    { rank: 53, playerId: 24441, name: "Michael Wilson", position: "WR", team: "ARI", gamesPlayed: 4, points: 56.8, pointsPerGame: 14.2, espnId: null },
    { rank: 54, playerId: 23182, name: "Cam Ward", position: "QB", team: "TEN", gamesPlayed: 4, points: 56.74, pointsPerGame: 14.18, espnId: null },
    { rank: 55, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 4, points: 56.2, pointsPerGame: 14.05, espnId: null },
    { rank: 56, playerId: 22823, name: "Devaughn Vele", position: "WR", team: "NO", gamesPlayed: 4, points: 55.6, pointsPerGame: 13.9, espnId: null },
    { rank: 57, playerId: 2124, name: "Denzel Boston", position: "WR", team: "CLE", gamesPlayed: 4, points: 55.4, pointsPerGame: 13.85, espnId: null },
    { rank: 58, playerId: 5703, name: "Stefon Diggs", position: "WR", team: "WAS", gamesPlayed: 4, points: 53.0, pointsPerGame: 13.25, espnId: null },
    { rank: 59, playerId: 13471, name: "Isaiah Likely", position: "TE", team: "NYG", gamesPlayed: 4, points: 53.0, pointsPerGame: 13.25, espnId: null },
    { rank: 60, playerId: 12320, name: "Travis Kelce", position: "TE", team: "KC", gamesPlayed: 4, points: 52.6, pointsPerGame: 13.15, espnId: null },
  ],
  "My league": [
    { rank: 1, playerId: 344, name: "Josh Allen", position: "QB", team: "BUF", gamesPlayed: 4, points: 123.96, pointsPerGame: 30.99, espnId: null },
    { rank: 2, playerId: 18022, name: "Brock Purdy", position: "QB", team: "SF", gamesPlayed: 4, points: 122.48, pointsPerGame: 30.62, espnId: null },
    { rank: 3, playerId: 24907, name: "Bryce Young", position: "QB", team: "CAR", gamesPlayed: 4, points: 108.62, pointsPerGame: 27.16, espnId: null },
    { rank: 4, playerId: 7871, name: "Jahmyr Gibbs", position: "RB", team: "DET", gamesPlayed: 4, points: 105.0, pointsPerGame: 26.25, espnId: null },
    { rank: 5, playerId: 23056, name: "Kenneth Walker III", position: "RB", team: "KC", gamesPlayed: 4, points: 104.1, pointsPerGame: 26.03, espnId: null },
    { rank: 6, playerId: 8050, name: "Jared Goff", position: "QB", team: "DET", gamesPlayed: 4, points: 104.06, pointsPerGame: 26.02, espnId: null },
    { rank: 7, playerId: 20079, name: "Tyler Shough", position: "QB", team: "NO", gamesPlayed: 4, points: 103.32, pointsPerGame: 25.83, espnId: null },
    { rank: 8, playerId: 13961, name: "Patrick Mahomes", position: "QB", team: "KC", gamesPlayed: 4, points: 101.58, pointsPerGame: 25.4, espnId: null },
    { rank: 9, playerId: 4715, name: "Kirk Cousins", position: "QB", team: "LV", gamesPlayed: 4, points: 100.84, pointsPerGame: 25.21, espnId: null },
    { rank: 10, playerId: 20739, name: "Jaxon Smith-Njigba", position: "WR", team: "SEA", gamesPlayed: 4, points: 100.66, pointsPerGame: 25.17, espnId: null },
    { rank: 11, playerId: 18803, name: "Bijan Robinson", position: "RB", team: "ATL", gamesPlayed: 4, points: 98.4, pointsPerGame: 24.6, espnId: null },
    { rank: 12, playerId: 17878, name: "Dak Prescott", position: "QB", team: "DAL", gamesPlayed: 4, points: 97.2, pointsPerGame: 24.3, espnId: null },
    { rank: 13, playerId: 12945, name: "CeeDee Lamb", position: "WR", team: "DAL", gamesPlayed: 4, points: 93.7, pointsPerGame: 23.43, espnId: null },
    { rank: 14, playerId: 10942, name: "Lamar Jackson", position: "QB", team: "BAL", gamesPlayed: 4, points: 91.08, pointsPerGame: 22.77, espnId: null },
    { rank: 15, playerId: 3167, name: "Joe Burrow", position: "QB", team: "CIN", gamesPlayed: 4, points: 90.64, pointsPerGame: 22.66, espnId: null },
    { rank: 16, playerId: 9715, name: "Derrick Henry", position: "RB", team: "BAL", gamesPlayed: 4, points: 87.3, pointsPerGame: 21.83, espnId: null },
    { rank: 17, playerId: 21702, name: "Jonathan Taylor", position: "RB", team: "IND", gamesPlayed: 4, points: 81.2, pointsPerGame: 20.3, espnId: null },
    { rank: 18, playerId: 13137, name: "Trevor Lawrence", position: "QB", team: "JAX", gamesPlayed: 4, points: 81.12, pointsPerGame: 20.28, espnId: null },
    { rank: 19, playerId: 10644, name: "Jalen Hurts", position: "QB", team: "PHI", gamesPlayed: 4, points: 81.02, pointsPerGame: 20.26, espnId: null },
    { rank: 20, playerId: 13707, name: "Jordan Love", position: "QB", team: "GB", gamesPlayed: 4, points: 80.84, pointsPerGame: 20.21, espnId: null },
    { rank: 21, playerId: 24155, name: "Kyren Williams", position: "RB", team: "LA", gamesPlayed: 4, points: 79.2, pointsPerGame: 19.8, espnId: null },
    { rank: 22, playerId: 23375, name: "Deshaun Watson", position: "QB", team: "CLE", gamesPlayed: 4, points: 78.9, pointsPerGame: 19.72, espnId: null },
    { rank: 23, playerId: 21319, name: "C.J. Stroud", position: "QB", team: "HOU", gamesPlayed: 4, points: 78.24, pointsPerGame: 19.56, espnId: null },
    { rank: 24, playerId: 20925, name: "Amon-Ra St. Brown", position: "WR", team: "DET", gamesPlayed: 4, points: 75.8, pointsPerGame: 18.95, espnId: null },
    { rank: 25, playerId: 18933, name: "Aaron Rodgers", position: "QB", team: "PIT", gamesPlayed: 4, points: 75.16, pointsPerGame: 18.79, espnId: null },
    { rank: 26, playerId: 24100, name: "Javonte Williams", position: "RB", team: "DAL", gamesPlayed: 4, points: 74.3, pointsPerGame: 18.58, espnId: null },
    { rank: 27, playerId: 10441, name: "Chuba Hubbard", position: "RB", team: "CAR", gamesPlayed: 4, points: 74.0, pointsPerGame: 18.5, espnId: null },
    { rank: 28, playerId: 20944, name: "Matthew Stafford", position: "QB", team: "LA", gamesPlayed: 4, points: 73.66, pointsPerGame: 18.42, espnId: null },
    { rank: 29, playerId: 16743, name: "Chris Olave", position: "WR", team: "NO", gamesPlayed: 4, points: 72.6, pointsPerGame: 18.15, espnId: null },
    { rank: 30, playerId: 2470, name: "Jacoby Brissett", position: "QB", team: "ARI", gamesPlayed: 4, points: 71.02, pointsPerGame: 17.76, espnId: null },
    { rank: 31, playerId: 20508, name: "Geno Smith", position: "QB", team: "NYJ", gamesPlayed: 4, points: 69.68, pointsPerGame: 17.42, espnId: null },
    { rank: 32, playerId: 16418, name: "Bo Nix", position: "QB", team: "DEN", gamesPlayed: 4, points: 67.26, pointsPerGame: 16.82, espnId: null },
    { rank: 33, playerId: 23372, name: "Christian Watson", position: "WR", team: "GB", gamesPlayed: 4, points: 67.1, pointsPerGame: 16.78, espnId: null },
    { rank: 34, playerId: 14480, name: "Christian McCaffrey", position: "RB", team: "SF", gamesPlayed: 4, points: 66.0, pointsPerGame: 16.5, espnId: null },
    { rank: 35, playerId: 11156, name: "Ashton Jeanty", position: "RB", team: "LV", gamesPlayed: 4, points: 64.4, pointsPerGame: 16.1, espnId: null },
    { rank: 36, playerId: 4536, name: "James Cook", position: "RB", team: "BUF", gamesPlayed: 4, points: 63.5, pointsPerGame: 15.88, espnId: null },
    { rank: 37, playerId: 23182, name: "Cam Ward", position: "QB", team: "TEN", gamesPlayed: 4, points: 62.74, pointsPerGame: 15.68, espnId: null },
    { rank: 38, playerId: 63, name: "Davante Adams", position: "WR", team: "LA", gamesPlayed: 4, points: 62.0, pointsPerGame: 15.5, espnId: null },
    { rank: 39, playerId: 14975, name: "Tetairoa McMillan", position: "WR", team: "CAR", gamesPlayed: 4, points: 61.5, pointsPerGame: 15.38, espnId: null },
    { rank: 40, playerId: 9871, name: "Tee Higgins", position: "WR", team: "CIN", gamesPlayed: 4, points: 58.6, pointsPerGame: 14.65, espnId: null },
    { rank: 41, playerId: 21490, name: "D'Andre Swift", position: "RB", team: "CHI", gamesPlayed: 4, points: 58.5, pointsPerGame: 14.62, espnId: null },
    { rank: 42, playerId: 7100, name: "Zay Flowers", position: "WR", team: "BAL", gamesPlayed: 3, points: 58.2, pointsPerGame: 19.4, espnId: null },
    { rank: 43, playerId: 15567, name: "Kyle Monangai", position: "RB", team: "CHI", gamesPlayed: 4, points: 56.8, pointsPerGame: 14.2, espnId: null },
    { rank: 44, playerId: 12621, name: "George Kittle", position: "TE", team: "SF", gamesPlayed: 4, points: 56.4, pointsPerGame: 14.1, espnId: null },
    { rank: 45, playerId: 14406, name: "Drake Maye", position: "QB", team: "NE", gamesPlayed: 4, points: 55.76, pointsPerGame: 13.94, espnId: null },
    { rank: 46, playerId: 5158, name: "Sam Darnold", position: "QB", team: "SEA", gamesPlayed: 3, points: 53.5, pointsPerGame: 17.83, espnId: null },
    { rank: 47, playerId: 14473, name: "Trey McBride", position: "TE", team: "ARI", gamesPlayed: 4, points: 52.7, pointsPerGame: 13.18, espnId: null },
    { rank: 48, playerId: 9744, name: "Justin Herbert", position: "QB", team: "LAC", gamesPlayed: 4, points: 51.44, pointsPerGame: 12.86, espnId: null },
    { rank: 49, playerId: 24399, name: "Garrett Wilson", position: "WR", team: "NYJ", gamesPlayed: 4, points: 51.0, pointsPerGame: 12.75, espnId: null },
    { rank: 50, playerId: 19397, name: "Deebo Samuel Sr.", position: "WR", team: "SF", gamesPlayed: 4, points: 50.9, pointsPerGame: 12.73, espnId: null },
    { rank: 51, playerId: 3865, name: "Ja'Marr Chase", position: "WR", team: "CIN", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.43, espnId: null },
    { rank: 52, playerId: 23240, name: "Jaylen Warren", position: "RB", team: "PIT", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.43, espnId: null },
    { rank: 53, playerId: 11550, name: "Juwan Johnson", position: "TE", team: "NO", gamesPlayed: 4, points: 49.7, pointsPerGame: 12.42, espnId: null },
    { rank: 54, playerId: 8073, name: "Matthew Golden", position: "WR", team: "GB", gamesPlayed: 4, points: 49.0, pointsPerGame: 12.25, espnId: null },
    { rank: 55, playerId: 23961, name: "Caleb Williams", position: "QB", team: "CHI", gamesPlayed: 2, points: 48.98, pointsPerGame: 24.49, espnId: null },
    { rank: 56, playerId: 2124, name: "Denzel Boston", position: "WR", team: "CLE", gamesPlayed: 4, points: 48.9, pointsPerGame: 12.23, espnId: null },
    { rank: 57, playerId: 22604, name: "Bhayshul Tuten", position: "RB", team: "JAX", gamesPlayed: 4, points: 48.6, pointsPerGame: 12.15, espnId: null },
    { rank: 58, playerId: 2650, name: "Chase Brown", position: "RB", team: "CIN", gamesPlayed: 4, points: 47.5, pointsPerGame: 11.88, espnId: null },
    { rank: 59, playerId: 13617, name: "Drake London", position: "WR", team: "ATL", gamesPlayed: 4, points: 47.4, pointsPerGame: 11.85, espnId: null },
    { rank: 60, playerId: 11751, name: "Aaron Jones", position: "RB", team: "MIN", gamesPlayed: 4, points: 47.0, pointsPerGame: 11.75, espnId: null },
  ],
};

/**
 * The last week the capture runs through, read off the rows rather than
 * declared: the most games anyone on the board has played. Byes make it a
 * floor, not a promise, so `tests/lib.test.ts` holds every board to it.
 */
export const HERO_THROUGH_WEEK = Math.max(...HERO_BOARDS_2026.PPR.map((r) => r.gamesPlayed));

/**
 * The switch's choices, as a member with one ruleset of their own sees them --
 * the scorings captured above, and only those: TE Premium was not captured, so
 * it is not offered, because every option here works. The presets' ids are
 * their own; the member's is illustrative.
 */
export const HERO_PROFILES: (ScoringProfile & { ruleset: HeroRuleset })[] = [
  { id: 1, name: "Standard", preset: true, ruleset: "0 PPR" },
  { id: 2, name: "Half PPR", preset: true, ruleset: "Half PPR" },
  { id: 3, name: "Full PPR", preset: true, ruleset: "PPR" },
  { id: 5, name: "My league", preset: false, ruleset: "My league" },
];
