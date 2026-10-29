"use client";

import Link from "next/link";
import { ApiError } from "@/lib/api";
import { formatPoints } from "@/lib/board";
import type { AvailabilityStatus, Projection, ProjectionBoardResponse } from "@/lib/types";
import { BoardSkeleton } from "../RankingsBoard";
import { StatusMessage } from "../ui/StatusMessage";
import { PlayerAvatar } from "./PlayerAvatar";
import { PositionBadge } from "./PositionBadge";

/** The projection board's columns, in the performance board's grammar (`MEMBER_COL`). */
const COL = {
  rank: "w-10 pr-2 text-right sm:w-12 sm:pr-3",
  player: "pr-3",
  pos: "hidden w-14 pr-3 sm:table-cell",
  matchup: "hidden w-28 pr-3 md:table-cell",
  points: "w-24 pr-2 text-right sm:pr-3",
} as const;

const cell = "h-12 border-b border-line transition-colors group-hover:bg-well sm:h-11";

/** "@ KC", "vs BUF": where the game is, from the player's side. */
export function matchupOf(row: Pick<Projection, "home" | "opponent">) {
  return `${row.home ? "vs" : "@"} ${row.opponent}`;
}

/**
 * What qualifies a projected figure, in words: the roster status it was
 * ranked under, a thin history, a kickoff already passed.
 */
const STATUS_NOTE: Record<AvailabilityStatus, string | null> = {
  AVAILABLE: null,
  QUESTIONABLE: "questionable",
  DOUBTFUL: "doubtful",
  OUT: "out",
  RESERVE: "reserve list",
  NOT_ON_ROSTER: "not on a roster",
  UNKNOWN: "status not yet known",
};

export function projectionNotes(row: Projection): string[] {
  const notes: string[] = [];
  const status = STATUS_NOTE[row.availability.status];
  if (status) notes.push(status);
  if (row.history === "LIMITED") notes.push("limited history");
  if (row.locked) notes.push("locked at kickoff");
  return notes;
}

/**
 * The stored week's projections, scored under the board's ruleset, highest
 * first: a mode of the rankings board, so it keeps the board's ruleset,
 * position and search. Presentational -- the workspace owns the query
 * (`useProjectionBoard`), so a player page and back finds the same rows.
 *
 * Rows only, no tiers: tiers are natural breaks in what happened, and a
 * projection is one model's estimate; cutting it into letters would claim a
 * certainty the number does not have.
 */
export function ProjectionBoard({
  pages,
  error,
  fetching,
  onRetry,
  find,
  profileId,
  onOpenPlayer,
}: {
  pages: ProjectionBoardResponse[] | undefined;
  error: unknown;
  /** A newer answer is being computed while this one stays on screen. */
  fetching: boolean;
  onRetry: () => void;
  find: string;
  profileId: number | null;
  onOpenPlayer: (href: string) => void;
}) {
  if (!pages) {
    return error ? (
      <BoardError error={error} onRetry={onRetry} />
    ) : (
      <div role="status" aria-label="Loading projections">
        <BoardSkeleton />
      </div>
    );
  }

  const [first] = pages;
  if (first.state === "UNAVAILABLE") {
    return (
      <StatusMessage tone="info" title="No projections are stored yet.">
        Projections appear here once a projection run has been stored for the coming week. Every
        recorded game is on the performance board.
      </StatusMessage>
    );
  }

  const rows = pages.flatMap((p) => p.page.content);
  const week = rows[0];
  const searching = find.trim() !== "";

  return (
    <div className="relative">
      {error ? (
        <div className="mb-4">
          <BoardError error={error} onRetry={onRetry} />
        </div>
      ) : null}

      {first.state === "STALE" && (
        <div className="mb-4">
          <StatusMessage tone="info" title="These are an older stored week, not a forecast.">
            No projection has been stored for the week being played. The figures below are kept as
            the record of what was projected then.
          </StatusMessage>
        </div>
      )}

      <p className="mb-2 text-[13px] text-mute">
        {week && (
          <>
            Week {week.week} of {week.season}.{" "}
          </>
        )}
        <span className="text-ink">{first.source.label}</span>: {first.source.description}
      </p>

      {fetching && (
        <div aria-hidden className="absolute inset-x-0 top-0 z-20 h-0.5 overflow-hidden">
          <div className="sweep h-full w-2/5 bg-energy" />
        </div>
      )}

      {rows.length === 0 ? (
        <StatusMessage
          tone="info"
          title={
            searching ? `No projected player matching “${find.trim()}”.` : "No projected players."
          }
        >
          {searching
            ? "The search covers every player projected this week."
            : "Nobody at this position is projected and available this week."}
        </StatusMessage>
      ) : (
        <table aria-busy={fetching} className="w-full border-separate border-spacing-0 text-sm">
          <caption className="sr-only">
            Projected points for week {week.week} of {week.season} under the selected scoring
            ruleset
          </caption>
          <thead>
            <tr className="text-left [&>th]:sticky [&>th]:top-14 [&>th]:z-10 [&>th]:h-9 [&>th]:border-b [&>th]:border-line [&>th]:bg-canvas [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute md:[&>th]:top-0">
              <th scope="col" className={COL.rank}>
                #
              </th>
              <th scope="col" className={COL.player}>
                Player
              </th>
              <th scope="col" className={COL.pos}>
                Pos
              </th>
              <th scope="col" className={COL.matchup}>
                Game
              </th>
              <th scope="col" className={COL.points}>
                Projected
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, i) => (
              <ProjectionRow
                key={row.playerId}
                row={row}
                // A search's order is the board's, but its count is not: a
                // place among the matches would read as a rank.
                place={searching ? null : i + 1}
                profileId={profileId}
                onOpenPlayer={onOpenPlayer}
              />
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function ProjectionRow({
  row,
  place,
  profileId,
  onOpenPlayer,
}: {
  row: Projection;
  place: number | null;
  profileId: number | null;
  onOpenPlayer: (href: string) => void;
}) {
  const href = `/players/${row.playerId}?season=${row.season}&profileId=${profileId ?? ""}`;
  const notes = projectionNotes(row);
  return (
    <tr className="group relative">
      <td className={`${cell} ${COL.rank} type-rank text-[15px] text-mute`}>{place ?? "—"}</td>
      <th scope="row" className={`${cell} ${COL.player} text-left font-normal`}>
        <div className="flex min-w-0 items-center gap-3">
          <PlayerAvatar name={row.name} espnId={row.espnId} />
          <span className="min-w-0">
            <Link
              href={href}
              onClick={() => onOpenPlayer(href)}
              className="block truncate text-[15px] font-semibold text-ink after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
            >
              {row.name}
            </Link>
            {/* The game folds in here below md, where its column leaves. */}
            <span className="block truncate text-[12px] text-mute">
              <span className="md:hidden">
                {row.team} {matchupOf(row)}
                {notes.length > 0 && " · "}
              </span>
              {notes.join(" · ")}
            </span>
          </span>
        </div>
      </th>
      <td className={`${cell} ${COL.pos} text-[13px]`}>
        <PositionBadge position={row.position} rank={null} />
      </td>
      <td className={`${cell} ${COL.matchup} text-[13px] text-mute`}>
        {row.team} {matchupOf(row)}
      </td>
      <td className={`${cell} ${COL.points} tabular text-[15px] font-semibold text-ink`}>
        {formatPoints(row.points)}
      </td>
    </tr>
  );
}

function BoardError({ error, onRetry }: { error: unknown; onRetry: () => void }) {
  return (
    <StatusMessage
      tone="error"
      title="The projections could not be scored."
      action={{ label: "Try again", onClick: onRetry }}
    >
      {error instanceof ApiError
        ? error.message
        : "The API did not answer. Check that it is running."}
    </StatusMessage>
  );
}
