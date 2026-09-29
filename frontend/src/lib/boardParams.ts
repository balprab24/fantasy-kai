import type { Position, Scope } from "./types";

/**
 * The rankings board's state, as it lives in the URL.
 *
 * In the URL so that a board survives a round trip to a player page and back,
 * a refresh, and being sent to someone -- and parsed defensively, because a
 * URL is typed by whoever typed it: every value is checked against the same
 * whitelist the API holds, and anything unknown falls back to its default
 * rather than reaching a query key.
 *
 * Pure, so it can be checked without a browser (`tests/`).
 */
export interface BoardState {
  season: number;
  position: Position | null;
  scope: Scope;
  /** Requested, not resolved -- `useSelectedProfile` decides whether it may be used. */
  profileId: number | null;
  q: string;
}

const POSITIONS: Position[] = ["QB", "RB", "WR", "TE"];
const SCOPES: Scope[] = ["season", "per_game", "last4"];

function int(raw: string | null): number | null {
  return raw !== null && /^[1-9][0-9]{0,9}$/.test(raw) ? Number(raw) : null;
}

export function parseBoardParams(
  params: URLSearchParams,
  seasons: { first: number; current: number },
): BoardState {
  const season = int(params.get("season"));
  const pos = params.get("pos");
  const scope = params.get("scope");
  return {
    season:
      season !== null && season >= seasons.first && season <= seasons.current
        ? season
        : seasons.current,
    position: POSITIONS.includes(pos as Position) ? (pos as Position) : null,
    scope: SCOPES.includes(scope as Scope) ? (scope as Scope) : "season",
    profileId: int(params.get("profileId")),
    q: (params.get("q") ?? "").slice(0, 60),
  };
}

/**
 * The query string for a state, defaults omitted so an untouched board keeps a
 * clean URL -- and so a board opened with no parameters writes none.
 */
export function boardSearch(state: BoardState, currentSeason: number): string {
  const out = new URLSearchParams();
  if (state.season !== currentSeason) out.set("season", String(state.season));
  if (state.position) out.set("pos", state.position);
  if (state.scope !== "season") out.set("scope", state.scope);
  if (state.profileId !== null) out.set("profileId", String(state.profileId));
  if (state.q.trim()) out.set("q", state.q);
  const text = out.toString();
  return text ? `?${text}` : "";
}
