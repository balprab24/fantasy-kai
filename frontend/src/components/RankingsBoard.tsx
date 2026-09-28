"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { assignTiers, toBoardRows, type BoardRow, type Metric } from "@/lib/board";
import { ApiError } from "@/lib/api";
import { FIRST_SEASON } from "@/lib/season";
import type { RankingRow, Scope } from "@/lib/types";
import { COL, COLUMN_COUNT, PlayerRow } from "./rankings/PlayerRow";
import { TierHeader } from "./rankings/TierHeader";
import { Skeleton, StatusMessage } from "./ui/StatusMessage";

const QUALITY_KEY =
  "Coloured against players at the same position: green inside a 12-team league's starters, yellow up to twice that, red beyond. Players with under half the board's games are not judged.";

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
  const [previous, setPrevious] = useState<Map<number, number> | null>(null);
  const lastKey = useRef<string | null>(null);
  const lastBoard = useRef<string | null>(null);
  const lastRanks = useRef<Map<number, number> | null>(null);

  useEffect(() => {
    if (!rows || stale) return;
    if (lastBoard.current !== null && lastBoard.current !== board) {
      setPrevious(null);
    } else if (lastKey.current !== null && lastKey.current !== key) {
      setPrevious(lastRanks.current);
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
}) {
  const metric: Metric = scope === "per_game" ? "pointsPerGame" : "points";
  const previous = usePreviousRanks(rows, comparisonKey, boardKey, stale);
  const board = useMemo(() => toBoardRows(rows ?? [], metric), [rows, metric]);
  const { tiers, rest } = useMemo(() => assignTiers(board), [board]);
  const needle = normalize(find);

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
        <div role="status" aria-label={loading ? "Scoring every game" : "Loading"} className="space-y-0.5">
          {Array.from({ length: 10 }, (_, i) => (
            <Skeleton key={i} className="h-11 rounded-md" />
          ))}
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

  const perGame = metric === "pointsPerGame";
  const delta = (row: BoardRow) => {
    if (!previous) return 0;
    const was = previous.get(row.playerId);
    return was === undefined ? null : was - row.rank;
  };

  return (
    <div className="relative">
      {failure && <div className="mb-4">{failure}</div>}

      {fetching && (
        <div aria-hidden className="absolute inset-x-0 -top-2 h-0.5 overflow-hidden rounded-full">
          <div className="sweep h-full w-2/5 bg-energy/80" />
        </div>
      )}

      {groups.length === 0 ? (
        <StatusMessage tone="info" title={`No player matching “${find.trim()}” on this board.`}>
          The search covers the {rows.length} players loaded so far, not the whole league.
        </StatusMessage>
      ) : (
        <div className="max-md:-mx-4 max-md:overflow-x-auto max-md:px-4">
          <table
            aria-busy={fetching}
            className="w-full border-separate border-spacing-y-0.5 text-sm"
          >
            <caption className="sr-only">
              Player rankings for the {season} season under the selected scoring ruleset, grouped
              into tiers
            </caption>
            <thead>
              <tr className="text-left">
                <th scope="col" className={`${COL.rank} eyebrow bg-paper pb-1 pl-2 font-semibold`}>
                  #
                </th>
                <th scope="col" className={`${COL.player} eyebrow bg-paper pb-1 font-semibold`}>
                  Player
                </th>
                <th scope="col" className={`${COL.pos} eyebrow pb-1 font-semibold`}>
                  Pos
                </th>
                <th scope="col" className={`${COL.team} eyebrow pb-1 font-semibold`}>
                  Team
                </th>
                <th scope="col" className={`${COL.games} eyebrow pb-1 font-semibold`}>
                  <abbr title="Games played" className="no-underline">
                    G
                  </abbr>
                </th>
                {/* The headline column is whatever the board is sorted by; the
                    other figure steps down beside it. */}
                <th scope="col" className={`${COL.primary} eyebrow pb-1 font-semibold`}>
                  {perGame ? <span title={QUALITY_KEY}>Per game</span> : "Points"}
                </th>
                <th scope="col" className={`${COL.secondary} eyebrow pb-1 font-semibold`}>
                  {perGame ? (
                    "Total"
                  ) : (
                    <abbr title={`Points per game. ${QUALITY_KEY}`} className="no-underline">
                      Per G
                    </abbr>
                  )}
                </th>
                <th scope="col" className={`${COL.move} eyebrow pb-1 font-semibold`}>
                  <span title="Places moved since the ruleset you were last viewing">Move</span>
                </th>
              </tr>
            </thead>
            {groups.map((g) => (
              <tbody key={g.key}>
                <TierHeader
                  letter={g.letter}
                  count={g.rows.length}
                  high={g.high}
                  low={g.low}
                  unit={perGame ? "pts/game" : "pts"}
                  colSpan={COLUMN_COUNT}
                />
                {g.shown.map((row) => (
                  <PlayerRow
                    key={row.playerId}
                    row={row}
                    delta={delta(row)}
                    baseline={previous?.size ?? 0}
                    metric={metric}
                    href={`/players/${row.playerId}?season=${season}&profileId=${profileId ?? ""}`}
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
