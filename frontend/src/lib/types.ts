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
  /** Digits only, or null. The one input a headshot needs (`lib/headshot.ts`). */
  espnId: string | null;
}

/** One regular-season week of a player's recent form; `points` null is no recorded game, not zero. */
export interface RecentWeek {
  week: number;
  points: number | null;
}

/**
 * A row of `/api/v1/rankings/board` (`BoardController.Row`): the ranking row
 * plus what the member board shows beside it. Both position ranks are counted
 * over the whole population before paging, so they hold on any page.
 */
export interface BoardRankingRow extends RankingRow {
  posRank: number | null;
  /** Null when the player has under half the leader's games (not judged). */
  ppgPosRank: number | null;
  /** Points per game over the last four league weeks; null with no game in them. */
  recentPpg: number | null;
  /** `recentPpg` minus the season's per-game, when both rest on enough games. */
  form: number | null;
  /** The last four league weeks, oldest first. */
  recentWeeks: RecentWeek[];
  /** The latest regular-season week with any recorded game; 0 before week 1. */
  dataThrough: number;
  usage: Usage;
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
  espnId: string | null;
  /** ISO `YYYY-MM-DD`; null where nflverse has none. */
  birthDate: string | null;
  /** The current team's full name; null for a free agent. */
  teamName: string | null;
  /** An https URL (the API guarantees the scheme) or null. */
  teamLogo: string | null;
}

/** The 13 `StatKey` columns, keyed exactly as the API spells them. */
export type StatKey =
  | "pass_yd" | "pass_td" | "pass_int" | "pass_2pt"
  | "rush_yd" | "rush_td" | "rush_2pt"
  | "rec" | "rec_yd" | "rec_td" | "rec_2pt"
  | "fum_lost" | "ret_td";

/** Volume -- how often a player got the ball. Never scored, so never a `StatKey`. */
export interface Usage {
  passAtt: number;
  passCmp: number;
  rushAtt: number;
  targets: number;
}

/**
 * nflverse's `game_type`: `REG`, or the playoff round -- `WC`, `DIV`, `CON`,
 * `SB`. Never "POST" (measured, 2026-09-28), so test for `REG`, not for a
 * postseason value.
 */
export type SeasonType = string;

export interface GamelogWeek {
  season: number;
  week: number;
  seasonType: SeasonType;
  /** The player's team that week. */
  team: string;
  opponent: string | null;
  /** Designated home side; null when the data cannot say. */
  home: boolean | null;
  snapPct: number | null;
  usage: Usage;
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

/** One regular-season game inside a career season. */
export interface CareerWeek {
  week: number;
  opponent: string | null;
  home: boolean | null;
  points: number;
  /** Place among every player at his position that week; ties share. */
  posRank: number | null;
}

/**
 * One regular season, scored under one profile. The same three numbers the
 * board shows for this player and season -- points, per game, games -- and the
 * positional rank the board derives on a season board.
 */
export interface CareerSeason {
  season: number;
  teams: string[];
  age: number | null;
  gamesPlayed: number;
  points: number;
  pointsPerGame: number;
  posRank: number | null;
  stats: Record<StatKey, number>;
  usage: Usage;
  weeks: CareerWeek[];
}

export interface CareerResponse {
  playerId: number;
  profileId: number;
  /** Regular seasons, newest first. */
  seasons: CareerSeason[];
  /** Every season with any game, playoffs included, newest first -- a superset of `seasons`. */
  seasonsPlayed: number[];
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

/**
 * Projections (`ProjectionController`). A projection is a stored raw stat
 * line scored on request under the caller's ruleset -- never a stored point.
 *
 * - `READY` (the board) / `PROJECTED` (one player): the latest stored week.
 * - `STALE`: every game of the stored week kicked off over 48 hours ago --
 *   shown labelled, never as a forecast.
 * - `UNAVAILABLE`: nothing stored, or nothing for this player.
 * - `BYE`, `NO_HISTORY`, `NOT_ROSTERED`: one player has no line this week, and why.
 */
export type ProjectionBoardState = "READY" | "STALE" | "UNAVAILABLE";
export type ProjectionState =
  | "PROJECTED"
  | "STALE"
  | "UNAVAILABLE"
  | "BYE"
  | "NO_HISTORY"
  | "NOT_ROSTERED";

/**
 * `AvailabilityStatus` on the server. Out, reserve and off-roster are kept
 * as projections but never ranked; nothing known is `UNKNOWN`, and ranked.
 */
export type AvailabilityStatus =
  | "AVAILABLE"
  | "QUESTIONABLE"
  | "DOUBTFUL"
  | "OUT"
  | "RESERVE"
  | "NOT_ON_ROSTER"
  | "UNKNOWN";

export interface ProjectionSource {
  label: string;
  description: string;
}

/** One stat's share of a projected total: `value` x `rate` = `points`. */
export interface ProjectionPart {
  stat: string;
  value: number;
  rate: number;
  points: number;
}

export interface Projection {
  playerId: number;
  name: string;
  position: Position;
  team: string;
  opponent: string;
  home: boolean;
  /** ISO instant, or null when the schedule has no kickoff time yet. */
  kickoff: string | null;
  /** Kickoff has passed: the row is the record of what was projected. */
  locked: boolean;
  points: number;
  gamesUsed: number;
  /** `LIMITED` under four games of history, else `ESTABLISHED`. */
  history: "LIMITED" | "ESTABLISHED";
  availability: { status: AvailabilityStatus };
  espnId: string | null;
  /** ISO instant. */
  generatedAt: string;
  season: number;
  week: number;
  parts: ProjectionPart[];
  bonusPoints: number;
}

export interface ProjectionBoardResponse {
  state: ProjectionBoardState;
  source: ProjectionSource;
  page: Page<Projection>;
}

export interface PlayerProjectionResponse {
  state: ProjectionState;
  source: ProjectionSource;
  projection: Projection | null;
}
