/**
 * Who moved most when the ruleset changed -- derived from two sets of ranks
 * the API returned for the same board, and nothing else. Pure, so the rules
 * below can be tested without a browser.
 *
 * Only the top of the board is considered (`window`, the tiered top 60 by
 * default): a player going from 412th to 371st moved 41 places and matters to
 * nobody, while 9th to 2nd is the story. A player counts if he is inside the
 * window under either ruleset and has a rank under both -- one missing from
 * the previous board was below the rows loaded then, not unranked, so his
 * distance is unknown and he is left out rather than guessed.
 */
export interface Mover {
  playerId: number;
  name: string;
  position: string;
  rank: number;
  previous: number;
  /** Places gained: positive is up the board. */
  delta: number;
}

export function biggestMovers(
  rows: readonly { playerId: number; name: string; position: string; rank: number }[],
  previous: ReadonlyMap<number, number>,
  { window = 60, count = 3 }: { window?: number; count?: number } = {},
): { up: Mover[]; down: Mover[] } {
  const moved: Mover[] = [];
  for (const row of rows) {
    const was = previous.get(row.playerId);
    if (was === undefined || was === row.rank) continue;
    if (row.rank > window && was > window) continue;
    moved.push({ playerId: row.playerId, name: row.name, position: row.position, rank: row.rank, previous: was, delta: was - row.rank });
  }
  // Ties broken by the better current rank, so the order is stable and the
  // more relevant player is named first.
  const byGain = [...moved].sort((a, b) => b.delta - a.delta || a.rank - b.rank);
  const byLoss = [...moved].sort((a, b) => a.delta - b.delta || a.rank - b.rank);
  return {
    up: byGain.filter((m) => m.delta > 0).slice(0, count),
    down: byLoss.filter((m) => m.delta < 0).slice(0, count),
  };
}
