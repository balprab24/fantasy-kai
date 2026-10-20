/**
 * The board's order, as it stood when a player was opened from it, so the
 * player view can step to the previous and next player without leaving --
 * "the next one down the board" means the board the member was looking at,
 * filters, search and ruleset included, not a fresh query.
 *
 * Kept per tab in `sessionStorage`, every access guarded (it can be missing or
 * throw in private windows); without it the player view simply has no
 * previous/next, which is also what a shared link opened cold gets.
 */
const KEY = "fk:board-sequence";

export interface SequenceEntry {
  playerId: number;
  name: string;
  rank: number;
}

export interface BoardSequence {
  /** The query string every step keeps (season, profileId), so the view stays on the board's rules. */
  search: string;
  players: SequenceEntry[];
}

/** At most this many either side of the opened player: enough to step through, small enough to store. */
const SPAN = 100;

export function saveBoardSequence(players: SequenceEntry[], openedId: number, search: string) {
  const at = players.findIndex((p) => p.playerId === openedId);
  const from = Math.max(0, at - SPAN);
  const slice = at < 0 ? players.slice(0, 2 * SPAN) : players.slice(from, at + SPAN + 1);
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ search, players: slice } satisfies BoardSequence));
  } catch {
    // Storage unavailable: no previous/next. Nothing else breaks.
  }
}

export function readBoardSequence(): BoardSequence | null {
  return parseBoardSequence(boardSequenceSnapshot());
}

/**
 * The stored text itself, for `useSyncExternalStore`: a string compares by
 * value, so React sees the same snapshot until the sequence really changes.
 */
export function boardSequenceSnapshot(): string | null {
  try {
    return sessionStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function parseBoardSequence(raw: string | null): BoardSequence | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as BoardSequence;
    return Array.isArray(parsed?.players) ? parsed : null;
  } catch {
    return null;
  }
}

/** The players either side of `playerId` in a sequence, or nulls where there are none. */
export function neighbours(
  sequence: BoardSequence | null,
  playerId: number,
): { previous: SequenceEntry | null; next: SequenceEntry | null } {
  const list = sequence?.players ?? [];
  const at = list.findIndex((p) => p.playerId === playerId);
  if (at < 0) return { previous: null, next: null };
  return { previous: list[at - 1] ?? null, next: list[at + 1] ?? null };
}
