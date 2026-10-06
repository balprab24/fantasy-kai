import type { Position } from "./types";

/**
 * The landing's hero board, derived: where each player stands under a
 * ruleset, his place among his position, how far he moved, where the one-time
 * entrance starts him -- and how much of a week's points a catch rate adds.
 * Pure, and its imports are type-only, so `tests/lib.test.ts` can check it
 * without a browser.
 *
 * The rows it works on are a small set -- everyone in the top eight under any
 * ruleset -- not a whole board. Every derivation that needs the players ranked
 * above someone checks they are all there, and says nothing rather than
 * something confident and wrong when one is missing.
 */

/** Rows the hero's board always shows, top down. */
export const HERO_TOP = 5;

/** The hero's board, in slots: the top five, then one more (`heroSlotOf`). */
export const HERO_SLOTS = HERO_TOP + 1;

export interface Standing {
  rank: number;
  points: number;
  pointsPerGame: number;
}

export interface RankedEverywhere<R extends string> {
  playerId: number;
  position: Position;
  by: Record<R, Standing>;
}

/**
 * His slot on the hero's board under `ruleset`, 0 at the top, or null off it.
 * The top `HERO_TOP` always show. The last slot holds the followed player when
 * he is outside them -- the board follows him, so he never drops off it -- and
 * otherwise whoever is next.
 */
export function heroSlotOf<R extends string>(
  player: RankedEverywhere<R>,
  ruleset: R,
  followed: RankedEverywhere<R>,
): number | null {
  const { rank } = player.by[ruleset];
  if (rank <= HERO_TOP) return rank - 1;
  const pinned = followed.by[ruleset].rank > HERO_TOP;
  if (pinned ? player.playerId === followed.playerId : rank === HERO_TOP + 1) return HERO_TOP;
  return null;
}

/** Whether the hero's last slot skips places to reach the followed player (under 0 PPR he is 7th). */
export function heroSkips<R extends string>(followed: RankedEverywhere<R>, ruleset: R): boolean {
  return followed.by[ruleset].rank > HERO_SLOTS;
}

/**
 * Place among his own position under `ruleset` ("RB1"), counted from everyone
 * ranked above him. Null when anyone ranked above him is not in `players`:
 * counting a partial set would print a confident "WR1" for a WR3.
 */
export function posRankOf<R extends string>(
  players: readonly RankedEverywhere<R>[],
  player: RankedEverywhere<R>,
  ruleset: R,
): number | null {
  const { rank } = player.by[ruleset];
  const above = players.filter((p) => p.by[ruleset].rank < rank);
  if (above.length !== rank - 1) return null;
  return 1 + above.filter((p) => p.position === player.position).length;
}

/** Places moved going from `from` to `to`: positive is up the board. */
export function movedBetween<R extends string>(player: RankedEverywhere<R>, from: R, to: R): number {
  return player.by[from].rank - player.by[to].rank;
}

/**
 * Where the entrance starts a row, given its slot on the board it opens on
 * (`start`) and on the board it lands on (`end`): `rows` is how far below
 * (positive) or above (negative) its final slot it starts, and `shown` whether
 * it is on the opening board at all. A row on neither board starts and ends
 * parked at `parked`, one slot below the last, and hidden.
 */
export function entranceOf(
  start: number | null,
  end: number | null,
  parked: number,
): { rows: number; shown: boolean } {
  return { rows: (start ?? parked) - (end ?? parked), shown: start !== null };
}

export interface WeekPoints {
  week: number;
  points: number;
}

/**
 * A season's weeks taken apart by one rate: `base` is the week as scored
 * without it (the 0 PPR week), `cap` what it adds under the chosen ruleset
 * (that week less the base). The landing's presets differ in what a catch is
 * worth and nothing else (V3), so the cap is the week's catches times the
 * rate -- read off the captured weeks, never recomputed from a formula here.
 * Throws when the two seasons disagree on which weeks were played: a missing
 * week is not a week worth nothing.
 */
export function catchCaps(
  base: readonly WeekPoints[],
  chosen: readonly WeekPoints[],
): { week: number; base: number; cap: number }[] {
  const scored = new Map(chosen.map((w) => [w.week, w.points]));
  if (scored.size !== base.length || base.some((w) => !scored.has(w.week))) {
    throw new Error("catchCaps: the two seasons do not have the same weeks");
  }
  return base.map((w) => ({ week: w.week, base: w.points, cap: scored.get(w.week)! - w.points }));
}
