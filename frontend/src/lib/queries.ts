import { useState } from "react";
import { keepPreviousData, useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { api } from "./api";
import { useAuth } from "./auth";
import type {
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
 * in the order the API gave it.
 */
const PRESET_ORDER = ["Standard", "Half PPR", "Full PPR", "TE Premium"];

export function useProfiles() {
  // Which profiles exist is decided by `user_id IS NULL OR user_id = ?` bound
  // to the JWT subject, so this is the one query whose ANSWER depends on being
  // signed in. On a fresh load there is no access token yet -- it is being
  // traded for from the refresh cookie -- and firing now returns the four
  // presets and none of the user's own, cached for five minutes. Wait for the
  // session to settle; every other query here is public and does not.
  const { status } = useAuth();

  return useQuery({
    queryKey: ["profiles"],
    enabled: status !== "restoring",
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
  });
}

export function usePlayer(id: number) {
  return useQuery({
    queryKey: ["player", id],
    queryFn: () => api<PlayerDetail>(`/api/v1/players/${id}`),
  });
}

export function useGamelog(id: number, profileId: number | null, season: number) {
  return useQuery({
    queryKey: ["gamelog", id, profileId, season],
    queryFn: () =>
      api<GamelogResponse>(
        `/api/v1/players/${id}/gamelog?profileId=${profileId}&season=${season}`,
      ),
    enabled: profileId !== null,
  });
}

/**
 * The selected ruleset, with a default that is derived rather than stored.
 *
 * The obvious version sets the default from an effect once the profile list
 * arrives, which React 19 correctly flags: it is a cascading render, and it
 * leaves two sources of truth for one fact — the state, and the list it was
 * derived from. Falling back at read time has neither problem, and "nothing
 * chosen yet" stays honestly represented as null.
 *
 * Half PPR is the fallback because it is what most real leagues run, so the
 * first board a visitor sees is the one most likely to be theirs.
 */
export function useSelectedProfile(initial: number | null = null) {
  const profiles = useProfiles();
  const [chosen, setChosen] = useState<number | null>(initial);

  const fallback =
    profiles.data?.find((p) => p.name === "Half PPR")?.id ?? profiles.data?.[0]?.id ?? null;

  return { profiles, profileId: chosen ?? fallback, setProfileId: setChosen };
}
