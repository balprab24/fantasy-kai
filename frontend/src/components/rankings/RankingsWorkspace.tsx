"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useLayoutEffect, useMemo, useOptimistic, useRef, useState, useTransition } from "react";
import { BoardSkeleton, RankingsBoard } from "@/components/RankingsBoard";
import { BUTTON_SECONDARY } from "@/components/ui/buttons";
import { boardSearch, parseBoardParams, type BoardState } from "@/lib/boardParams";
import { clearBoardReturn, readBoardReturn, saveBoardReturn } from "@/lib/boardReturn";
import { profileLabel } from "@/lib/profiles";
import { BOARD_PAGE_SIZE, useRankingsBoard, useSelectedProfile } from "@/lib/queries";
import { FIRST_SEASON, currentSeason } from "@/lib/season";
import { FilterBar, SCOPES } from "./FilterBar";

/** How long typing in the search box waits before the URL catches up. */
const FIND_DEBOUNCE_MS = 300;

/**
 * The rankings workspace -- what `/rankings` shows, and where a member lands.
 *
 * One board, on one route. The home page once carried its own hero and its own
 * 100-row board, pinned to a season literal, and the two boards drifted apart;
 * `/` is now the landing page and shows no live board at all, so there is
 * nothing left to drift. The page is a tool, so it opens on the tool: a
 * two-line header, one row of controls, then rows.
 *
 * Every filter lives in the URL and is read back from it on each render --
 * never copied into state, which would be a second source of truth. That is
 * what lets a board survive a trip to a player page and back, a refresh, or
 * being sent to someone. A filter change replaces the URL rather than adding
 * a history entry, so back from a player lands on the board you left rather
 * than walking through every filter you tried on the way.
 *
 * Writes go through `router.replace`, not `history.replaceState`. Next 16
 * follows a bare replaceState by moving the URL but keeping the route tree it
 * rendered for the old query, and a player opened from that board then fails
 * the overlay's interception twice and falls back to a full page load
 * (measured: after any filter or scoring change, never on a fresh load). A
 * router write keeps the tree and the URL in step. It waits on the server for
 * the new query, so the controls answer from an optimistic copy of the URL
 * until it lands, and nothing on screen waits for the round trip.
 */
export function RankingsWorkspace() {
  const router = useRouter();
  const liveParams = useSearchParams();
  const pathname = usePathname();
  const now = currentSeason();
  const [, startTransition] = useTransition();

  // A player opened from the board shows over it on its own URL
  // (/players/[id], an intercepted route), and while it does, the search
  // params are the player view's -- not the board's. The board keeps the last
  // ones it saw on its own route, so the rows underneath never re-sort to
  // whatever the player view's URL says. Adjusted during render, the way React
  // documents state derived from a changing input.
  // Compared as text, so a fresh params object for the same URL is no change.
  const onBoard = pathname === "/rankings";
  // What the URL says, or is about to say: a write shows at once, and gives
  // way to the real URL when the router has caught up.
  const [live, showWrite] = useOptimistic(liveParams.toString());
  const [frozen, setFrozen] = useState(live);
  if (onBoard && live !== frozen) setFrozen(live);
  const params = onBoard ? live : frozen;
  const openPlayerId = onBoard ? null : Number(/^\/players\/([1-9][0-9]{0,14})$/.exec(pathname)?.[1] ?? 0) || null;
  const state = useMemo(
    () =>
      parseBoardParams(new URLSearchParams(params), {
        first: FIRST_SEASON,
        current: now,
      }),
    [params, now],
  );
  const { season, position, scope } = state;
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [showTiers, setShowTiers] = useState(true);
  const { profiles, profileId } = useSelectedProfile(state.profileId);

  // Merged into the newest write, not what this render saw: a debounced search
  // can land after a filter click whose navigation has not committed yet (the
  // address bar still shows the old query until it does), and a stale copy
  // would quietly undo that click.
  const latest = useRef<string | null>(null);
  const committed = liveParams.toString();
  useEffect(() => {
    if (latest.current === committed) latest.current = null;
  }, [committed]);
  const currentQuery = useCallback(() => latest.current ?? window.location.search.replace(/^\?/, ""), []);
  const write = useCallback(
    (change: Partial<BoardState>) => {
      const base = currentQuery();
      const current = parseBoardParams(new URLSearchParams(base), { first: FIRST_SEASON, current: now });
      const search = boardSearch({ ...current, ...change }, now);
      const query = search.replace(/^\?/, "");
      latest.current = query;
      startTransition(() => {
        showWrite(query);
        router.replace(pathname + search, { scroll: false });
      });
    },
    [pathname, now, router, showWrite, currentQuery],
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
  const scopePhrase = SCOPES.find((s) => s.value === scope)?.phrase;

  // Leaving for a player: make the URL current (a search still waiting on its
  // debounce would otherwise be lost) and note where on the board we were.
  const onOpenPlayer = (href: string) => {
    if (pendingFind.current) {
      clearTimeout(pendingFind.current);
      pendingFind.current = null;
      setWritten(find);
      write({ q: find });
    }
    const query = currentQuery();
    saveBoardReturn({
      board: window.location.pathname + (query ? `?${query}` : ""),
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

  const labelFor = (key: string) => {
    const p = profiles.data?.find((x) => String(x.id) === key);
    return p ? profileLabel(p) : undefined;
  };

  return (
    <div className="mx-auto max-w-[1600px] px-2 pt-2 pb-8 sm:px-4">
      <h1 className="sr-only">Rankings</h1>
      <FilterBar
        profiles={profiles.data} profileId={profileId} onProfile={(id) => write({ profileId: id })}
        view={state.view ?? "overview"} onView={(view) => write({ view })}
        season={season} onSeason={(s) => write({ season: s })}
        scope={scope} onScope={(s) => write({ scope: s })}
        position={position} onPosition={(p) => write({ position: p })}
        find={find} onFind={onFind} filtersOpen={filtersOpen} onFilters={() => setFiltersOpen(!filtersOpen)}
      />
      <div className="flex min-h-7 flex-wrap items-center justify-between gap-x-3 py-1 text-[12px] text-mute">
        <p>{profile ? profileLabel(profile) : "Loading scoring"} · {season} · {scopePhrase}{total !== undefined && ` · ${total} players`}</p>
        <p>{rows?.[0]?.dataThrough === null ? "Extended stats coming shortly" : rows?.[0]?.dataThrough ? `Data through Week ${rows[0].dataThrough}` : ""}</p>
      </div>
      {filtersOpen && (
        <section id="board-options" aria-label="Filters and display" className="mb-2 flex flex-wrap items-center gap-4 rounded-control border border-line bg-surface p-3 text-[13px] text-mute">
          <label className="flex items-center gap-2 text-ink"><input type="checkbox" checked={showTiers} onChange={(e) => setShowTiers(e.target.checked)} />Show tiers</label>
          <p>Green per-game figures: inside a 12-team league’s starters at that position. — means no recorded value.</p>
          <button type="button" className="text-ink underline" onClick={() => { onFind(""); write({ position: null, q: "" }); }}>Clear position and search</button>
          <button type="button" className="ml-auto text-ink underline" onClick={() => setFiltersOpen(false)}>Close filters</button>
        </section>
      )}
      <div>
        <RankingsBoard
          rows={rows}
          view={state.view ?? "overview"}
          showTiers={showTiers}
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
          openPlayerId={openPlayerId}
          labelFor={labelFor}
        />
      </div>

      {rows && total !== undefined && rows.length > 0 && (
        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 text-sm">
          <span className="tabular text-mute" aria-live="polite">
            {board.hasNextPage ? `Showing 1–${rows.length} of ${total}` : `All ${total} players shown`}
          </span>
          {board.hasNextPage && (
            <button
              type="button"
              onClick={() => void board.fetchNextPage()}
              disabled={board.isFetchingNextPage}
              className={BUTTON_SECONDARY}
            >
              {board.isFetchingNextPage
                ? "Scoring…"
                : `Show next ${Math.min(BOARD_PAGE_SIZE, total - rows.length)}`}
            </button>
          )}
        </div>
      )}

      <p className="mt-8 text-[13px] text-faint">
        No point on this board is stored. Every one is computed on request against the ruleset you
        picked.
      </p>
    </div>
  );
}

/**
 * What `/rankings` renders before the URL can be read. The board reads its
 * filters from `useSearchParams`, which on a prerendered route has no value
 * until the browser runs -- so everything under the Suspense boundary renders
 * client-side, and this stands in for it at the real heights.
 *
 * Today it never paints: every page renders per request (the CSP nonce), so
 * there is nothing to wait for -- measured 2026-09-30, full loads at six
 * widths and client-side arrivals from sign-in and from a player page. It
 * stays so the page holds still if the route is ever prerendered again.
 */
export function RankingsWorkspaceFallback() {
  return (
    <div className="mx-auto max-w-[1600px] px-2 pt-2 sm:px-4" aria-busy>
      <h1 className="sr-only">Rankings</h1>
      <div aria-hidden className="skeleton h-[78px] sm:h-12" />
      <div aria-hidden className="h-7" />
      <div role="status" aria-label="Loading"><BoardSkeleton /></div>
    </div>
  );
}
