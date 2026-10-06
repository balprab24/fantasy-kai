"use client";

import { useState } from "react";
import { formatPoints } from "@/lib/board";
import { ageOn } from "@/lib/player";
import { columnsFor, formatCount, GROUP_LABELS } from "@/lib/playerStats";
import type { CareerSeason, PlayerDetail } from "@/lib/types";
import { PositionBadge, positionGlow, positionHue, positionName } from "../rankings/PositionBadge";
import { Skeleton } from "../ui/StatusMessage";
import { PlayerCutout } from "./PlayerCutout";

/** nflverse roster codes worth a word. Anything else is shown as it comes. */
const STATUS: Record<string, string> = {
  RES: "Reserve",
  INA: "Inactive",
  CUT: "Released",
  DEV: "Practice squad",
  RET: "Retired",
  UFA: "Free agent",
};

/**
 * The scouter report's plate: the one expressive surface inside the product,
 * because here the player is the subject. The cut-out stands on the plate,
 * lit from behind by an aura in his position's hue -- the product's anime
 * nod, and semantic, since the hue is the position. Then the name, who he
 * is, and the season's line set as type rather than as tiles. (A faint route
 * of the season once ran across the plate too; it crossed the stat line, and
 * the chart below already draws the season, so it went.)
 *
 * Every figure is the career endpoint's: the same points, per-game and games
 * the board shows for this season, and the positional rank the board derives.
 * Nothing is added up here.
 */
export function PlayerIdentity({
  player,
  season,
  selected,
  careerLoading,
  scoringLabel,
  stale,
  scored,
}: {
  player: PlayerDetail;
  season: number | null;
  selected: CareerSeason | null;
  careerLoading: boolean;
  scoringLabel: string;
  stale: boolean;
  /** False for a position v1 does not score: its points are "—", not 0.0. */
  scored: boolean;
}) {
  // Today's age is display arithmetic on the birth date, computed in the
  // browser: this panel only renders after the player has been fetched there.
  const [today] = useState(() => new Date());
  const age = ageOn(player.birthDate, today);
  const status = player.status && player.status !== "ACT" ? player.status : null;
  const groups = selected ? columnsFor(player.position, [selected]) : [];
  const role = positionName(player.position);

  return (
    <section
      aria-labelledby="player-name"
      className="relative isolate overflow-hidden rounded-plate bg-surface"
    >
      <div className="grid items-end md:grid-cols-[minmax(0,20rem)_minmax(0,1fr)] md:gap-x-8 lg:grid-cols-[minmax(0,25rem)_minmax(0,1fr)] lg:gap-x-10 xl:grid-cols-[minmax(0,26rem)_minmax(0,1fr)]">
        {/* The player, lit from behind by his position's hue -- a close
            stage light rather than a wash over the whole plate, so it reads
            as light and not as a stain. Below md the image's flat crop line
            would land mid-plate, above the name, so the image fades out
            there instead of ending on a hard edge. */}
        <div className="relative mx-auto w-[min(82%,19rem)] pt-8 [mask-image:linear-gradient(to_bottom,#000_76%,transparent)] md:mx-0 md:w-full md:pt-5 md:pl-4 md:[mask-image:none]">
          <div
            aria-hidden
            className="absolute inset-x-[4%] top-[12%] bottom-0 -z-10"
            style={{
              background: `radial-gradient(50% 58% at 50% 62%, ${positionGlow(player.position, 0.3)}, transparent 100%)`,
            }}
          />
          <PlayerCutout name={player.name} espnId={player.espnId} />
        </div>

        <div className="min-w-0 px-5 pt-4 pb-7 md:px-0 md:pt-9 md:pr-8 md:pb-8">
          <h1
            id="player-name"
            className="type-display text-[clamp(2.5rem,5.2vw,4.5rem)] break-words text-ink"
          >
            {player.name}
          </h1>
          <p className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1.5 text-[15px] text-mute">
            <span className={`font-semibold capitalize ${positionHue(player.position).text}`}>{role}</span>
            <TeamMark player={player} />
            <span className="tabular">
              {age !== null ? <>Age {age}</> : <span className="text-faint">Age not on record</span>}
            </span>
            {status && (
              <span title={`Roster status: ${status}`} className="text-q-mid">
                {STATUS[status] ?? status}
              </span>
            )}
          </p>

          <div className={`mt-6 transition-opacity ${stale ? "opacity-60" : ""}`}>
            {careerLoading ? (
              <Skeleton className="h-11 w-[26rem] max-w-full" />
            ) : selected ? (
              <>
                <dl className="flex flex-wrap items-baseline gap-x-8 gap-y-3">
                  <StatFigure label="Rank" hint="Place at his position by regular-season points">
                    {selected.posRank !== null ? (
                      <PositionBadge
                        position={player.position}
                        rank={selected.posRank}
                        basis={`by ${selected.season} season points`}
                        className="font-label text-[2.5rem] leading-none font-bold italic"
                      />
                    ) : (
                      <span className="text-faint" title="v1 ranks QB, RB, WR and TE">
                        —
                      </span>
                    )}
                  </StatFigure>
                  <StatFigure label="Points" unit="pts">
                    {scored ? formatPoints(selected.points) : <NotScored />}
                  </StatFigure>
                  <StatFigure label="Per game" unit="per game">
                    {scored ? formatPoints(selected.pointsPerGame) : <NotScored />}
                  </StatFigure>
                  <StatFigure label="Games" unit={selected.gamesPlayed === 1 ? "game" : "games"}>
                    {selected.gamesPlayed}
                  </StatFigure>
                </dl>
                <p className="mt-2.5 text-[13px] text-mute">
                  {selected.season} regular season, scored under {scoringLabel}
                </p>
              </>
            ) : (
              <p className="text-[15px] text-mute">
                {season === null
                  ? "No regular-season games on record."
                  : `No regular-season games in ${season}.`}
              </p>
            )}
          </div>

          {groups.length > 0 && selected && (
            <dl className="mt-5 space-y-1 text-[14px]">
              {groups.map((g) => (
                <div key={g.group} className="flex flex-wrap items-baseline gap-x-4 gap-y-0.5">
                  <dt className="w-20 shrink-0 text-[13px] text-mute">{GROUP_LABELS[g.group]}</dt>
                  <dd className="flex flex-wrap gap-x-4 gap-y-0.5">
                    {g.columns.map((c) => (
                      <span key={c.key} title={c.title} className="whitespace-nowrap text-mute">
                        <span className="font-semibold text-ink">{formatCount(c.value(selected))}</span>{" "}
                        {c.label.toLowerCase() === "yd" ? "yds" : c.label}
                      </span>
                    ))}
                  </dd>
                </div>
              ))}
            </dl>
          )}
        </div>
      </div>
    </section>
  );
}

/**
 * One figure of the season line: the number set big, its unit small after it,
 * as a broadcast stat strip reads -- not a label stacked over a number in a
 * tile. Proportional figures: these stand alone, and tabular would space them
 * loose. The label is for screen readers; the unit says it on screen.
 */
function StatFigure({
  label,
  unit,
  hint,
  children,
}: {
  label: string;
  unit?: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex items-baseline gap-1.5" title={hint}>
      <dt className="sr-only">{label}</dt>
      <dd className="flex items-baseline gap-1.5">
        <span className="type-stat text-[2.5rem] text-ink">{children}</span>
        {unit && <span className="text-[14px] text-mute">{unit}</span>}
      </dd>
    </div>
  );
}

function NotScored() {
  return (
    <span className="text-faint" title="v1 scores QB, RB, WR and TE only">
      —
    </span>
  );
}

/** Logo and full name; the abbreviation alone for a team with no logo on record. */
function TeamMark({ player }: { player: PlayerDetail }) {
  const [broken, setBroken] = useState(false);
  if (!player.team) return <span className="text-faint">No current team</span>;
  const logo = player.teamLogo?.startsWith("https://") && !broken ? player.teamLogo : null;
  return (
    <span className="inline-flex min-w-0 items-center gap-2">
      {logo && (
        // eslint-disable-next-line @next/next/no-img-element -- a 20px logo from ESPN's CDN; nothing for next/image to optimise
        <img
          src={logo}
          alt=""
          width={22}
          height={22}
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="size-[22px] shrink-0 object-contain"
        />
      )}
      <span className="text-ink">{player.teamName ?? player.team}</span>
    </span>
  );
}
