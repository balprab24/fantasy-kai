"use client";

import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import { assignTiers, toBoardRows, type BoardRow, type Metric } from "@/lib/board";
import { ApiError } from "@/lib/api";
import { saveBoardSequence } from "@/lib/boardSequence";
import { biggestMovers, type Mover } from "@/lib/movers";
import { FIRST_SEASON } from "@/lib/season";
import type { RankingRow, Scope } from "@/lib/types";
import { COL, COLUMN_COUNT, PlayerRow } from "./rankings/PlayerRow";
import { TierHeader } from "./rankings/TierHeader";
import { PositionChip } from "./ui/PositionChip";
import { Skeleton, StatusMessage } from "./ui/StatusMessage";

const QUALITY_KEY =
  "Judged against players at the same position: green inside a 12-team league's starters, white up to twice that, dim beyond. Players with under half the board's games are not judged.";

/**
 * Remembers where every player stood under the previously selected ruleset, so
 * the next one can say how far they moved. Deliberately client-side and
 * deliberately not persisted: the comparison is "against what I was just
 * looking at", which is a property of this session and of nothing else.
 *
 * Movement is only meaningful between two rulesets on the *same* board. When
 * the board itself changes -- another season, position or window -- the
 * baseline is dropped. Without that, switching to QBs compared QB-only ranks
 * with the overall board and called every quarterback "up one".
 *
 * `stale` rows -- the previous board, kept on screen while the next is scored --
 * are never recorded as a baseline: they belong to a board the keys no longer
 * describe, and recording them would bring that same bug back over a slow link.
 */
function usePreviousRanks(
  rows: RankingRow[] | undefined,
  key: string,
  board: string,
  stale: boolean,
) {
  const [previous, setPrevious] = useState<{ ranks: Map<number, number>; key: string } | null>(null);
  const lastKey = useRef<string | null>(null);
  const lastBoard = useRef<string | null>(null);
  const lastRanks = useRef<Map<number, number> | null>(null);

  useEffect(() => {
    if (!rows || stale) return;
    if (lastBoard.current !== null && lastBoard.current !== board) {
      setPrevious(null);
    } else if (lastKey.current !== null && lastKey.current !== key && lastRanks.current) {
      setPrevious({ ranks: lastRanks.current, key: lastKey.current });
    }
    lastKey.current = key;
    lastBoard.current = board;
    lastRanks.current = new Map(rows.map((row) => [row.playerId, row.rank]));
  }, [rows, key, board, stale]);

  return previous;
}

/**
 * Case-, accent- and punctuation-insensitive. Apostrophes and periods are
 * dropped rather than spaced, so "jamarr" finds Ja'Marr Chase and "aj brown"
 * finds A.J. Brown; hyphens become spaces, so "amon ra" finds Amon-Ra.
 */
function normalize(s: string) {
  return s
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "") // combining accents, split off by NFD
    .replace(/['\u2019.]/g, "")
    .replace(/[^a-z0-9]+/gi, " ")
    .trim()
    .toLowerCase();
}

function matches(name: string, needle: string) {
  const hay = normalize(name);
  // Spaces ignored as a second chance: "amonra" and "de von" still land.
  return hay.includes(needle) || hay.replaceAll(" ", "").includes(needle.replaceAll(" ", ""));
}

export function RankingsBoard({
  rows,
  comparisonKey,
  boardKey = "",
  season,
  profileId,
  loading,
  scope = "season",
  stale = false,
  fetching = false,
  error = null,
  onRetry,
  onSeason,
  find = "",
  onOpenPlayer,
  openPlayerId = null,
  labelFor,
}: {
  rows: RankingRow[] | undefined;
  /** Changes whenever the thing being compared changes (the ruleset). */
  comparisonKey: string;
  /** Identifies the board apart from its ruleset: season, position, window. */
  boardKey?: string;
  season: number;
  profileId: number | null;
  loading?: boolean;
  /** Decides which number the board is sorted by, and so which one it shows. */
  scope?: Scope;
  /** `rows` are the previous board, kept on screen while the next one loads. */
  stale?: boolean;
  /** A newer answer is being computed while this one stays on screen. */
  fetching?: boolean;
  error?: unknown;
  onRetry?: () => void;
  /** Offered when this season has nothing scored yet, to step back a year. */
  onSeason?: (season: number) => void;
  /** Narrows the rows already loaded. Tiers are decided before it applies. */
  find?: string;
  /** A player row's link is being followed. */
  onOpenPlayer?: (href: string) => void;
  /** The player whose view is open over the board, if any. */
  openPlayerId?: number | null;
  /** A comparison key (a profile id) as a ruleset's name, for the movers line. */
  labelFor?: (key: string) => string | undefined;
}) {
  const metric: Metric = scope === "per_game" ? "pointsPerGame" : "points";
  const prior = usePreviousRanks(rows, comparisonKey, boardKey, stale);
  const previous = prior?.ranks ?? null;
  const board = useMemo(() => toBoardRows(rows ?? [], metric), [rows, metric]);
  const { tiers, rest } = useMemo(() => assignTiers(board), [board]);
  const needle = normalize(find);

  const hrefFor = (playerId: number) => `/players/${playerId}?season=${season}&profileId=${profileId ?? ""}`;
  const movers = useMemo(
    () => (previous && rows && !stale ? biggestMovers(toBoardRows(rows, metric), previous) : null),
    [previous, rows, stale, metric],
  );

  const failure = error ? (
    <StatusMessage
      tone="error"
      title="The board could not be scored."
      action={onRetry ? { label: "Try again", onClick: onRetry } : undefined}
    >
      {error instanceof ApiError ? error.message : "The API did not answer. Check that it is running."}
    </StatusMessage>
  ) : null;

  if (!rows) {
    // No rows and no error means the answer is still on its way -- including
    // while the ruleset list itself loads. It never means "pick a ruleset":
    // one is always chosen by default.
    return (
      failure ?? (
        <div role="status" aria-label={loading ? "Scoring every game" : "Loading"}>
          <BoardSkeleton />
        </div>
      )
    );
  }

  if (rows.length === 0) {
    return (
      // Every position is scored in every played week, so an empty board is a
      // season with no regular-season games yet -- typically the current one,
      // between March and kickoff. Say so; never swap last year in silently.
      <StatusMessage
        tone="info"
        title={`No ${season} games have been scored yet.`}
        action={
          onSeason && season > FIRST_SEASON
            ? { label: `View ${season - 1}`, onClick: () => onSeason(season - 1) }
            : undefined
        }
      >
        Rankings appear here once the first regular-season week has been played and pulled in.
      </StatusMessage>
    );
  }

  const groups = [
    ...tiers.map((t) => ({ key: t.letter, letter: t.letter, rows: t.rows })),
    ...(rest.length ? [{ key: "rest", letter: null, rows: rest }] : []),
  ]
    .map((g) => ({
      ...g,
      high: g.rows[0].value,
      low: g.rows[g.rows.length - 1].value,
      shown: needle ? g.rows.filter((r) => matches(r.name, needle)) : g.rows,
    }))
    .filter((g) => g.shown.length > 0);

  // The order a member sees, filters and search applied: what "next player"
  // means in the player view.
  const sequence = groups.flatMap((g) => g.shown.map((r) => ({ playerId: r.playerId, name: r.name, rank: r.rank })));
  const open = (playerId: number) => {
    saveBoardSequence(sequence, playerId, `?season=${season}&profileId=${profileId ?? ""}`);
    onOpenPlayer?.(hrefFor(playerId));
  };

  const perGame = metric === "pointsPerGame";
  const delta = (row: BoardRow) => {
    if (!previous) return 0;
    const was = previous.get(row.playerId);
    return was === undefined ? null : was - row.rank;
  };

  return (
    <div className="relative">
      {failure && <div className="mb-4">{failure}</div>}

      {movers && (movers.up.length > 0 || movers.down.length > 0) && prior && (
        <MoversStrip
          from={labelFor?.(prior.key)}
          to={labelFor?.(comparisonKey)}
          movers={movers}
          hrefFor={hrefFor}
          onOpen={open}
        />
      )}

      {groups.length === 0 ? (
        <StatusMessage tone="info" title={`No player matching “${find.trim()}” on this board.`}>
          The search covers the {rows.length} players loaded so far, not the whole league. Try part of a
          surname, or load more of the board below.
        </StatusMessage>
      ) : (
        <div className="relative rounded-panel border border-line bg-surface">
          {fetching && (
            <div aria-hidden className="absolute inset-x-4 top-0 z-20 h-0.5 overflow-hidden rounded-full">
              <div className="sweep h-full w-2/5 bg-energy" />
            </div>
          )}
          <table aria-busy={fetching} className="w-full border-separate border-spacing-0 text-sm">
            <caption className="sr-only">
              Player rankings for the {season} season under the selected scoring ruleset, grouped
              into tiers
            </caption>
            {/* The column labels stay in view while the board scrolls, pinned
                under the bar, on the panel's own ground. */}
            <thead>
              <tr className="text-left [&>th]:sticky [&>th]:top-14 [&>th]:z-10 [&>th]:h-11 [&>th]:border-b [&>th]:border-line [&>th]:bg-surface [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute [&>th:first-child]:rounded-tl-panel [&>th:last-child]:rounded-tr-panel">
                <th scope="col" className={COL.rank}>
                  #
                </th>
                <th scope="col" className={COL.player}>
                  Player
                </th>
                <th scope="col" className={COL.pos}>
                  Pos
                </th>
                <th scope="col" className={COL.team}>
                  {/* Current, on every season's board: team history is not
                      stored, so a player traded since shows his new team. */}
                  <span title="Current team">Team</span>
                </th>
                <th scope="col" className={COL.games}>
                  <abbr title="Games played" className="no-underline">
                    G
                  </abbr>
                </th>
                {/* The headline column is whatever the board is sorted by; the
                    other figure steps down beside it. */}
                <th scope="col" className={COL.primary}>
                  {perGame ? <span title={QUALITY_KEY}>Per game</span> : "Points"}
                </th>
                <th scope="col" className={COL.secondary}>
                  {perGame ? (
                    "Total"
                  ) : (
                    <abbr title={`Points per game. ${QUALITY_KEY}`} className="no-underline">
                      Per G
                    </abbr>
                  )}
                </th>
                {previous && (
                  <th scope="col" className={COL.move}>
                    <span title="Places moved since the ruleset you were last viewing">Move</span>
                  </th>
                )}
              </tr>
            </thead>
            {groups.map((g, gi) => (
              <tbody key={g.key}>
                <TierHeader
                  letter={g.letter}
                  count={g.rows.length}
                  high={g.high}
                  low={g.low}
                  unit={perGame ? "pts a game" : "pts"}
                  colSpan={previous ? COLUMN_COUNT : COLUMN_COUNT - 1}
                  first={gi === 0}
                />
                {g.shown.map((row) => (
                  <PlayerRow
                    key={row.playerId}
                    row={row}
                    delta={delta(row)}
                    baseline={previous?.size ?? 0}
                    metric={metric}
                    href={hrefFor(row.playerId)}
                    onOpen={() => open(row.playerId)}
                    open={row.playerId === openPlayerId}
                    showMove={previous !== null}
                  />
                ))}
              </tbody>
            ))}
          </table>
        </div>
      )}
    </div>
  );
}

/**
 * After a switch, the moves worth knowing about, said in a line: the biggest
 * gains and losses inside the tiered top of the board, each a link to the
 * player. Derived from the two boards' ranks (`lib/movers.ts`), nothing else.
 */
function MoversStrip({
  from,
  to,
  movers,
  hrefFor,
  onOpen,
}: {
  from: string | undefined;
  to: string | undefined;
  movers: { up: Mover[]; down: Mover[] };
  hrefFor: (playerId: number) => string;
  onOpen: (playerId: number) => void;
}) {
  const chip = (m: Mover) => (
    <li key={m.playerId} className="min-w-0">
      <Link
        href={hrefFor(m.playerId)}
        onClick={() => onOpen(m.playerId)}
        className="flex h-10 min-w-0 items-center gap-2 rounded-control bg-well pr-3 pl-2 text-[13px] text-ink shadow-[inset_0_0_0_1px_var(--color-line)] transition-colors hover:bg-lift sm:h-9"
        title={`${m.name}: ${m.previous} to ${m.rank}`}
      >
        <span className={`inline-flex items-center gap-1 font-semibold tabular ${m.delta > 0 ? "text-ink" : "text-mute"}`}>
          <svg aria-hidden width="8" height="8" viewBox="0 0 8 8">
            <path d={m.delta > 0 ? "M4 1 7.5 7h-7z" : "M.5 1h7L4 7z"} fill="currentColor" />
          </svg>
          <span className="sr-only">{m.delta > 0 ? "up" : "down"}</span>
          {Math.abs(m.delta)}
        </span>
        <span className="min-w-0 truncate font-medium">{m.name}</span>
        <PositionChip position={m.position} className="max-sm:hidden" />
        <span className="tabular text-mute max-sm:hidden">
          {m.previous}
          <span aria-hidden> &rarr; </span>
          <span className="sr-only"> to </span>
          {m.rank}
        </span>
      </Link>
    </li>
  );
  return (
    <section aria-label="Biggest moves" className="mb-4 rounded-panel border border-line bg-surface px-4 py-3.5 sm:px-5">
      <p className="text-[14px] text-mute">
        {from && to ? (
          <>
            From <span className="font-semibold text-ink">{from}</span> to{" "}
            <span className="font-semibold text-ink">{to}</span>, the biggest moves in the top 60:
          </>
        ) : (
          <>The biggest moves in the top 60 since the last ruleset:</>
        )}
      </p>
      {/* Gains and losses as two columns on a phone, one wrapping line from sm. */}
      <div className="mt-2.5 grid grid-cols-2 gap-2 sm:flex sm:flex-wrap">
        <ul aria-label="Up the board" className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap">
          {movers.up.map(chip)}
        </ul>
        <ul aria-label="Down the board" className="flex min-w-0 flex-col gap-2 sm:flex-row sm:flex-wrap">
          {movers.down.map(chip)}
        </ul>
      </div>
    </section>
  );
}

/**
 * The board's shape before it has rows: the column-label band and ten row
 * lines at the real heights, so the answer lands without moving anything.
 * The workspace's Suspense fallback draws the same thing.
 */
export function BoardSkeleton() {
  return (
    <div aria-hidden className="rounded-panel border border-line bg-surface">
      <div className="h-11 border-b border-line" />
      <div className="flex h-[3.75rem] items-center gap-3 pl-3 sm:pl-4">
        <Skeleton className="h-8 w-11" />
        <Skeleton className="h-3.5 w-48 max-w-[40%]" />
      </div>
      {Array.from({ length: 10 }, (_, i) => (
        <div key={i} className="flex h-[3.75rem] items-center gap-3 border-t border-line pr-4 sm:h-[3.25rem]">
          <div className="w-11 sm:w-16" />
          <Skeleton className="size-9 rounded-full" />
          <Skeleton className="h-3.5 w-44 max-w-[40%]" />
          <Skeleton className="ml-auto h-3.5 w-12" />
          <Skeleton className="h-3.5 w-10" />
        </div>
      ))}
    </div>
  );
}
