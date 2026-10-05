import { positionHue } from "@/components/rankings/PositionBadge";
import { SeasonRoute } from "@/components/SeasonRoute";
import { formatPoints, starterWeeks, STARTERS } from "@/lib/board";
import { Crossfade } from "./Crossfade";
import { HERO_IMAGE } from "./heroImage";
import type { HeroSeason } from "./previewData";

/** Weeks 1-18 of the 2025 regular season, byes included, so a bye is a visible gap. */
const SLOTS = Array.from({ length: 18 }, (_, i) => i + 1);

/** One scale for every ruleset, so a switch shows the change instead of rescaling it away. */
const SCALE = 40;

/** The chart box, in the SVG's units, at the shape its CSS gives it, so the route is never stretched. */
const BOX = { w: 240, h: 56 };

/** A week's bar, cut down from full height to its value: a switch repaints without re-laying-out. */
const clip = (points: number) =>
  `inset(${100 - (Math.max(0, points) / SCALE) * 100}% 0 0 0 round 3px 3px 0 0)`;

/**
 * The player the hero's board follows, docked under it the way a broadcast
 * lower-third sits under a leaderboard: McCaffrey's 2025 season under the
 * board's rules -- his place among running backs, total and per game, and each
 * week as a bar with the season drawn through them as the route. Part of the
 * plate (Prime time), not a card laid on it.
 *
 * A week inside the RB starter line is an ink bar and any other a slate one,
 * with the count said in words; the blue route carries the season through
 * them, so on the landing the data line is the brand's blue. During the
 * entrance the figures and bars start at their 0 PPR values (`opening`) and
 * turn into the board's as its rows travel; the route is drawn once they
 * land, and again on every switch.
 *
 * `HERO_IMAGE` stays the swap point for a licensed cut-out, standing at the
 * lower-third's right edge. It is null (owner decision 2026-09-28, kept
 * 2026-09-29 and 2026-09-30), and no likeness appears here.
 */
export function HeroReadout({
  season,
  opening,
  switches,
}: {
  season: HeroSeason;
  /** The season under the ruleset the entrance opens on, while it plays; null after. */
  opening: HeroSeason | null;
  /** How many times the visitor has switched: each one redraws the route. */
  switches: number;
}) {
  const entering = opening !== null;
  const byWeek = new Map(season.weeks.map((w) => [w.week, w]));
  const before = new Map((opening?.weeks ?? []).map((w) => [w.week, w]));
  const values = SLOTS.map((week) => byWeek.get(week)?.points ?? null);

  return (
    <div
      aria-hidden
      className="relative grid items-end gap-x-6 gap-y-3 border-t border-line px-4 pt-3.5 pb-4 sm:grid-cols-[auto_minmax(0,1fr)] sm:px-5"
    >
      {HERO_IMAGE && (
        // A licensed cut-out, if one ever exists, stands at the lower-third's edge.
        // eslint-disable-next-line @next/next/no-img-element -- a local, fixed-size cut-out
        <img
          src={HERO_IMAGE.src}
          alt=""
          width={HERO_IMAGE.width}
          height={HERO_IMAGE.height}
          className="pointer-events-none absolute right-3 bottom-0 h-[150%] w-auto"
        />
      )}
      <div>
        <p className="flex items-baseline gap-2">
          <span className="font-label text-[16px] leading-tight font-bold text-ink italic">
            Christian McCaffrey
          </span>
          <span className="text-[12px] text-mute">SF, 2025</span>
        </p>
        <div className="mt-1 flex items-baseline gap-x-3">
          <span className="type-display text-[2.5rem]">
            <span className={positionHue("RB").text}>RB</span>
            <Crossfade
              entering={entering}
              then={opening?.posRank ?? null}
              now={season.posRank}
              className="text-ink"
            />
          </span>
          <span className="type-stat text-[1.25rem] text-ink">
            <Crossfade
              entering={entering}
              align="end"
              then={opening ? formatPoints(opening.points) : null}
              now={formatPoints(season.points)}
            />
            <span className="ml-1 font-sans text-[13px] font-normal text-mute">pts</span>
          </span>
          <span className="text-[13px] whitespace-nowrap text-mute">
            <Crossfade
              entering={entering}
              align="end"
              then={opening ? formatPoints(opening.pointsPerGame) : null}
              now={formatPoints(season.pointsPerGame)}
              className="tabular font-semibold text-ink"
            />{" "}
            a game
          </span>
        </div>
      </div>

      <div>
        <div className="relative w-full" style={{ aspectRatio: `${BOX.w} / ${BOX.h}` }}>
          <div className="absolute inset-0 flex items-end">
            {SLOTS.map((week) => {
              const w = byWeek.get(week);
              const was = before.get(week);
              return (
                <div key={week} className="relative flex h-full flex-1 items-end justify-center">
                  {w === undefined ? (
                    <span className="h-px w-1.5 bg-line-strong" />
                  ) : (
                    <span
                      className={`block h-full w-[56%] max-w-2 motion-safe:transition-[clip-path] motion-safe:duration-500 motion-safe:ease-out ${
                        w.posRank !== null && w.posRank <= STARTERS.RB ? "bg-ink" : "bg-chart-rest"
                      } ${was ? "bar-resort" : ""}`}
                      style={
                        {
                          clipPath: clip(w.points),
                          ...(was ? { "--clip0": clip(was.points) } : {}),
                        } as React.CSSProperties
                      }
                    />
                  )}
                </div>
              );
            })}
          </div>
          <SeasonRoute
            key={switches}
            values={values}
            max={SCALE}
            width={BOX.w}
            height={BOX.h}
            draw
            drawDelay={switches === 0 ? 1500 : 80}
            drawDuration={600}
          />
        </div>
        <p className="mt-1.5 text-[12px] text-mute">
          A top-{STARTERS.RB} running back in{" "}
          <Crossfade
            entering={entering}
            then={opening ? starterWeeks(opening.weeks, "RB") : null}
            now={starterWeeks(season.weeks, "RB")}
            className="font-semibold text-ink"
          />{" "}
          of {season.gamesPlayed} weeks
        </p>
      </div>
      {HERO_IMAGE && <p className="text-right text-xs text-faint sm:col-span-2">{HERO_IMAGE.credit}</p>}
    </div>
  );
}
