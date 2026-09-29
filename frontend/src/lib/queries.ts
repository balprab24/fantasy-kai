import { keepPreviousData, useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { api } from "./api";
import { useAuth } from "./auth";
import type {
  CareerResponse,
  GamelogResponse,
  Page,
  PlayerDetail,
  Position,
  RankingRow,
  ScoringProfile,
  Scope,
} from "./types";

/**
 * Presets in increasing order of what a reception is worth, because that is
 * the axis they actually differ on and seeing them in that order is half the
 * explanation. The API returns them by name, which sorts Full PPR above
 * Standard and makes the set look arbitrary. Anything the user made follows,
 * in the order the API gave it. These are the stored names; the screen shows
 * `profileLabel` -- 0 PPR, Half PPR, PPR.
 */
const PRESET_ORDER = ["Standard", "Half PPR", "Full PPR", "TE Premium"];

export function useProfiles() {
  // Which profiles exist is decided by `user_id IS NULL OR user_id = ?` bound
  // to the JWT subject, so this is the one query whose ANSWER depends on being
  // signed in. On a fresh load there is no access token yet -- it is being
  // traded for from the refresh cookie -- and firing now returns the four
  // presets and none of the user's own, cached for five minutes. Wait for a
  // session. Since 2026-09-29 every read needs one, so a signed-out caller would
  // only cache a 401; the other queries live under RequireAccount, which does not
  // render them until the session exists.
  const { status } = useAuth();

  return useQuery({
    queryKey: ["profiles"],
    enabled: status === "signed-in",
    queryFn: async () => {
      const profiles = await api<ScoringProfile[]>("/api/v1/scoring-profiles");
      return [...profiles].sort((a, b) => {
        if (a.preset !== b.preset) return a.preset ? -1 : 1;
        if (!a.preset) return 0;
        return PRESET_ORDER.indexOf(a.name) - PRESET_ORDER.indexOf(b.name);
      });
    },
  });
}

/**
 * The rankings workspace's board: page after page from rank 1, never a page
 * on its own.
 *
 * Two reasons, both measured facts rather than preferences. Every request
 * scores the whole season whatever `size` is, so 200 -- the API's cap -- is the
 * cheapest way to move a board: three requests for ~600 players instead of
 * twelve. And a board that always starts at rank 1 is what lets positional
 * ranks and tiers be derived here at all (`lib/board.ts`).
 */
export const BOARD_PAGE_SIZE = 200;

export interface BoardQuery {
  profileId: number | null;
  season: number;
  position: Position | null;
  scope: Scope;
}

export function useRankingsBoard(q: BoardQuery) {
  return useInfiniteQuery({
    queryKey: ["rankings-board", q.profileId, q.season, q.position, q.scope],
    enabled: q.profileId !== null,
    initialPageParam: 0,
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams({
        profileId: String(q.profileId ?? ""),
        season: String(q.season),
        scope: q.scope,
        page: String(pageParam),
        size: String(BOARD_PAGE_SIZE),
      });
      if (q.position) params.set("position", q.position);
      return api<Page<RankingRow>>(`/api/v1/rankings?${params}`);
    },
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
    // The previous board stays on screen while the next ruleset is scored, so
    // a switch reads as movement rather than as a reload.
    placeholderData: keepPreviousData,
    // Kept well past the default five minutes: the board unmounts while a
    // player page is open, and coming back to a board that has to be scored
    // again -- with the pages you had loaded gone -- loses your place in it.
    gcTime: 30 * 60_000,
  });
}

export function usePlayer(id: number) {
  return useQuery({
    queryKey: ["player", id],
    queryFn: () => api<PlayerDetail>(`/api/v1/players/${id}`),
    // A malformed id in the URL is 0 here (app/players/[id]); asking the API
    // about it would only be a 400 dressed up as a request.
    enabled: id > 0,
  });
}

export function useGamelog(id: number, profileId: number | null, season: number | null) {
  return useQuery({
    queryKey: ["gamelog", id, profileId, season],
    queryFn: () =>
      api<GamelogResponse>(
        `/api/v1/players/${id}/gamelog?profileId=${profileId}&season=${season}`,
      ),
    enabled: id > 0 && profileId !== null && season !== null,
    placeholderData: keepPreviousData,
  });
}

/**
 * Every regular season, scored under one profile, with positional ranks. One
 * request per (player, profile) feeds the identity tiles, the chart and the
 * career table; switching season never refetches it.
 */
export function useCareer(id: number, profileId: number | null) {
  return useQuery({
    queryKey: ["career", id, profileId],
    queryFn: () => api<CareerResponse>(`/api/v1/players/${id}/career?profileId=${profileId}`),
    enabled: id > 0 && profileId !== null,
    placeholderData: keepPreviousData,
  });
}

/**
 * The ruleset a page scores against, resolved from the one it asked for.
 *
 * Derived, never stored: the choice lives in the URL, and the default falls
 * out at read time rather than from an effect -- which React 19 correctly
 * flags as a cascading render that leaves two sources of truth for one fact.
 *
 * A requested id counts only once the profile list is back and contains it.
 * That list waits for the session to be restored (`useProfiles`), so this is
 * what stops a URL carrying a private ruleset's id from firing rankings, game
 * log and career requests before the access token exists. Those would come
 * back 404 -- an ownership miss is a 404, which the refresh-and-retry never
 * sees -- and be cached as the answer. Until the list arrives the result is
 * null and every query keyed on it waits.
 *
 * Half PPR is the fallback because it is what most real leagues run, so the
 * first board a visitor sees is the one most likely to be theirs.
 */
export function useSelectedProfile(requested: number | null) {
  const profiles = useProfiles();
  const list = profiles.data;

  let profileId: number | null = null;
  if (list) {
    profileId = list.some((p) => p.id === requested)
      ? requested
      : (list.find((p) => p.preset && p.name === "Half PPR")?.id ?? list[0]?.id ?? null);
  }
  return { profiles, profileId };
}
