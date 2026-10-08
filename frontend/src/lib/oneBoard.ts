/**
 * The landing's one board, derived: which slot each player takes under a
 * ruleset, whether the board skips places to keep the player it follows on
 * screen, and how far a player moved. Pure and import-free, so
 * `tests/lib.test.ts` can check it without a browser.
 *
 * The rows it works on are a small set -- everyone in the top `top` under any
 * ruleset, plus the followed player -- not a whole board, so a slot is only
 * ever asked of players the set is known to hold.
 */

export interface Standing {
  rank: number;
}

export interface RankedEverywhere<R extends string> {
  playerId: number;
  by: Record<R, Standing>;
}

/**
 * His slot on the board under `ruleset`, 0 at the top, or null off it. The top
 * `top` always show, and one slot more: the followed player's when he is
 * outside them -- the board follows him, so he never drops off it -- and
 * otherwise whoever is next.
 */
export function slotOf<R extends string>(
  player: RankedEverywhere<R>,
  ruleset: R,
  followed: RankedEverywhere<R>,
  top: number,
): number | null {
  const { rank } = player.by[ruleset];
  if (rank <= top) return rank - 1;
  const pinned = followed.by[ruleset].rank > top;
  if (pinned ? player.playerId === followed.playerId : rank === top + 1) return top;
  return null;
}

/**
 * Whether the last slot skips places to reach the followed player, so a
 * dashed rule says so: under 0 PPR Nacua is 20th, and the board jumps from 8th
 * to him. Exactly the next place down is not a skip.
 */
export function skipsTo<R extends string>(followed: RankedEverywhere<R>, ruleset: R, top: number): boolean {
  return followed.by[ruleset].rank > top + 1;
}

/** The place the last slot stands for: the followed player's own when the board skips to him. */
export function lastPlace<R extends string>(followed: RankedEverywhere<R>, ruleset: R, top: number): number {
  return skipsTo(followed, ruleset, top) ? followed.by[ruleset].rank : top + 1;
}

/** Places moved going from `from` to `to`: positive is up the board. */
export function movedBetween<R extends string>(player: RankedEverywhere<R>, from: R, to: R): number {
  return player.by[from].rank - player.by[to].rank;
}
