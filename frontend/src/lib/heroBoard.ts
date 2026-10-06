import type { Position } from "./types";

/**
 * The landing hero's board, derived: where each player stands under a ruleset,
 * his place among his position, how far he moved, and where the one-time
 * entrance starts him. Pure, and its imports are type-only, so
 * `tests/lib.test.ts` can check it without a browser.
 *
 * The rows it works on are a small set -- everyone in the top `HERO_ROWS`
 * under any ruleset -- not a whole board. Every derivation that needs the
 * players ranked above someone checks they are all there, and says nothing
 * rather than something confident and wrong when one is missing.
 */

/** Rows the hero's board shows. */
export const HERO_ROWS = 8;

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

/** His row on the board under `ruleset`, 0 at the top, or null below the board. */
export function slotOf<R extends string>(player: RankedEverywhere<R>, ruleset: R): number | null {
  const { rank } = player.by[ruleset];
  return rank <= HERO_ROWS ? rank - 1 : null;
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
 * Where the entrance starts a row, going from the `from` board to the `to`
 * board: `rows` is how far above (negative) or below (positive) his final
 * place he starts, in rows; `shown` is whether he is on the board at the
 * start. A player on neither board starts and ends parked, one row below the
 * last, and hidden.
 */
export function entranceOf<R extends string>(
  player: RankedEverywhere<R>,
  from: R,
  to: R,
): { rows: number; shown: boolean } {
  const start = slotOf(player, from);
  const end = slotOf(player, to);
  return { rows: (start ?? HERO_ROWS) - (end ?? HERO_ROWS), shown: start !== null };
}

/**
 * The landing's bump chart: the top `HERO_ROWS` on the `final` board, in that
 * order, each with his rank at every stop -- the same players followed across
 * every ruleset, so a line is one player and its slope is the rule's effect.
 * `depth` is the lowest rank any of them reaches, which is how tall the chart
 * has to be for every line to land on its real place.
 */
export function bumpLines<R extends string>(
  players: readonly RankedEverywhere<R>[],
  stops: readonly R[],
  final: R,
): { lines: { playerId: number; ranks: number[] }[]; depth: number } {
  const lines = players
    .filter((p) => slotOf(p, final) !== null)
    .sort((a, b) => a.by[final].rank - b.by[final].rank)
    .map((p) => ({ playerId: p.playerId, ranks: stops.map((s) => p.by[s].rank) }));
  return { lines, depth: Math.max(...lines.flatMap((l) => l.ranks)) };
}
