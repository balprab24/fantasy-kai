/**
 * Mirrors the Java records in `com.fantasykai.api`. Kept hand-written rather
 * than generated: the API is small, and a generator would be a build step
 * nobody runs. If these drift, `npm run build` will not notice -- the contract
 * tests are on the backend side.
 */

export type Position = "QB" | "RB" | "WR" | "TE";
export type Scope = "season" | "per_game" | "last4";

/** `PageResponse<T>` -- note it is `total`/`page`, not Spring Data's envelope. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
}

export interface RankingRow {
  rank: number;
  playerId: number;
  name: string;
  position: Position;
  team: string | null;
  gamesPlayed: number;
  points: number;
  pointsPerGame: number;
}

export interface PlayerSummary {
  id: number;
  name: string;
  position: string;
  team: string | null;
  status: string | null;
}

export interface PlayerDetail extends PlayerSummary {
  gsisId: string | null;
}

/** The 13 `StatKey` columns, keyed exactly as the API spells them. */
export type StatKey =
  | "pass_yd" | "pass_td" | "pass_int" | "pass_2pt"
  | "rush_yd" | "rush_td" | "rush_2pt"
  | "rec" | "rec_yd" | "rec_td" | "rec_2pt"
  | "fum_lost" | "ret_td";

export interface GamelogWeek {
  season: number;
  week: number;
  seasonType: "REG" | "POST";
  opponent: string | null;
  snapPct: number | null;
  stats: Record<StatKey, number>;
  points: number;
}

export interface GamelogResponse {
  playerId: number;
  name: string;
  position: string;
  profileId: number;
  season: number;
  gamesPlayed: number;
  totalPoints: number;
  weeks: GamelogWeek[];
}

export interface ScoringProfile {
  id: number;
  name: string;
  preset: boolean;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

/** RFC 7807. The API returns `problem+json` for every error path. */
export interface Problem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
}
