"use client";

import { useState } from "react";
import { STARTERS, formatPoints } from "@/lib/board";
import { ageOn } from "@/lib/player";
import { finishStep, lastWeekPlayed, starterShare, type HeatStep } from "@/lib/playerView";
import type { CareerSeason, PlayerDetail, ScoringProfile } from "@/lib/types";
import { positionGlow, positionHue, positionName } from "../rankings/PositionBadge";
import { FormatBar } from "../ui/FormatBar";
import { PositionChip } from "../ui/PositionChip";
import { SeasonSelect } from "../ui/SeasonSelect";
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

/** A season's positional finish on the four-step scale, as text: 1-12 green down to 37+ red. */
const FINISH_TEXT: Record<HeatStep, string> = {
  0: "text-heat-green",
  1: "text-heat-yellow",
  2: "text-heat-orange",
  3: "text-heat-red",
};

export interface OverallRank {
  rank: number | null;
  outside: boolean;
  loading: boolean;
  stale: boolean;
}

/** "Jaxon" over "Smith-Njigba": the first word, then the rest. */
export function splitName(name: string): [string, string] {
  const at = name.indexOf(" ");
  return at > 0 ? [name.slice(0, at), name.slice(at + 1)] : ["", name];
}

/**
 * Who he is, and his season in figures: the player view's first card, at the
 * top of its sidebar. Name, team and status; his cut-out on a plate lit in
 * his position's hue; then the season the page is on as two rows of figures.
 *
 * Every figure is the API's: the career endpoint's points, per game, games and
 * positional rank (the same the board shows for this season), the board's own
 * overall rank, and a count of the weeks his weekly positional rank -- also
 * the API's -- fell inside a 12-team league's starters. Nothing is scored here.
 */
export function IdentityCard({
  player,
  season,
  selected,
  careerLoading,
  careerFailed,
  scoringLabel,
  stale,
  scored,
  overall,
  current,
}: {
  player: PlayerDetail;
  season: number | null;
  selected: CareerSeason | null;
  careerLoading: boolean;
  /** The career could not be scored: say so, rather than "no games". */
  careerFailed: boolean;
  scoringLabel: string;
  stale: boolean;
  /** False for a position v1 does not score: its points are "—", not 0.0. */
  scored: boolean;
  overall: OverallRank;
  /** The season being played now, the only one still moving. */
  current: number;
}) {
  // Today's age is display arithmetic on the birth date, computed in the
  // browser: this card only renders after the player has been fetched there.
  const [today] = useState(() => new Date());
  const age = ageOn(player.birthDate, today);
  const status = player.status && player.status !== "ACT" ? player.status : null;
  const line = Object.hasOwn(STARTERS, player.position) ? STARTERS[player.position as keyof typeof STARTERS] : undefined;
  const starts = selected ? starterShare(selected.weeks, line) : null;
  const through = selected && selected.season === current ? lastWeekPlayed(selected) : null;
  const finish = selected ? finishStep(selected.posRank) : null;
  const [first, rest] = splitName(player.name);

  return (
    <section aria-labelledby="player-name" className="overflow-hidden rounded-panel border border-line bg-surface">
      <div className="px-5 pt-5">
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-[13px] text-mute">
          <PositionChip position={player.position} size="md" />
          <TeamMark player={player} />
          {status && (
            <span title={`Roster status: ${status}`} className="text-q-mid">
              {STATUS[status] ?? status}
            </span>
          )}
        </p>
        <h1 id="player-name" className="mt-3 break-words">
          {first && <span className="block font-label text-[17px] leading-tight font-semibold text-mute">{first} </span>}
          <span className="block type-display text-[clamp(2.4rem,3.2vw,2.9rem)] text-ink">{rest}</span>
        </h1>
        <p className="sr-only">{positionName(player.position)}</p>
      </div>

      <Plate player={player} />

      <div className={`border-t border-line transition-opacity ${stale ? "opacity-60" : ""}`}>
        {careerFailed ? (
          <p className="px-5 py-4 text-[14px] text-danger">
            This season could not be scored. The panels say why and offer a retry.
          </p>
        ) : careerLoading ? (
          <div className="grid grid-cols-4 gap-px">
            {Array.from({ length: 4 }, (_, i) => (
              <div key={i} className="px-3 py-3.5">
                <Skeleton className="h-6 w-12" />
                <Skeleton className="mt-2 h-3 w-10" />
              </div>
            ))}
          </div>
        ) : selected ? (
          <>
            <dl className="grid grid-cols-4 [&>div+div]:border-l [&>div]:border-line">
              <Figure label="Overall" hint="Place on the whole board, every position, by season points">
                {!scored ? (
                  <span className="text-faint">—</span>
                ) : overall.loading ? (
                  <Skeleton className="inline-block h-5 w-9 align-middle" />
                ) : overall.outside ? (
                  <span className="text-[0.7em] text-mute" title="Outside the top 200 on this board">200+</span>
                ) : overall.rank !== null ? (
                  <span className={overall.stale ? "opacity-60" : ""}>
                    <span className="text-[0.7em] text-mute">#</span>
                    {overall.rank}
                  </span>
                ) : (
                  <span className="text-faint">—</span>
                )}
              </Figure>
              <Figure label="Pos rank" hint={`Place among every ${player.position} by ${selected.season} season points`}>
                {selected.posRank !== null ? (
                  <>
                    <span className={positionHue(player.position).text}>{player.position}</span>
                    <span className={finish === null ? "text-ink" : FINISH_TEXT[finish]}>{selected.posRank}</span>
                  </>
                ) : (
                  <span className="text-faint" title="v1 ranks QB, RB, WR and TE">—</span>
                )}
              </Figure>
              <Figure label="Per game" hint="Fantasy points per game played">
                {scored ? formatPoints(selected.pointsPerGame) : <span className="text-faint">—</span>}
              </Figure>
              <Figure label="Age" hint={age !== null ? "Today, from his birth date" : "No birth date on record"}>
                {age !== null ? age : <span className="text-faint">—</span>}
              </Figure>
            </dl>
            <dl className="grid grid-cols-3 border-t border-line [&>div+div]:border-l [&>div]:border-line">
              <Figure label="Points" hint={`Fantasy points, ${selected.season} regular season, ${scoringLabel}`}>
                {scored ? formatPoints(selected.points) : <span className="text-faint">—</span>}
              </Figure>
              <Figure label={selected.gamesPlayed === 1 ? "Game" : "Games"}>{selected.gamesPlayed}</Figure>
              <Figure
                label={starts ? `Top-${starts.line} wks` : "Starter wks"}
                hint={
                  starts
                    ? `Weeks he finished inside the top ${starts.line} ${player.position}s -- a 12-team league's starters -- out of the weeks he played`
                    : undefined
                }
              >
                {starts ? (
                  <>
                    {starts.starts}
                    <span className="text-[0.62em] text-mute"> of {starts.games}</span>
                  </>
                ) : (
                  <span className="text-faint">—</span>
                )}
              </Figure>
            </dl>
            <p className="border-t border-line px-5 py-3 text-[12px] leading-relaxed text-mute">
              {selected.season} regular season
              {through !== null && <>, through week {through}</>}, scored under{" "}
              <span className="font-semibold text-ink">{scoringLabel}</span>.
            </p>
          </>
        ) : (
          <p className="px-5 py-4 text-[14px] text-mute">
            {season === null ? "No regular-season games on record." : `No regular-season games in ${season}.`}
          </p>
        )}
      </div>
    </section>
  );
}

/**
 * The two choices behind every number on the page: which ruleset scores it,
 * and which season. One ruleset a row in the sidebar; three to a row on a
 * phone, where the sidebar is the top of the page.
 */
export function ScoringCard({
  profiles,
  profileId,
  onProfile,
  seasons,
  season,
  onSeason,
}: {
  profiles: ScoringProfile[] | undefined;
  profileId: number | null;
  onProfile: (id: number) => void;
  /** Every season with a game, newest first. */
  seasons: number[];
  season: number | null;
  onSeason: (season: number) => void;
}) {
  return (
    <section aria-labelledby="scoring-card-title" className="rounded-panel border border-line bg-surface p-4">
      <div className="flex items-center justify-between gap-3 pb-3 pl-1">
        <h2 id="scoring-card-title" className="type-heading text-[1.25rem] text-ink">
          Scored as
        </h2>
        {seasons.length > 0 && season !== null && (
          <SeasonSelect value={season} options={seasons} onChange={onSeason} variant="field" />
        )}
      </div>
      {profiles ? (
        <>
          <FormatBar profiles={profiles} selected={profileId} onSelect={onProfile} layout="list" className="max-lg:hidden" />
          <FormatBar profiles={profiles} selected={profileId} onSelect={onProfile} size="sm" className="lg:hidden" />
        </>
      ) : (
        <Skeleton className="h-40" />
      )}
    </section>
  );
}

/** One figure: the value in the italic figure face, its label small under it. */
function Figure({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col-reverse px-3 py-3 first:pl-5" title={hint}>
      <dt className="mt-1 truncate text-[11px] text-mute">{label}</dt>
      <dd className="type-figure truncate text-[22px] text-ink">{children}</dd>
    </div>
  );
}

/**
 * His cut-out on a plate: a tight light in the position's hue behind him, and
 * behind that the logo's two strokes, large and quiet. On a missing id or a
 * failed load the cut-out's box shows his initials (`PlayerCutout`), so the
 * plate keeps its shape either way.
 */
function Plate({ player }: { player: PlayerDetail }) {
  return (
    <div className="relative isolate mx-auto mt-3 max-w-[17rem] px-6 sm:max-w-[22rem]">
      <svg
        aria-hidden
        viewBox="0 0 26 26"
        preserveAspectRatio="xMidYMax meet"
        className="absolute inset-x-[8%] top-0 bottom-0 -z-20 h-full w-[84%]"
        style={{ color: positionGlow(player.position, 0.12) }}
      >
        <path d="M9 3h6L9 23H3z" fill="currentColor" />
        <path d="M18 3h5l-6 20h-5z" fill="currentColor" opacity="0.6" />
      </svg>
      <div
        aria-hidden
        className="absolute inset-0 -z-10"
        style={{ background: `radial-gradient(55% 62% at 50% 72%, ${positionGlow(player.position, 0.28)}, transparent 100%)` }}
      />
      <PlayerCutout name={player.name} espnId={player.espnId} position={player.position} />
    </div>
  );
}

/** Logo and full name; the abbreviation alone for a team with no logo on record. */
function TeamMark({ player }: { player: PlayerDetail }) {
  const [broken, setBroken] = useState(false);
  if (!player.team) return <span className="text-faint">No current team</span>;
  const logo = player.teamLogo?.startsWith("https://") && !broken ? player.teamLogo : null;
  return (
    <span className="inline-flex min-w-0 items-center gap-1.5">
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
      <span className="font-medium text-ink">{player.teamName ?? player.team}</span>
    </span>
  );
}
