"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { RankingsBoard } from "@/components/RankingsBoard";
import { Icon } from "@/components/ui/Icon";
import { BOARD_PAGE_SIZE, useRankingsBoard, useSelectedProfile } from "@/lib/queries";
import { currentSeason } from "@/lib/season";
import type { Position, Scope } from "@/lib/types";
import { FilterBar, SCOPES } from "./FilterBar";

/**
 * The rankings workspace -- what `/` and `/rankings` both show.
 *
 * One component for both routes on purpose: the home page used to carry its
 * own hero and its own 100-row board, pinned to a season literal, and the two
 * boards drifted apart. The page is a tool, so it opens on the tool: a
 * two-line header, one row of controls, then rows.
 */
export function RankingsWorkspace() {
  const { profiles, profileId, setProfileId } = useSelectedProfile();
  const [filters, setFilters] = useState<{
    season: number;
    position: Position | null;
    scope: Scope;
  }>(() => ({ season: currentSeason(), position: null, scope: "season" }));
  const [find, setFind] = useState("");
  const { season, position, scope } = filters;
  const setSeason = (s: number) => setFilters((f) => ({ ...f, season: s }));

  const board = useRankingsBoard({ profileId, season, position, scope });
  // Memoized: a fresh array on every render would re-run the tiering on every
  // keystroke in the search box.
  const pages = board.data?.pages;
  const rows = useMemo(() => pages?.flatMap((p) => p.content), [pages]);
  const total = board.data?.pages[0]?.total;
  const profileName = profiles.data?.find((p) => p.id === profileId)?.name;
  const scopeLabel = SCOPES.find((s) => s.value === scope)?.title;

  return (
    <div>
      <div className="mx-auto max-w-[1320px] px-4 pt-5 pb-10 sm:px-6 lg:px-8">
        <header className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-0.5">
            <h1 className="font-display text-[22px] leading-7 font-bold tracking-[-0.01em]">
              Rankings
            </h1>
            <p className="tabular text-sm text-mute">
              {season} season
              {profileName && <> · {profileName}</>} · {scopeLabel}
              {total !== undefined && total > 0 && <> · {total} players</>}
            </p>
          </div>
          <Link
            href="/profiles"
            className="inline-flex items-center gap-1.5 text-sm text-energy-text hover:text-ink"
          >
            Edit scoring
            <Icon name="arrowRight" size={16} />
          </Link>
        </header>

        <div className="mt-3">
          <FilterBar
            profiles={profiles.data}
            profileId={profileId}
            onProfile={setProfileId}
            season={season}
            onSeason={setSeason}
            scope={scope}
            onScope={(s) => setFilters((f) => ({ ...f, scope: s }))}
            position={position}
            onPosition={(p) => setFilters((f) => ({ ...f, position: p }))}
            find={find}
            onFind={setFind}
          />
        </div>

        <div className="mt-3">
          <RankingsBoard
            rows={rows}
            comparisonKey={String(profileId)}
            boardKey={`${season}|${position}|${scope}`}
            scope={scope}
            stale={board.isPlaceholderData}
            season={season}
            profileId={profileId}
            loading={board.isLoading}
            fetching={board.isFetching && !board.isFetchingNextPage && !board.isLoading}
            error={board.error ?? profiles.error}
            onRetry={() => void (profiles.error ? profiles.refetch() : board.refetch())}
            onSeason={setSeason}
            find={find}
          />
        </div>

        {rows && total !== undefined && rows.length > 0 && (
          <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
            <span className="tabular text-mute" aria-live="polite">
              {board.hasNextPage
                ? `Showing 1–${rows.length} of ${total}`
                : `All ${total} players shown`}
            </span>
            {board.hasNextPage && (
              <button
                type="button"
                onClick={() => void board.fetchNextPage()}
                disabled={board.isFetchingNextPage}
                className="h-9 rounded-md border border-line-strong px-4 text-ink hover:bg-surface-2 disabled:opacity-60"
              >
                {board.isFetchingNextPage
                  ? "Scoring…"
                  : `Show next ${Math.min(BOARD_PAGE_SIZE, total - rows.length)}`}
              </button>
            )}
          </div>
        )}

        <p className="mt-8 text-xs text-faint">
          No point on this board is stored — every one is computed on request against the ruleset
          you picked.
        </p>
      </div>
    </div>
  );
}
