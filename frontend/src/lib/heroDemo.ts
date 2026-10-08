/**
 * The landing hero's board, as data: where each row and tier divider stands
 * under a scoring, and the beats of the short loop that shows the board
 * re-sorting. Pure and import-free, so `tests/lib.test.ts` can check it without
 * a browser; the components (`components/landing/HeroDemo.tsx`, `HeroBoard.tsx`)
 * only turn it into state and CSS.
 */

/** A place on the board, in rows above it and tier dividers above it (its own included). */
export interface Placement {
  rows: number;
  dividers: number;
  /** Off the board's top `top` under this scoring: parked just below the last shown row, hidden. */
  shown: boolean;
}

export interface RankedRow {
  playerId: number;
  rank: number;
}

/** A tier as `assignTiers` cuts it: its letter and the rank it starts at. */
export interface TierStart {
  letter: string;
  first: number;
}

/**
 * Where everything on one board stands: the top `top` rows, each tier divider
 * that opens at or above them, and every other player in `cast` parked just
 * below the last shown row -- so a row leaving the board sinks out of view and
 * one arriving rises into it, rather than either jumping. `board` must run
 * from rank 1 with no gaps, as the captures do.
 */
export function boardLayout(
  board: readonly RankedRow[],
  tiers: readonly TierStart[],
  cast: readonly number[],
  top: number,
): { rows: Map<number, Placement>; dividers: Map<string, Placement> } {
  const opening = tiers.filter((t) => t.first <= top).sort((a, b) => a.first - b.first);
  const dividersAt = (rank: number) => opening.filter((t) => t.first <= rank).length;
  const rank = new Map(board.map((r) => [r.playerId, r.rank]));

  const rows = new Map<number, Placement>();
  for (const id of cast) {
    const r = rank.get(id);
    rows.set(
      id,
      r !== undefined && r <= top
        ? { rows: r - 1, dividers: dividersAt(r), shown: true }
        : { rows: top, dividers: opening.length, shown: false },
    );
  }
  const dividers = new Map<string, Placement>(
    opening.map((t, i) => [t.letter, { rows: t.first - 1, dividers: i, shown: true }]),
  );
  return { rows, dividers };
}

/** The players a board moving between scorings needs: everyone in any one board's top `top`. */
export function castOf(boards: readonly (readonly RankedRow[])[], top: number): number[] {
  const ids = boards.flat().filter((r) => r.rank <= top).map((r) => r.playerId);
  return [...new Set(ids)];
}

/**
 * The hero's loop, as data: the board under the member's own ruleset, "My
 * league" (the poster -- what the server renders, what reduced motion keeps,
 * and where every loop comes to rest), then PPR, the baseline, then My league
 * again, held long enough to read. The header and the switch never move; only
 * what the scoring changes does.
 */
export type LoopRuleset = "PPR" | "My league";

export interface Step {
  beat: "poster" | "baseline" | "league";
  /** ms into the loop. */
  at: number;
  /** The scoring the board switches to; null for the poster, which only holds. */
  picks: LoopRuleset | null;
}

export const HERO_POSTER: LoopRuleset = "My league";

export const HERO_BEATS: readonly Step[] = [
  { beat: "poster", at: 0, picks: null },
  { beat: "baseline", at: 1400, picks: "PPR" },
  { beat: "league", at: 3600, picks: "My league" },
];

/** One loop, ms: the last switch, its rows' travel, and a hold on the board it lands on. */
export const HERO_LOOP_MS = 8000;

/** Loops played before the board rests for good (until Play, or a visitor's own choice). */
export const HERO_MAX_LOOPS = 3;

/** The scoring the board shows after `step`, starting from the poster. */
export function rulesetAfter(step: number): LoopRuleset {
  let r = HERO_POSTER;
  for (const s of HERO_BEATS.slice(0, step + 1)) if (s.picks) r = s.picks;
  return r;
}
