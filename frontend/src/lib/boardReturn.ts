/**
 * Where you were on the board when you opened a player, so coming back puts
 * you there.
 *
 * Next 16 remounts the board on a back navigation and does not restore the
 * scroll position itself: back runs inside a transition, and the browser has
 * already restored scroll against the player page's shorter document. So the
 * board records its own place on the way out and reclaims it on the way in.
 *
 * `sessionStorage`, per tab, and every access guarded: it can be missing or
 * throw (private windows, blocked storage), and then the only cost is starting
 * at the top.
 */
const KEY = "fk:board-return";

export interface BoardReturn {
  /** The board's own URL (path + search) when the player was opened. */
  board: string;
  /** The player page it went to (path only; its search changes in place). */
  player: string;
  scrollY: number;
  /** Rows loaded at the time; fewer now means the saved offset is meaningless. */
  rows: number;
}

export function saveBoardReturn(entry: BoardReturn) {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(entry));
  } catch {
    // Storage unavailable: coming back starts at the top. Nothing else breaks.
  }
}

export function readBoardReturn(): BoardReturn | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as BoardReturn) : null;
  } catch {
    return null;
  }
}

export function clearBoardReturn() {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // As above.
  }
}
