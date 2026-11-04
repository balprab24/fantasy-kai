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

/** `LeagueDtos` in the API (league plan S1). */
export type UnmodeledGroup = "OFFENSE" | "KICKING" | "TEAM_DEFENSE" | "DEFENSIVE_PLAYERS" | "ROSTER";

export interface UnmodeledItem {
  key: string;
  label: string;
  value: number | null;
  group: UnmodeledGroup;
}

export interface DiscoveredLeague {
  externalLeagueId: string;
  name: string;
  season: number;
  numTeams: number;
  /** The member's own copy, when they already imported it. */
  importedLeagueId: number | null;
}

export interface Discovery {
  externalUserId: string;
  displayName: string;
  leagues: DiscoveredLeague[];
}

export interface LeagueSummary {
  id: number;
  platform: string;
  externalLeagueId: string;
  season: number;
  name: string;
  numTeams: number;
  myTeamName: string | null;
  syncedAt: string;
}

export interface RosterSpot {
  externalPlayerId: string;
  /** A RosterSlot name for a starter, or BENCH, IR, TAXI or UNMODELED. */
  slot: string;
  kind: "PLAYER" | "TEAM_DEFENSE" | "UNMATCHED";
  playerId: number | null;
  name: string | null;
  position: string | null;
  team: string | null;
  espnId: string | null;
}

export interface LeagueTeam {
  externalTeamId: string;
  name: string;
  managerName: string | null;
  wins: number;
  losses: number;
  ties: number;
  mine: boolean;
  roster: RosterSpot[];
}

export interface LeagueWeek {
  week: number;
  opponentExternalTeamId: string;
  opponentName: string | null;
}

export interface League {
  id: number;
  platform: string;
  externalLeagueId: string;
  season: number;
  name: string;
  numTeams: number;
  leagueType: "REDRAFT" | "KEEPER" | "DYNASTY" | "UNKNOWN";
  scoringProfileId: number | null;
  scoringNote: string | null;
  approximations: string[];
  rosterSlots: Record<string, number>;
  unmodeled: UnmodeledItem[];
  playoffTeams: number | null;
  playoffWeekStart: number | null;
  tradeDeadline: number | null;
  myTeamExternalId: string | null;
  syncedAt: string;
  teams: LeagueTeam[];
  schedule: LeagueWeek[];
  unmatched: number;
}

/** `TeamNeeds.View` in the API (league plan S2). Points rounded once, by the server. */
export interface SlotNeed {
  slot: string;
  label: string;
  /** False for K, D/ST and IDP slots: listed, never scored. */
  modeled: boolean;
  mine: number | null;
  median: number | null;
  diff: number | null;
  starterPlayerId: number | null;
  starterName: string | null;
}

export interface PlayerOutlook {
  externalPlayerId: string;
  playerId: number | null;
  status: "PROJECTED" | "NO_PROJECTION" | "NOT_MODELED";
  availability: string;
  /** Mean over the remaining weeks, byes and absences counted as 0. */
  perWeek: number | null;
  perGame: number | null;
  weeks: { week: number; points: number }[];
  parts: { stat: string; value: number; rate: number; points: number }[];
  bonusPoints: number | null;
}

export interface TeamNeedsView {
  season: number;
  weeks: number[];
  myTeamExternalId: string | null;
  slots: SlotNeed[];
  players: PlayerOutlook[];
  noProjection: number;
}
