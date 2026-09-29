"use client";

import { useState } from "react";
import { formatPoints } from "@/lib/board";
import { ageOn } from "@/lib/player";
import { columnsFor, formatCount, GROUP_LABELS } from "@/lib/playerStats";
import type { CareerSeason, PlayerDetail } from "@/lib/types";
import { PlayerAvatar } from "../rankings/PlayerAvatar";
import { PositionBadge } from "../rankings/PositionBadge";
import { Skeleton } from "../ui/StatusMessage";

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
 * Who the player is and what his selected season was worth -- the top of the
 * page, and at `xl` a rail that stays put while the log scrolls.
 *
 * Every figure in the tiles is the career endpoint's: the same points,
 * per-game and games the board shows for this season, and the positional rank
 * the board derives. Nothing is added up here.
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

  return (
    <section
      aria-labelledby="player-name"
      className="rounded-xl border border-line bg-raised p-4 xl:p-5"
    >
      <p className="eyebrow flex items-center gap-2">
        <span aria-hidden className="size-1.5 rounded-full bg-ki shadow-[0_0_8px_rgb(255_138_61/0.7)]" />
        Scouter report
      </p>

      <div className="mt-3 grid gap-x-6 gap-y-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)] xl:grid-cols-1">
        <div className="flex min-w-0 items-center gap-4">
          <PlayerAvatar name={player.name} position={player.position} espnId={player.espnId} size="lg" />
          <div className="min-w-0">
            <h1
              id="player-name"
              className="font-display text-2xl leading-tight font-bold tracking-[-0.01em] break-words"
            >
              {player.name}
            </h1>
            <div className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-sm text-mute">
              <PositionBadge position={player.position} rank={null} />
              <TeamMark player={player} />
            </div>
            <p className="tabular mt-1.5 text-sm text-mute">
              {age !== null ? <>Age {age}</> : <span className="text-faint">Age not on record</span>}
              {status && (
                <>
                  {" · "}
                  <span title={`Roster status: ${status}`} className="text-q-mid">
                    {STATUS[status] ?? status}
                  </span>
                </>
              )}
            </p>
          </div>
        </div>

        <div className={`min-w-0 transition-opacity ${stale ? "opacity-60" : ""}`}>
          <p className="eyebrow">
            {season !== null && `${season} season · `}
            {scoringLabel}
          </p>
          {careerLoading ? (
            <Skeleton className="mt-2 h-[68px] rounded-lg" />
          ) : selected ? (
            <dl className="mt-2 grid grid-cols-4 gap-px overflow-hidden rounded-lg border border-line bg-line">
              <Tile label="Rank" hint="Place at his position by regular-season points">
                {selected.posRank !== null ? (
                  <PositionBadge
                    position={player.position}
                    rank={selected.posRank}
                    basis={`by ${selected.season} season points`}
                  />
                ) : (
                  <span className="text-base text-faint" title="v1 ranks QB, RB, WR and TE">
                    —
                  </span>
                )}
              </Tile>
              <Tile label="Points">{scored ? formatPoints(selected.points) : <NotScored />}</Tile>
              <Tile label="Per game">
                {scored ? formatPoints(selected.pointsPerGame) : <NotScored />}
              </Tile>
              <Tile label="Games">{selected.gamesPlayed}</Tile>
            </dl>
          ) : (
            <p className="mt-2 rounded-lg border border-line px-3 py-4 text-sm text-mute">
              {season === null
                ? "No regular-season games on record."
                : `No regular-season games in ${season}.`}
            </p>
          )}

          {groups.length > 0 && selected && (
            <dl className="mt-3 space-y-1.5 text-[13px]">
              {groups.map((g) => (
                <div key={g.group} className="grid grid-cols-[4.5rem_minmax(0,1fr)] items-baseline gap-x-2">
                  <dt className="text-xs text-faint">{GROUP_LABELS[g.group]}</dt>
                  <dd className="flex flex-wrap gap-x-3 gap-y-0.5">
                    {g.columns.map((c) => (
                      <span key={c.key} title={c.title} className="whitespace-nowrap text-mute">
                        <span className="tabular font-medium text-ink">
                          {formatCount(c.value(selected))}
                        </span>{" "}
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

function NotScored() {
  return (
    <span className="text-base text-faint" title="v1 scores QB, RB, WR and TE only">
      —
    </span>
  );
}

function Tile({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="flex min-w-0 flex-col justify-between gap-1 bg-raised px-2.5 py-2" title={hint}>
      <dt className="text-[11px] text-faint">{label}</dt>
      <dd className="font-display text-xl leading-none font-semibold text-ink">{children}</dd>
    </div>
  );
}

/** Logo and full name; the abbreviation alone for a team with no logo on record. */
function TeamMark({ player }: { player: PlayerDetail }) {
  const [broken, setBroken] = useState(false);
  if (!player.team) return <span className="text-faint">No current team</span>;
  const logo = player.teamLogo?.startsWith("https://") && !broken ? player.teamLogo : null;
  return (
    <span className="inline-flex min-w-0 flex-wrap items-center gap-x-1.5">
      {logo && (
        // eslint-disable-next-line @next/next/no-img-element -- a 20px logo from ESPN's CDN; nothing for next/image to optimise
        <img
          src={logo}
          alt=""
          width={20}
          height={20}
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="size-5 shrink-0 object-contain"
        />
      )}
      <span className="text-ink">{player.teamName ?? player.team}</span>
      <span className="text-faint">{player.team}</span>
    </span>
  );
}
