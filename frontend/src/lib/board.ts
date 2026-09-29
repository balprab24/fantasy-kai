import type { Position, RankingRow } from "./types";

/**
 * Everything the board derives from the rows the API sent, and nothing it
 * makes up. Pure functions, no React, so they can be checked on their own.
 *
 * Both derivations depend on one property of the input: it starts at rank 1
 * and has no gaps. The API's `rank` is `i + 1` over the whole sorted season,
 * so a board loaded page after page from the top has it. A lone page 3 does
 * not -- it cannot know that its first wide receiver is WR27 -- and in that
 * case these return nothing rather than a confident wrong answer.
 */

/**
 * The number a board is sorted by. The API always returns both, but ranks a
 * per-game board by `pointsPerGame` -- so a gauge or a tier computed from
 * `points` there describes a different ranking than the one on screen.
 */
export type Metric = "points" | "pointsPerGame";

export interface BoardRow extends RankingRow {
  /** Place among players at the same position, or null if it cannot be known. */
  posRank: number | null;
  /**
   * Place by points per game among qualified players at the same position
   * (see `QUALIFYING_SHARE`), or null when unqualified or unknowable.
   */
  ppgPosRank: number | null;
  /** Where `ppgPosRank` falls against the starter line; null means say nothing. */
  quality: Quality | null;
  /** The value the board is sorted by -- `points` or `pointsPerGame`. */
  value: number;
  /** `value` as a fraction of the board leader's, 0..1. Drives the bar. */
  share: number;
}

/**
 * One decimal, with a real minus sign. A hyphen reads as a dash in a range --
 * "4.8–-1.6" -- and fantasy points do go negative.
 */
export function formatPoints(n: number) {
  const fixed = Math.abs(n).toFixed(1);
  return n < 0 && fixed !== "0.0" ? `\u2212${fixed}` : fixed;
}

export function isContiguous(rows: RankingRow[]) {
  return rows.every((row, i) => row.rank === i + 1);
}

export function toBoardRows(rows: RankingRow[], metric: Metric = "points"): BoardRow[] {
  const known = isContiguous(rows);
  const leader = rows[0]?.[metric] ?? 0;
  const seen = new Map<Position, number>();
  const ppgRanks = known ? rankPerGame(rows) : new Map<number, number>();

  return rows.map((row) => {
    const n = (seen.get(row.position) ?? 0) + 1;
    seen.set(row.position, n);
    const ppgPosRank = ppgRanks.get(row.playerId) ?? null;
    return {
      ...row,
      posRank: known ? n : null,
      ppgPosRank,
      quality: ppgPosRank === null ? null : band(ppgPosRank, row.position),
      value: row[metric],
      share: leader > 0 ? Math.max(0, Math.min(1, row[metric] / leader)) : 0,
    };
  });
}

/**
 * How good a points-per-game figure is, judged against the player's own
 * position -- 15 a game is a top tight end and a backup quarterback, so one
 * scale across positions would colour every quarterback green.
 *
 * The line is the number of starters at that position in a 12-team league
 * (1 QB, 2 RB, 3 WR, 1 TE): inside it is `good`, up to twice it -- the bench
 * and flex range -- is `mid`, beyond that `poor`. An assumption, named here
 * once, until league import supplies a league's real roster slots.
 */
export type Quality = "good" | "mid" | "poor";

export const STARTERS: Record<Position, number> = { QB: 12, RB: 24, WR: 36, TE: 12 };

export function band(rank: number, position: Position): Quality {
  const line = STARTERS[position];
  return rank <= line ? "good" : rank <= 2 * line ? "mid" : "poor";
}

/** `band` for a position that may be one v1 does not rank (K, OL...): null there. */
export function bandFor(rank: number | null, position: string): Quality | null {
  // hasOwn, not `in`: "constructor" is `in` every object.
  return rank !== null && Object.hasOwn(STARTERS, position) ? band(rank, position as Position) : null;
}

/**
 * A player must have played at least this share of the most games anyone on
 * the board has, or their per-game figure is not judged: one 25-point game is
 * an anecdote, not an elite rate.
 */
export const QUALIFYING_SHARE = 0.5;

/**
 * Positional rank by points per game over the rows loaded, qualified players
 * only. Ties share a rank, so equal figures always get the same colour.
 *
 * Only as complete as the board: a qualified player below the loaded rows is
 * not counted, which can only make a rank better than it should be. On a
 * season board that touches the far end of `mid` at most -- the starter lines
 * sum to 84, and the first page is 200 rows.
 */
function rankPerGame(rows: RankingRow[]): Map<number, number> {
  const most = rows.reduce((m, r) => Math.max(m, r.gamesPlayed), 0);
  const floor = Math.max(1, Math.ceil(most * QUALIFYING_SHARE));
  const byPosition = new Map<Position, RankingRow[]>();
  for (const row of rows) {
    if (row.gamesPlayed < floor) continue;
    const list = byPosition.get(row.position) ?? [];
    list.push(row);
    byPosition.set(row.position, list);
  }

  const ranks = new Map<number, number>();
  for (const list of byPosition.values()) {
    list.sort((a, b) => b.pointsPerGame - a.pointsPerGame);
    list.forEach((row, i) => {
      const tied = i > 0 && row.pointsPerGame === list[i - 1].pointsPerGame;
      ranks.set(row.playerId, tied ? ranks.get(list[i - 1].playerId)! : i + 1);
    });
  }
  return ranks;
}

export interface Tier {
  letter: TierLetter;
  rows: BoardRow[];
  high: number;
  low: number;
}

export interface Tiered {
  tiers: Tier[];
  /** Everything below the tiered window, in rank order and ungrouped. */
  rest: BoardRow[];
}

export const TIER_LETTERS = ["S", "A", "B", "C", "D"] as const;
export type TierLetter = (typeof TIER_LETTERS)[number];

/**
 * Only the top of a board is tiered. Measured on the real 2025 boards: over
 * the top 60, natural breaks give tiers that grow down the list (Half PPR
 * season, overall 8/11/9/15/17, RB 6/9/12/16/17, TE 1/12/15/13/19). Over 200
 * they turn into 19-to-68-player buckets that say nothing. The first page is
 * 200 rows and always arrives first, so tiers never change as more loads.
 *
 * Two simpler rules were tried first, on the same boards, and failed. A break
 * wherever the drop to the next player is >= X% of the leader (X from 1.5% to
 * 5%) made S a single player on nearly every board and used up all six letters
 * by rank 6-30. A break wherever a drop is k times the tier's own average drop
 * gave either one tier or 20-70. Both look only at neighbours, and a board is
 * sparse at the top and dense at the bottom, so a local rule spends every
 * break in the first few ranks. Natural breaks look at the whole window.
 */
export const TIER_WINDOW = 60;

/**
 * Splits the top of a ranked list into up to five tiers by natural breaks.
 *
 * Fisher-Jenks: the partition into contiguous groups that minimises the spread
 * of points inside each group -- the breaks land where the board genuinely
 * separates, not at fixed ranks. Exact, by dynamic programming, O(k n^2);
 * at n = 60 that is ~18,000 steps. A break is never placed between two players
 * on identical points: one tier boundary through a tie would be a claim the
 * data does not make.
 */
export function assignTiers(rows: BoardRow[], window = TIER_WINDOW): Tiered {
  if (!isContiguous(rows)) return { tiers: [], rest: rows };
  const top = rows.slice(0, window);
  const rest = rows.slice(window);
  if (top.length === 0) return { tiers: [], rest };

  const k = Math.max(1, Math.min(TIER_LETTERS.length, Math.floor(top.length / 2)));
  const starts = naturalBreaks(
    top.map((r) => r.value),
    k,
  );

  const tiers = [0, ...starts].map((from, i, all) => {
    const slice = top.slice(from, all[i + 1] ?? top.length);
    return {
      letter: TIER_LETTERS[i],
      rows: slice,
      high: slice[0].value,
      low: slice[slice.length - 1].value,
    };
  });
  return { tiers, rest };
}

/** Start index of every group after the first. `x` is sorted, descending. */
function naturalBreaks(x: number[], k: number): number[] {
  const n = x.length;
  const sum = [0];
  const sumSq = [0];
  for (const v of x) {
    sum.push(sum[sum.length - 1] + v);
    sumSq.push(sumSq[sumSq.length - 1] + v * v);
  }
  // Sum of squared deviations of x[i..j] from their own mean.
  const spread = (i: number, j: number) => {
    const s = sum[j + 1] - sum[i];
    return sumSq[j + 1] - sumSq[i] - (s * s) / (j - i + 1);
  };

  // cost[c][j]: best total spread of x[0..j] in c + 1 groups; from[c][j]: where
  // the last of those groups starts.
  const cost = Array.from({ length: k }, () => new Array<number>(n).fill(Infinity));
  const from = Array.from({ length: k }, () => new Array<number>(n).fill(0));
  for (let j = 0; j < n; j++) cost[0][j] = spread(0, j);
  for (let c = 1; c < k; c++) {
    for (let j = c; j < n; j++) {
      for (let i = c; i <= j; i++) {
        if (x[i] === x[i - 1]) continue; // never split a tie
        const total = cost[c - 1][i - 1] + spread(i, j);
        if (total < cost[c][j]) {
          cost[c][j] = total;
          from[c][j] = i;
        }
      }
    }
  }

  // Use as many groups as the data allows: a board of ties may not split k ways.
  let groups = k;
  while (groups > 1 && cost[groups - 1][n - 1] === Infinity) groups--;

  const starts: number[] = [];
  let j = n - 1;
  for (let c = groups - 1; c > 0; c--) {
    const i = from[c][j];
    starts.unshift(i);
    j = i - 1;
  }
  return starts;
}
