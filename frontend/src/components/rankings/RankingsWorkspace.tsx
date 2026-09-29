"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { RankingsBoard } from "@/components/RankingsBoard";
import { Icon } from "@/components/ui/Icon";
import { Skeleton } from "@/components/ui/StatusMessage";
import { boardSearch, parseBoardParams, type BoardState } from "@/lib/boardParams";
import { clearBoardReturn, readBoardReturn, saveBoardReturn } from "@/lib/boardReturn";
import { profileLabel } from "@/lib/profiles";
import { BOARD_PAGE_SIZE, useRankingsBoard, useSelectedProfile } from "@/lib/queries";
import { FIRST_SEASON, currentSeason } from "@/lib/season";
import { FilterBar, SCOPES } from "./FilterBar";

/** How long typing in the search box waits before the URL catches up. */
const FIND_DEBOUNCE_MS = 300;

/**
 * The rankings workspace -- what `/` and `/rankings` both show.
 *
 * One component for both routes on purpose: the home page used to carry its
 * own hero and its own 100-row board, pinned to a season literal, and the two
 * boards drifted apart. The page is a tool, so it opens on the tool: a
 * two-line header, one row of controls, then rows.
 *
 * Every filter lives in the URL and is read back from it on each render --
 * never copied into state, which would be a second source of truth. That is
 * what lets a board survive a trip to a player page and back, a refresh, or
 * being sent to someone. Writes use `history.replaceState`, which Next keeps
 * in step with `useSearchParams`: a filter change is not a history entry, so
 * back from a player page lands on the board you left rather than walking
 * through every filter you tried on the way.
 */
export function RankingsWorkspace() {
  const params = useSearchParams();
  const pathname = usePathname();
  const now = currentSeason();
  const state = useMemo(
    () =>
      parseBoardParams(new URLSearchParams(params.toString()), {
        first: FIRST_SEASON,
        current: now,
      }),
    [params, now],
  );
  const { season, position, scope } = state;
  const { profiles, profileId } = useSelectedProfile(state.profileId);

  // Merged into what the URL says *now*, not what this render saw: a debounced
  // search can land after a filter click whose re-render has not committed
  // yet, and a stale copy of the state would quietly undo that click.
  const write = useCallback(
    (change: Partial<BoardState>) => {
      const current = parseBoardParams(new URLSearchParams(window.location.search), {
        first: FIRST_SEASON,
        current: now,
      });
      window.history.replaceState(null, "", pathname + boardSearch({ ...current, ...change }, now));
    },
    [pathname, now],
  );

  // The box answers every keystroke; the URL follows a moment later. Safari
  // throttles replaceState at 100 calls in 10 seconds, and each write
  // re-renders the router.
  const [find, setFind] = useState(state.q);
  const pendingFind = useRef<ReturnType<typeof setTimeout> | null>(null);

  // The box follows the URL when the URL's search changes to something this
  // board did not write -- the sidebar's Rankings link, back and forward --
  // and ignores the echo of its own debounced writes, which it is already
  // ahead of. Adjusted during render, the way React documents for state
  // derived from a changing input, rather than in an effect.
  const [written, setWritten] = useState(state.q);
  const [seenQ, setSeenQ] = useState(state.q);
  if (state.q !== seenQ) {
    setSeenQ(state.q);
    if (state.q !== written) setFind(state.q);
  }

  const onFind = (value: string) => {
    setFind(value);
    if (pendingFind.current) clearTimeout(pendingFind.current);
    pendingFind.current = setTimeout(() => {
      pendingFind.current = null;
      setWritten(value);
      write({ q: value });
    }, FIND_DEBOUNCE_MS);
  };
  useEffect(() => () => {
    if (pendingFind.current) clearTimeout(pendingFind.current);
  }, []);

  const board = useRankingsBoard({ profileId, season, position, scope });
  // Memoized: a fresh array on every render would re-run the tiering on every
  // keystroke in the search box.
  const pages = board.data?.pages;
  const rows = useMemo(() => pages?.flatMap((p) => p.content), [pages]);
  const rowsLoaded = rows?.length ?? 0;
  const total = board.data?.pages[0]?.total;
  const profile = profiles.data?.find((p) => p.id === profileId);
  const scopeLabel = SCOPES.find((s) => s.value === scope)?.title;

  // Leaving for a player: make the URL current (a search still waiting on its
  // debounce would otherwise be lost) and note where on the board we were.
  const onOpenPlayer = (href: string) => {
    if (pendingFind.current) {
      clearTimeout(pendingFind.current);
      pendingFind.current = null;
      setWritten(find);
      write({ q: find });
    }
    saveBoardReturn({
      board: window.location.pathname + window.location.search,
      player: href.split("?")[0],
      scrollY: window.scrollY,
      rows: rowsLoaded,
    });
  };

  // Coming back: once the rows are on screen, go back to where we were --
  // but only to this exact board, and only if as many rows are loaded as
  // there were (the cache can have been evicted), or the offset means nothing.
  useLayoutEffect(() => {
    if (!rowsLoaded) return;
    const saved = readBoardReturn();
    if (!saved) return;
    if (saved.board === window.location.pathname + window.location.search) {
      if (rowsLoaded >= saved.rows) window.scrollTo(0, saved.scrollY);
      clearBoardReturn();
    }
  }, [rowsLoaded]);

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
              {profile && <> · {profileLabel(profile)}</>} · {scopeLabel}
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
            onProfile={(id) => write({ profileId: id })}
            season={season}
            onSeason={(s) => write({ season: s })}
            scope={scope}
            onScope={(s) => write({ scope: s })}
            position={position}
            onPosition={(p) => write({ position: p })}
            find={find}
            onFind={onFind}
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
            onSeason={(s) => write({ season: s })}
            find={find}
            onOpenPlayer={onOpenPlayer}
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

/**
 * What `/` and `/rankings` render before the URL can be read. The board reads
 * its filters from `useSearchParams`, which on a prerendered route has no
 * value until the browser runs -- so everything under the Suspense boundary
 * renders client-side, and this stands in for it. Same container, same header
 * height, same control-row height, same row height, so the real board lands
 * without moving anything.
 */
export function RankingsWorkspaceFallback() {
  return (
    <div className="mx-auto max-w-[1320px] px-4 pt-5 pb-10 sm:px-6 lg:px-8" aria-busy>
      <h1 className="font-display text-[22px] leading-7 font-bold tracking-[-0.01em]">Rankings</h1>
      <div aria-hidden className="mt-3 h-[46px] md:h-[38px]" />
      <div role="status" aria-label="Loading" className="mt-3 space-y-0.5">
        {Array.from({ length: 10 }, (_, i) => (
          <Skeleton key={i} className="h-11 rounded-md" />
        ))}
      </div>
    </div>
  );
}
