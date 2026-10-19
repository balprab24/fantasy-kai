import type { CareerSeason, CareerWeek, GamelogWeek } from "./types";

/**
 * Small, pure facts the player view derives for display from what the API
 * returned -- which tab is open, how far into a season the data runs, which
 * games are the recent ones, how many weeks a player started. Nothing here
 * scores, adds or rounds a fantasy point: every point shown is the API's.
 */

export const PLAYER_TABS = ["overview", "log", "scoring", "career"] as const;
export type PlayerTab = (typeof PLAYER_TABS)[number];

/** `?tab=` as typed by whoever typed it: anything unknown is the overview. */
export function parseTab(raw: string | null): PlayerTab {
  return (PLAYER_TABS as readonly string[]).includes(raw ?? "") ? (raw as PlayerTab) : "overview";
}

/** The last regular-season week a player has a game in, or null for none. */
export function lastWeekPlayed(season: Pick<CareerSeason, "weeks">): number | null {
  return season.weeks.length ? Math.max(...season.weeks.map((w) => w.week)) : null;
}

/**
 * The most recent games, newest first -- every game in the log, playoffs
 * included, since this is a record of what he did. `count` at most.
 */
export function recentGames(weeks: readonly GamelogWeek[], count = 5): GamelogWeek[] {
  return [...weeks].sort((a, b) => b.week - a.week).slice(0, count);
}

/**
 * Weeks inside a 12-team league's starter line at his position that week
 * (`line`, from `STARTERS` in lib/board.ts -- passed in, so this module stays
 * import-free for `node --test`), out of the weeks he played. Null when there
 * is no line: a position v1 does not rank.
 */
export function starterShare(
  weeks: readonly Pick<CareerWeek, "posRank">[],
  line: number | undefined,
): { starts: number; games: number; line: number } | null {
  if (line === undefined) return null;
  return {
    starts: weeks.filter((w) => w.posRank !== null && w.posRank <= line).length,
    games: weeks.length,
    line,
  };
}

/**
 * Every regular-season week from 1 to the last one with a game, each either a
 * game or a gap. A gap is "no game recorded": a bye, an injury, a healthy
 * scratch -- the data does not say which, so the view never names one.
 */
export function weekSlots(season: Pick<CareerSeason, "weeks">): { week: number; game: CareerWeek | null }[] {
  const last = lastWeekPlayed(season) ?? 0;
  const byWeek = new Map(season.weeks.map((w) => [w.week, w]));
  return Array.from({ length: last }, (_, i) => ({ week: i + 1, game: byWeek.get(i + 1) ?? null }));
}
