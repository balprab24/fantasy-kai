"use client";

import { ApiError } from "@/lib/api";
import { formatPoints } from "@/lib/board";
import { usePlayerPerformance } from "@/lib/queries";

/**
 * The figures from the last-four-weeks board this page was opened from, said
 * once above a report that covers the whole season -- so the number the
 * member clicked on is still on screen, and the report's bigger season
 * figures do not read as a contradiction of it.
 *
 * A line of text, not a panel: it annotates the page, it is not a section.
 */
export function BoardContext({
  playerId,
  profileId,
  season,
}: {
  playerId: number;
  profileId: number | null;
  season: number | null;
}) {
  const performance = usePlayerPerformance(playerId, profileId, season, "last4");
  const row = performance.data;

  return (
    <p className="mt-4 text-[13px] text-mute" aria-live="polite">
      <span className="font-semibold text-ink">On the last-four-weeks board:</span>{" "}
      {row ? (
        <span className="tabular">
          {formatPoints(row.points)} pts, {formatPoints(row.pointsPerGame)} a game,{" "}
          {row.gamesPlayed} {row.gamesPlayed === 1 ? "game" : "games"}.
        </span>
      ) : performance.error instanceof ApiError && performance.error.status === 404 ? (
        // The board lists only players with a game in its window.
        "no game in those four weeks."
      ) : performance.error ? (
        "those figures could not be loaded."
      ) : (
        "…"
      )}{" "}
      The report below covers the whole regular season.
    </p>
  );
}
