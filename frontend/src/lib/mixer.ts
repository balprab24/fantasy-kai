/**
 * The landing hero's rule mixer, as data: which rows stand where under a pair
 * of dials, and how a ruleset is said in words. Pure and import-free, so
 * `tests/lib.test.ts` can check it; `components/landing/RuleMixer.tsx` turns it
 * into state and CSS.
 */

export const CATCH_RATES = [0, 0.5, 1] as const;
export const PASS_TDS = [4, 6] as const;
export type Catch = (typeof CATCH_RATES)[number];
export type PassTouchdown = (typeof PASS_TDS)[number];

export interface Ranked {
  playerId: number;
  rank: number;
}

/** The two dials as the key the captures are stored under. */
export function mixKey(rec: Catch, passTd: PassTouchdown): `${Catch}|${PassTouchdown}` {
  return `${rec}|${passTd}`;
}

/** Everyone in any board's top `top`: the rows the hero needs to animate between boards. */
export function castOf(boards: readonly (readonly Ranked[])[], top: number): number[] {
  return [...new Set(boards.flat().filter((r) => r.rank <= top).map((r) => r.playerId))];
}

/**
 * Where a player stands on a board, as a slot 0..top-1 -- or `top`, parked just
 * under the last shown row, when he is outside it. A row leaving the board
 * sinks out of view and one arriving rises into it, rather than either
 * jumping. The board must run from rank 1 with no gaps, as the captures do.
 */
export function slotOn(board: readonly Ranked[], playerId: number, top: number): number {
  const rank = board.find((r) => r.playerId === playerId)?.rank;
  return rank !== undefined && rank <= top ? rank - 1 : top;
}

/** What a ruleset is called, in words: the preset it is, and the one rate changed from it. */
export function describeMix(rec: Catch, passTd: PassTouchdown): string {
  const preset = rec === 0 ? "0 PPR" : rec === 0.5 ? "Half PPR" : "PPR";
  return passTd === 4 ? preset : `${preset} with 6-point passing touchdowns`;
}

/** "½" for a half, so the dial reads as the setting reads in a league. */
export function formatRate(n: number): string {
  return n === 0.5 ? "½" : String(n);
}
