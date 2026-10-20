"use client";

import { useState } from "react";
import { STARTERS, bandFor, formatPoints } from "@/lib/board";
import { ageOn } from "@/lib/player";
import { lastWeekPlayed, starterShare } from "@/lib/playerView";
import type { CareerSeason, PlayerDetail, ScoringProfile } from "@/lib/types";
import { positionGlow, positionHue, positionName } from "../rankings/PositionBadge";
import { QUALITY_TEXT } from "../rankings/PlayerRow";
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

/**
 * The player's stage: the one expressive surface inside the product, because
 * here the player is the subject. His cut-out stands on the stage, lit from
 * behind in his position's hue, in front of the logo's two strokes; then who
 * he is, the two choices that decide every number on the page (scoring and
 * season), and the season's line as a strip of figures.
 *
 * Every figure is the API's: the career endpoint's points, per game, games and
 * positional rank (the same the board shows for this season), the board's own
 * overall rank, and a count of the weeks his weekly positional rank -- also
 * the API's -- fell inside a 12-team league's starters. Nothing is scored here.
 */
export function PlayerStage({
  player,
  season,
  selected,
  careerLoading,
  scoringLabel,
  stale,
  scored,
  overall,
  profiles,
  profileId,
  onProfile,
  seasons,
  onSeason,
  current,
}: {
  player: PlayerDetail;
  season: number | null;
  selected: CareerSeason | null;
  careerLoading: boolean;
  scoringLabel: string;
  stale: boolean;
  /** False for a position v1 does not score: its points are "—", not 0.0. */
  scored: boolean;
  overall: { rank: number | null; outside: boolean; loading: boolean; stale: boolean };
  profiles: ScoringProfile[] | undefined;
  profileId: number | null;
  onProfile: (id: number) => void;
  /** Every season with a game, newest first. */
  seasons: number[];
  onSeason: (season: number) => void;
  /** The season being played now, which is the only one still moving. */
  current: number;
}) {
  // Today's age is display arithmetic on the birth date, computed in the
  // browser: this panel only renders after the player has been fetched there.
  const [today] = useState(() => new Date());
  const age = ageOn(player.birthDate, today);
  const status = player.status && player.status !== "ACT" ? player.status : null;
  const line = Object.hasOwn(STARTERS, player.position) ? STARTERS[player.position as keyof typeof STARTERS] : undefined;
  const starts = selected ? starterShare(selected.weeks, line) : null;
  const through = selected && selected.season === current ? lastWeekPlayed(selected) : null;
  const quality = selected ? bandFor(selected.posRank, player.position) : null;

  return (
    <section aria-labelledby="player-name" className="relative isolate overflow-hidden rounded-plate border border-line bg-surface">
      <div className="grid md:grid-cols-[minmax(0,18rem)_minmax(0,1fr)] lg:grid-cols-[minmax(0,25rem)_minmax(0,1fr)]">
        <Stage player={player} />

        <div className="min-w-0 px-4 pt-1 pb-6 sm:px-7 md:py-7 md:pr-8 md:pl-2 lg:py-9">
          <p className="flex flex-wrap items-center gap-x-4 gap-y-2 text-[15px] text-mute">
            <PositionChip position={player.position} size="lg" />
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
          <h1
            id="player-name"
            className="mt-4 type-display text-[clamp(2.75rem,6.4vw,5.25rem)] break-words text-ink"
          >
            {player.name}
          </h1>
          <p className="sr-only">{positionName(player.position)}</p>

          <div className="mt-6 flex flex-wrap items-end gap-3">
            {profiles ? (
              <FormatBar
                profiles={profiles}
                selected={profileId}
                onSelect={onProfile}
                size="sm"
                className="max-sm:w-full"
              />
            ) : (
              <Skeleton className="h-[3.25rem] w-full max-w-[34rem]" />
            )}
            {seasons.length > 0 && season !== null && (
              <SeasonSelect value={season} options={seasons} onChange={onSeason} variant="field" />
            )}
          </div>
        </div>
      </div>

      {/* The season's line: what every tab below is the detail of. */}
      <div className={`border-t border-line transition-opacity ${stale ? "opacity-60" : ""}`}>
        {careerLoading ? (
          <div className="grid grid-cols-3 gap-px lg:grid-cols-6">
            {Array.from({ length: 6 }, (_, i) => (
              <div key={i} className="px-5 py-5 sm:px-7">
                <Skeleton className="h-8 w-20" />
                <Skeleton className="mt-2 h-3 w-16" />
              </div>
            ))}
          </div>
        ) : selected ? (
          <>
            <dl className="grid grid-cols-3 lg:grid-cols-6 [&>div]:border-line max-lg:[&>div:nth-child(n+4)]:border-t lg:[&>div+div]:border-l max-lg:[&>div:not(:nth-child(3n+1))]:border-l">
              <Figure label="Position rank" hint={`Place among every ${player.position} by ${selected.season} season points`}>
                {selected.posRank !== null ? (
                  <>
                    <span className={positionHue(player.position).text}>{player.position}</span>
                    <span className={quality ? QUALITY_TEXT[quality] : "text-ink"}>{selected.posRank}</span>
                  </>
                ) : (
                  <span className="text-faint" title="v1 ranks QB, RB, WR and TE">—</span>
                )}
              </Figure>
              <Figure label="Overall" hint="Place on the whole board, every position, by season points">
                {!scored ? (
                  <span className="text-faint">—</span>
                ) : overall.loading ? (
                  <Skeleton className="inline-block h-7 w-12 align-middle" />
                ) : overall.outside ? (
                  <span className="text-[0.6em] text-mute" title="Outside the top 200 on this board">200+</span>
                ) : overall.rank !== null ? (
                  <span className={overall.stale ? "opacity-60" : ""}>
                    <span className="text-[0.7em] text-mute">#</span>
                    {overall.rank}
                  </span>
                ) : (
                  <span className="text-faint">—</span>
                )}
              </Figure>
              <Figure label="Points" hint={`Fantasy points, ${selected.season} regular season, ${scoringLabel}`}>
                {scored ? formatPoints(selected.points) : <span className="text-faint">—</span>}
              </Figure>
              <Figure label="Per game" hint="Fantasy points per game played">
                {scored ? formatPoints(selected.pointsPerGame) : <span className="text-faint">—</span>}
              </Figure>
              <Figure label={selected.gamesPlayed === 1 ? "Game" : "Games"}>{selected.gamesPlayed}</Figure>
              <Figure
                label={starts ? `Top-${starts.line} weeks` : "Starter weeks"}
                hint={
                  starts
                    ? `Weeks he finished inside the top ${starts.line} ${player.position}s -- a 12-team league's starters -- out of the weeks he played`
                    : undefined
                }
              >
                {starts ? (
                  <>
                    {starts.starts}
                    <span className="text-[0.6em] text-mute"> of {starts.games}</span>
                  </>
                ) : (
                  <span className="text-faint">—</span>
                )}
              </Figure>
            </dl>
            <p className="border-t border-line px-5 py-3 text-[13px] text-mute sm:px-7">
              {selected.season} regular season
              {through !== null && <>, through week {through}</>}, scored under{" "}
              <span className="font-semibold text-ink">{scoringLabel}</span>.
            </p>
          </>
        ) : (
          <p className="px-5 py-5 text-[15px] text-mute sm:px-7">
            {season === null ? "No regular-season games on record." : `No regular-season games in ${season}.`}
          </p>
        )}
      </div>
    </section>
  );
}

/**
 * One figure of the season line: the value big, in the italic figure face,
 * its label small under it in sentence case. The label is the `dt`; screen
 * readers hear "Points, 310.5".
 */
function Figure({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col-reverse px-4 py-4 sm:px-7 sm:py-5" title={hint}>
      <dt className="mt-1.5 truncate text-[12px] text-mute">{label}</dt>
      <dd className="type-figure truncate text-[26px] text-ink sm:text-[32px]">{children}</dd>
    </div>
  );
}

/**
 * The stage itself: a tight light in the position's hue behind the player,
 * and behind that the logo's two strokes, large and quiet -- the product's
 * mark, not decoration borrowed from anywhere. On a missing id or a failed
 * load the cut-out's box shows his initials (`PlayerCutout`), so the stage
 * keeps its shape either way.
 */
function Stage({ player }: { player: PlayerDetail }) {
  return (
    <div className="relative mx-auto w-[min(78%,19rem)] pt-6 md:mx-0 md:w-full md:self-end md:pt-8 md:pl-5 lg:pl-8">
      <svg
        aria-hidden
        viewBox="0 0 26 26"
        preserveAspectRatio="xMidYMax meet"
        className="absolute inset-x-[6%] top-[4%] bottom-0 -z-20 h-[96%] w-[88%]"
        style={{ color: positionGlow(player.position, 0.11) }}
      >
        <path d="M9 3h6L9 23H3z" fill="currentColor" />
        <path d="M18 3h5l-6 20h-5z" fill="currentColor" opacity="0.6" />
      </svg>
      <div
        aria-hidden
        className="absolute inset-x-[2%] top-[18%] bottom-0 -z-10"
        style={{
          background: `radial-gradient(52% 60% at 50% 70%, ${positionGlow(player.position, 0.26)}, transparent 100%)`,
        }}
      />
      <div className="[mask-image:linear-gradient(to_bottom,#000_80%,transparent)] md:[mask-image:none]">
        <PlayerCutout name={player.name} espnId={player.espnId} position={player.position} />
      </div>
    </div>
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
        // eslint-disable-next-line @next/next/no-img-element -- a 24px logo from ESPN's CDN; nothing for next/image to optimise
        <img
          src={logo}
          alt=""
          width={24}
          height={24}
          referrerPolicy="no-referrer"
          onError={() => setBroken(true)}
          className="size-6 shrink-0 object-contain"
        />
      )}
      <span className="font-medium text-ink">{player.teamName ?? player.team}</span>
    </span>
  );
}
