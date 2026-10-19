"use client";

import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { BoardSkeleton, RankingsBoard } from "@/components/RankingsBoard";
import { BUTTON_SECONDARY, LINK_QUIET } from "@/components/ui/buttons";
import { FormatBar } from "@/components/ui/FormatBar";
import { Icon } from "@/components/ui/Icon";
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
 * being sent to someone. Writes use `history.replaceState`, which Next keeps
 * in step with `useSearchParams`: a filter change is not a history entry, so
 * back from a player page lands on the board you left rather than walking
 * through every filter you tried on the way.
 */
export function RankingsWorkspace() {
  const liveParams = useSearchParams();
  const pathname = usePathname();
  const now = currentSeason();

  // A player opened from the board shows over it on its own URL
  // (/players/[id], an intercepted route), and while it does, the search
  // params are the player view's -- not the board's. The board keeps the last
  // ones it saw on its own route, so the rows underneath never re-sort to
  // whatever the player view's URL says. Adjusted during render, the way React
  // documents state derived from a changing input.
  // Compared as text, so a fresh params object for the same URL is no change.
  const onBoard = pathname === "/rankings";
  const live = liveParams.toString();
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

  const labelFor = (key: string) => {
    const p = profiles.data?.find((x) => String(x.id) === key);
    return p ? profileLabel(p) : undefined;
  };

  return (
    <div className="mx-auto max-w-[1440px] px-4 pt-8 pb-10 sm:px-6 lg:px-8">
      <header className="flex flex-wrap items-end justify-between gap-x-8 gap-y-3">
        <div className="min-w-0">
          <h1 className="type-display text-[clamp(2.75rem,6vw,3.75rem)] text-ink">Rankings</h1>
          {/* The board's recipe, said in a sentence: which rules, which
              season, which window. A ranking without its ruleset is a number
              with no meaning here. */}
          <p className="mt-3 text-[16px] text-mute">
            {profile ? (
              <>
                Scored under <span className="font-semibold text-ink">{profileLabel(profile)}</span>{" "}
                for the {season} season, {scopePhrase}.
                {total !== undefined && total > 0 && <> {total} {total === 1 ? "player" : "players"}.</>}
              </>
            ) : (
              <>The {season} season, {scopePhrase}.</>
            )}
          </p>
        </div>
        <Link href="/profiles" className={`inline-flex items-center gap-1.5 pb-1 text-sm ${LINK_QUIET}`}>
          <Icon name="scoring" size={16} className="text-mute" />
          Build your league&rsquo;s ruleset
        </Link>
      </header>

      <div className="mt-6">
        {profiles.data ? (
          <FormatBar profiles={profiles.data} selected={profileId} onSelect={(id) => write({ profileId: id })} />
        ) : (
          <div aria-hidden className="skeleton h-16 rounded-control" />
        )}
      </div>

      <div className="mt-4">
        <FilterBar
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

      {/* The one colour the board gives a figure, keyed where it can be
          read -- not in a tooltip a phone cannot reach. */}
      <p className="mt-4 flex items-center gap-2 text-[13px] text-mute">
        <span aria-hidden className="size-2 shrink-0 rounded-full bg-q-good" />
        Per-game figures in green are inside a 12-team league&rsquo;s starters at that position.
      </p>

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
    <div className="mx-auto max-w-[1440px] px-4 pt-8 pb-10 sm:px-6 lg:px-8" aria-busy>
      <h1 className="type-display text-[clamp(2.75rem,6vw,3.75rem)] text-ink">Rankings</h1>
      {/* The recipe sentence, the scoring switch, the filters and the key, at
          their heights on a wide screen; they wrap taller on a phone. */}
      <div aria-hidden className="mt-3 h-6" />
      <div aria-hidden className="skeleton mt-6 h-16 rounded-control" />
      <div aria-hidden className="skeleton mt-4 h-10 rounded-control" />
      <div aria-hidden className="mt-4 h-5" />
      <div role="status" aria-label="Loading" className="mt-3">
        <BoardSkeleton />
      </div>
    </div>
  );
}
