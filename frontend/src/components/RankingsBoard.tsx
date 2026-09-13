"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { Movement } from "./Movement";
import type { RankingRow } from "@/lib/types";

/**
 * Remembers where every player stood under the previously selected ruleset, so
 * the next one can say how far they moved. Deliberately client-side and
 * deliberately not persisted: the comparison is "against what I was just
 * looking at", which is a property of this session and of nothing else.
 */
function usePreviousRanks(rows: RankingRow[] | undefined, key: string) {
  const [previous, setPrevious] = useState<Map<number, number> | null>(null);
  const lastKey = useRef<string | null>(null);
  const lastRanks = useRef<Map<number, number> | null>(null);

  useEffect(() => {
    if (!rows) return;
    if (lastKey.current !== null && lastKey.current !== key) {
      setPrevious(lastRanks.current);
    }
    lastKey.current = key;
    lastRanks.current = new Map(rows.map((row) => [row.playerId, row.rank]));
  }, [rows, key]);

  return previous;
}

export function RankingsBoard({
  rows,
  comparisonKey,
  season,
  profileId,
  loading,
}: {
  rows: RankingRow[] | undefined;
  /** Changes whenever the thing being compared changes (the ruleset). */
  comparisonKey: string;
  season: number;
  profileId: number | null;
  loading?: boolean;
}) {
  const previous = usePreviousRanks(rows, comparisonKey);

  if (!rows) {
    return (
      <p className="py-16 text-sm text-mute">
        {loading ? "Scoring every game…" : "Pick a ruleset to see a board."}
      </p>
    );
  }

  if (rows.length === 0) {
    return (
      <p className="py-16 text-sm text-mute">
        No players scored under these filters. Try a different position or season.
      </p>
    );
  }

  return (
    <table className="w-full border-collapse text-sm">
      <caption className="sr-only">
        Player rankings for the {season} season under the selected scoring ruleset
      </caption>
      <thead>
        <tr className="border-b border-line-strong text-left text-mute">
          <th scope="col" className="w-10 py-2 pr-2 text-right font-medium">
            #
          </th>
          <th scope="col" className="py-2 pr-2 font-medium">
            Player
          </th>
          <th scope="col" className="hidden w-14 py-2 pr-2 font-medium sm:table-cell">
            Team
          </th>
          <th scope="col" className="hidden w-10 py-2 pr-2 text-right font-medium sm:table-cell">
            G
          </th>
          <th scope="col" className="w-14 py-2 pr-2 text-right font-medium">
            <span title="Places moved since the ruleset you were last viewing">Move</span>
          </th>
          <th scope="col" className="w-20 py-2 pr-2 text-right font-medium">
            Points
          </th>
          <th scope="col" className="hidden w-20 py-2 text-right font-medium sm:table-cell">
            Per game
          </th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => {
          const was = previous?.get(row.playerId);
          const delta = previous ? (was === undefined ? null : was - row.rank) : 0;
          return (
            <tr
              key={row.playerId}
              className={`border-b border-line ${delta ? "settled" : ""}`}
            >
              <td className="tabular py-2.5 pr-2 text-right text-mute">{row.rank}</td>
              <td className="py-2.5 pr-2">
                <Link
                  href={`/players/${row.playerId}?season=${season}&profileId=${profileId ?? ""}`}
                  className="font-medium underline decoration-transparent underline-offset-2 hover:decoration-line-strong"
                >
                  {row.name}
                </Link>
                <span className="ml-2 text-mute">{row.position}</span>
              </td>
              <td className="hidden py-2.5 pr-2 text-mute sm:table-cell">{row.team ?? "—"}</td>
              <td className="tabular hidden py-2.5 pr-2 text-right text-mute sm:table-cell">
                {row.gamesPlayed}
              </td>
              <td className="tabular py-2.5 pr-2 text-right">
                <Movement delta={delta} />
              </td>
              <td className="tabular py-2.5 pr-2 text-right font-medium">
                {row.points.toFixed(1)}
              </td>
              <td className="tabular hidden py-2.5 text-right text-mute sm:table-cell">
                {row.pointsPerGame.toFixed(1)}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
