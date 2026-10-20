import type { CareerSeason, CareerWeek, GamelogWeek } from "./types";

/**
 * Small, pure facts the player view derives for display from what the API
 * returned -- which tab is open, how far into a season the data runs, which
 * games are the recent ones, how many weeks a player started. Nothing here
 * scores, adds or rounds a fantasy point: every point shown is the API's.
 */

/**
 * The player view's sections, in page order. They are one scrolling column,
 * not panes: `?tab=` names the section the view opens scrolled to, and
 * "overview" is the top of it.
 */
export const PLAYER_TABS = ["overview", "log", "performance", "career", "scoring"] as const;
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

/** One game on the performance chart: his season and week, and the API's figures for it. */
export interface ChartGame {
  season: number;
  week: number;
  game: CareerWeek | null;
}

/**
 * His last `count` regular-season games across every season the career holds,
 * oldest first -- what "Last 17" draws. Games only: a week with no game is not
 * a game, so it takes no slot here (a single season's chart shows those gaps).
 * `seasons` is the career's own list, newest first.
 */
export function lastGames(seasons: readonly Pick<CareerSeason, "season" | "weeks">[], count = 17): ChartGame[] {
  const all = [...seasons]
    .sort((a, b) => a.season - b.season)
    .flatMap((s) => [...s.weeks].sort((a, b) => a.week - b.week).map((game) => ({ season: s.season, week: game.week, game })));
  return all.slice(Math.max(0, all.length - count));
}

/**
 * How strongly to shade a box-score cell: 0 for nothing, then 1 to `steps` by
 * its share of the biggest value in its column (`max`) -- his own range, not
 * the league's, which the API does not send. A negative or empty column is 0.
 */
export function heatStep(value: number, max: number, steps = 4): number {
  if (!(value > 0) || !(max > 0)) return 0;
  return Math.max(1, Math.min(steps, Math.ceil((value / max) * steps)));
}
