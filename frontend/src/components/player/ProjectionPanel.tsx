"use client";

import { formatPoints } from "@/lib/board";
import { useProjection } from "@/lib/queries";
import type { Projection, ProjectionSource, ProjectionState } from "@/lib/types";
import { matchupOf, projectionNotes } from "../rankings/Projections";
import { StatusMessage } from "../ui/StatusMessage";
import { Section } from "./Section";

/** Why one player has no line this week, when the API can say. */
const MISSING: Partial<Record<ProjectionState, string>> = {
  BYE: "On a bye in the projected week.",
  NO_HISTORY: "No recorded NFL game to project from.",
  NOT_ROSTERED: "Not on a roster for the projected week.",
};

/**
 * This week's projection on a player's report: the figure, the game, and --
 * one click away -- the stat line it was scored from, part by part, under the
 * page's ruleset.
 *
 * Says nothing at all while it loads and when no projection is stored
 * anywhere: the report is about what happened, and a section that only ever
 * says "not yet" is noise on every player's page. The projections board
 * explains the absence once.
 */
export function ProjectionPanel({
  playerId,
  profileId,
}: {
  playerId: number;
  profileId: number | null;
}) {
  const query = useProjection(playerId, profileId);
  const data = query.data;

  if (query.error) {
    return (
      <Section title="Projection">
        <StatusMessage
          tone="error"
          title="This week's projection could not be scored."
          action={{ label: "Try again", onClick: () => void query.refetch() }}
        />
      </Section>
    );
  }
  if (!data || data.state === "UNAVAILABLE") return null;

  const row = data.projection;
  if (!row) {
    const reason = MISSING[data.state];
    return reason ? (
      <Section title="Projection">
        <p className="text-sm text-mute">{reason}</p>
      </Section>
    ) : null;
  }

  const notes = projectionNotes(row);
  return (
    <Section title="Projection" meta={`Week ${row.week}, ${row.team} ${matchupOf(row)}`}>
      {data.state === "STALE" && (
        <p className="mb-3 text-sm text-mute">
          An older stored week, kept as the record of what was projected — not a forecast.
        </p>
      )}
      <p>
        <span className="font-label text-[40px] leading-none font-bold tracking-[-0.01em] text-ink">
          {formatPoints(row.points)}
        </span>{" "}
        <span className="text-sm text-mute">projected pts</span>
      </p>
      <p className="mt-2 text-[13px] text-mute">
        {[...notes, `${row.gamesUsed} games of history`].join(" · ")}
      </p>
      <Breakdown row={row} source={data.source} />
    </Section>
  );
}

/**
 * The projected line, stat by stat: expected count × your rate = points, and
 * the threshold bonuses' expected value in one line. Each part is shown
 * rounded and the total was computed before rounding, so the parts can be a
 * hundredth off their sum -- said under the table, not hidden.
 */
function Breakdown({ row, source }: { row: Projection; source: ProjectionSource }) {
  return (
    <details className="mt-4 max-w-xl text-sm">
      <summary className="cursor-pointer text-energy-text">How this was projected</summary>
      <p className="mt-3 text-mute">
        <span className="text-ink">{source.label}</span>: {source.description} Generated{" "}
        {new Date(row.generatedAt).toLocaleString()}.
      </p>
      <table className="mt-3 w-full border-separate border-spacing-0 text-[13px]">
        <thead>
          <tr className="text-left font-label text-[12px] text-mute [&>th]:h-8 [&>th]:border-b [&>th]:border-line [&>th]:font-semibold">
            <th scope="col">Stat</th>
            <th scope="col" className="text-right">
              Expected
            </th>
            <th scope="col" className="text-right">
              Rate
            </th>
            <th scope="col" className="text-right">
              Points
            </th>
          </tr>
        </thead>
        <tbody className="tabular [&_td]:h-8 [&_td]:border-b [&_td]:border-line [&_th]:h-8 [&_th]:border-b [&_th]:border-line">
          {row.parts.map((part) => (
            <tr key={part.stat}>
              <th scope="row" className="text-left font-normal text-ink">
                {part.stat.replaceAll("_", " ")}
              </th>
              <td className="text-right text-mute">{part.value.toFixed(2)}</td>
              <td className="text-right text-mute">× {part.rate}</td>
              <td className="text-right text-ink">{part.points.toFixed(2)}</td>
            </tr>
          ))}
          <tr>
            <th scope="row" className="text-left font-normal text-ink">
              Threshold bonuses
            </th>
            <td colSpan={2} />
            <td className="text-right text-ink">{row.bonusPoints.toFixed(2)}</td>
          </tr>
        </tbody>
      </table>
      <p className="mt-2 text-[12px] text-mute">
        Parts are shown rounded; the total is computed before rounding.
      </p>
    </details>
  );
}
