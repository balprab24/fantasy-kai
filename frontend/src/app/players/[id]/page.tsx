"use client";

import { useParams, useSearchParams } from "next/navigation";
import { useState } from "react";
import Link from "next/link";
import { RulesetSwitch } from "@/components/RulesetSwitch";
import { useGamelog, usePlayer, useSelectedProfile } from "@/lib/queries";
import type { GamelogWeek, StatKey } from "@/lib/types";

/**
 * Only the stats that actually moved are shown per week. All thirteen columns
 * for a running back would be eleven zeroes and two numbers, which buries the
 * two. Order follows the stat line as a person reads it, not `StatKey` order.
 */
const STAT_LABELS: [StatKey, string][] = [
  ["pass_yd", "pass yd"],
  ["pass_td", "pass TD"],
  ["pass_int", "int"],
  ["pass_2pt", "pass 2pt"],
  ["rush_yd", "rush yd"],
  ["rush_td", "rush TD"],
  ["rush_2pt", "rush 2pt"],
  ["rec", "rec"],
  ["rec_yd", "rec yd"],
  ["rec_td", "rec TD"],
  ["rec_2pt", "rec 2pt"],
  ["fum_lost", "fum lost"],
  ["ret_td", "ret TD"],
];

function StatLine({ week }: { week: GamelogWeek }) {
  const moved = STAT_LABELS.filter(([key]) => week.stats[key] !== 0);
  if (moved.length === 0) {
    return <span className="text-faint">did not record a stat</span>;
  }
  // Separated by space rather than by a dot or a pipe: the pairs already read
  // as pairs because the figure is dark and its label is not, and a delimiter
  // between every one of them in a column this dense adds noise to a line that
  // is mostly punctuation-free by design.
  return (
    <span className="flex flex-wrap gap-x-4 gap-y-0.5 text-mute">
      {moved.map(([key, label]) => (
        <span key={key} className="whitespace-nowrap">
          <span className="tabular text-ink">{week.stats[key]}</span> {label}
        </span>
      ))}
    </span>
  );
}

export default function PlayerPage() {
  const routeParams = useParams<{ id: string }>();
  const search = useSearchParams();
  const playerId = Number(routeParams.id);

  const { profiles, profileId, setProfileId } = useSelectedProfile(
    search.get("profileId") ? Number(search.get("profileId")) : null,
  );
  const [season, setSeason] = useState(Number(search.get("season") ?? 2025));

  const player = usePlayer(playerId);
  const gamelog = useGamelog(playerId, profileId, season);

  const weeks = gamelog.data?.weeks ?? [];
  const best = Math.max(1, ...weeks.map((w) => w.points));

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <Link
        href="/rankings"
        className="text-sm text-mute underline decoration-line-strong underline-offset-4 hover:text-ink"
      >
        Back to rankings
      </Link>

      <h1 className="font-display mt-4 text-3xl font-bold tracking-tight">
        {player.data?.name ?? "—"}
      </h1>
      <p className="mt-1 text-sm text-mute">
        {player.data ? (
          <>
            {player.data.position} for {player.data.team ?? "no listed team"}
            {player.data.status ? ` — ${player.data.status}` : ""}
          </>
        ) : (
          "Loading"
        )}
      </p>

      <div className="mt-7 flex flex-wrap items-center gap-4">
        {profiles.data && (
          <RulesetSwitch profiles={profiles.data} selected={profileId} onSelect={setProfileId} />
        )}
        <label className="flex items-center gap-2 text-sm">
          <span className="text-mute">Season</span>
          <select
            value={season}
            onChange={(e) => setSeason(Number(e.target.value))}
            className="rounded border border-line-strong bg-raised px-2 py-1.5"
          >
            {[2026, 2025, 2024, 2023, 2022, 2021, 2020].map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
          </select>
        </label>
      </div>

      {gamelog.data && (
        <dl className="mt-8 flex flex-wrap gap-x-12 gap-y-4 border-y border-line py-5">
          <div>
            <dt className="text-sm text-mute">Points</dt>
            <dd className="font-display tabular text-2xl font-bold">
              {gamelog.data.totalPoints.toFixed(1)}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-mute">Games</dt>
            <dd className="font-display tabular text-2xl font-bold">
              {gamelog.data.gamesPlayed}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-mute">Per game</dt>
            <dd className="font-display tabular text-2xl font-bold">
              {(gamelog.data.gamesPlayed
                ? gamelog.data.totalPoints / gamelog.data.gamesPlayed
                : 0
              ).toFixed(1)}
            </dd>
          </div>
        </dl>
      )}

      <h2 className="font-display mt-10 text-lg font-bold tracking-tight">Game log</h2>
      <p className="mt-1 max-w-[68ch] text-sm leading-relaxed text-mute">
        Every week this player was recorded, including the postseason. Rankings stop at the regular
        season because fantasy leagues do, but a game log is a record of what happened.
      </p>

      {weeks.length === 0 ? (
        <p className="py-12 text-sm text-mute">
          {gamelog.isLoading ? "Loading the season…" : "No games recorded for this season."}
        </p>
      ) : (
        <table className="mt-5 w-full border-collapse text-sm">
          <thead>
            <tr className="border-b border-line-strong text-left text-mute">
              <th scope="col" className="w-12 py-2 pr-2 font-medium">Wk</th>
              <th scope="col" className="w-16 py-2 pr-2 font-medium">Opp</th>
              <th scope="col" className="hidden w-16 py-2 pr-2 text-right font-medium sm:table-cell">
                Snaps
              </th>
              <th scope="col" className="py-2 pr-2 font-medium">Stat line</th>
              <th scope="col" className="w-28 py-2 text-right font-medium">Points</th>
            </tr>
          </thead>
          <tbody>
            {weeks.map((week) => (
              <tr key={`${week.season}-${week.week}`} className="border-b border-line align-top">
                <td className="tabular py-2.5 pr-2">
                  {week.week}
                  {week.seasonType === "POST" && (
                    <span className="ml-1 text-xs text-faint">post</span>
                  )}
                </td>
                <td className="py-2.5 pr-2 text-mute">{week.opponent ?? "—"}</td>
                <td className="tabular hidden py-2.5 pr-2 text-right text-mute sm:table-cell">
                  {week.snapPct === null ? "—" : `${week.snapPct.toFixed(0)}%`}
                </td>
                <td className="py-2.5 pr-2 leading-relaxed">
                  <StatLine week={week} />
                </td>
                <td className="py-2.5 text-right">
                  <span className="tabular font-medium">{week.points.toFixed(1)}</span>
                  <span
                    aria-hidden
                    className="mt-1 block h-1 rounded-full bg-field/70"
                    style={{
                      width: `${Math.max(0, (week.points / best) * 100)}%`,
                      marginLeft: "auto",
                    }}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
