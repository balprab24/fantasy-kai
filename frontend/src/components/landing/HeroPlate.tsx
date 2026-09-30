"use client";

import { useState } from "react";
import { Movement } from "@/components/Movement";
import { ordinal, PositionBadge } from "@/components/rankings/PositionBadge";
import { SeasonRoute } from "@/components/SeasonRoute";
import { Chip } from "@/components/ui/Chip";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints, starterWeeks, STARTERS } from "@/lib/board";
import { HERO_IMAGE } from "./heroImage";
import { MCCAFFREY_2025_BY_RULESET, type HeroSeason } from "./previewData";

/** Weeks 1-18 of a 2025 regular season, byes included, so a bye is a visible gap. */
const SLOTS = Array.from({ length: 18 }, (_, i) => i + 1);

/** One scale for every ruleset, so switching shows the change instead of rescaling it away. */
const SCALE = 40;

/** The chart box's shape, in the SVG's units: it matches the box's CSS, so the route never stretches. */
const BOX = { w: 866, h: 750 };

/**
 * The hero's focal object: one real season on an orange plate. The landing
 * page's argument is "your rules decide the order", so the plate does not
 * assert it -- it lets a visitor flip the rules and watch the same stat lines
 * re-score: the bars change height, the route is drawn again, and the chips
 * say what moved (McCaffrey is 7th overall and RB2 without the point per
 * catch, 1st and RB1 with it).
 *
 * Every figure is captured from the API (`previewData.ts`); none is made up.
 * No player's likeness appears here until one is licensed for promotion
 * (owner decision 2026-09-28, kept 2026-09-29): if `HERO_IMAGE` is ever set,
 * the cut-out stands in front of the bars.
 *
 * Above `lg` the chips straddle the plate's edges, layered over the image the
 * way a broadcast graphic floats its readouts; below it the switch sits inside
 * the plate's top band and the readouts beneath it, so nothing covers the
 * season on a phone.
 */
export function HeroPlate() {
  const [ruleset, setRuleset] = useState<HeroSeason["ruleset"]>("PPR");
  // What the movement chip compares against: on arrival, 0 PPR -- the rule
  // the page is about adding.
  const [previous, setPrevious] = useState<HeroSeason["ruleset"]>("0 PPR");
  const [drawn, setDrawn] = useState(0);
  const [hover, setHover] = useState<number | null>(null);

  const season = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === ruleset)!;
  const before = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === previous)!;
  const byWeek = new Map(season.weeks.map((w) => [w.week, w]));
  const values = SLOTS.map((week) => byWeek.get(week)?.points ?? null);
  const starts = starterWeeks(season.weeks, "RB");
  const shown = hover !== null ? byWeek.get(hover) : undefined;

  const choose = (next: string) => {
    if (next === ruleset) return;
    setPrevious(ruleset);
    setRuleset(next as HeroSeason["ruleset"]);
    setDrawn((n) => n + 1);
  };

  const switcher = (
    <SegmentedControl
      legend="Scoring for this season"
      hideLegend
      variant="pill"
      value={ruleset}
      onChange={choose}
      options={MCCAFFREY_2025_BY_RULESET.map((s) => ({
        value: s.ruleset,
        label: (
          <>
            {s.ruleset}
            <span className="tabular font-normal text-mute">{formatPoints(s.points)}</span>
          </>
        ),
      }))}
    />
  );

  const readouts = [
    <Chip key="rank" className="arrive flex items-baseline gap-2 px-5 py-2.5" style={{ "--i": 1 } as React.CSSProperties}>
      <PositionBadge
        position="RB"
        rank={season.posRank}
        basis={`by 2025 season points under ${ruleset}`}
        className="font-label text-[1.6rem] leading-none font-bold italic"
      />
      <span className="text-[15px] font-semibold text-ink">{formatPoints(season.points)}</span>
      <span className="text-sm text-mute">pts</span>
    </Chip>,
    <Chip key="overall" className="arrive flex items-center gap-2 px-4 py-2.5 text-sm" style={{ "--i": 2 } as React.CSSProperties}>
      <span className="font-semibold text-ink">{ordinal(season.overallRank)} overall</span>
      <Movement delta={before.overallRank - season.overallRank} baseline={0} />
      <span className="text-mute">
        {before.overallRank === season.overallRank ? `same as ${previous}` : `from ${previous}`}
      </span>
    </Chip>,
    <Chip key="starts" className="arrive px-4 py-2.5 text-sm text-mute" style={{ "--i": 3 } as React.CSSProperties}>
      Top-{STARTERS.RB} RB in <span className="font-semibold text-ink">{starts}</span> of{" "}
      {season.gamesPlayed} weeks
    </Chip>,
  ];

  return (
    <figure className="relative mx-auto w-full max-w-[25rem] lg:mr-6 lg:ml-auto xl:mr-12">
      <figcaption className="sr-only" aria-live="polite">
        Christian McCaffrey&rsquo;s 2025 regular season under {ruleset}: {formatPoints(season.points)}{" "}
        points, running back number {season.posRank}, {ordinal(season.overallRank)} overall, a top-
        {STARTERS.RB} running back in {starts} of {season.gamesPlayed} weeks.
      </figcaption>

      <div className="relative aspect-[21/26] overflow-visible rounded-plate bg-ki shadow-[inset_0_1px_0_rgb(255_255_255/0.35),inset_0_-80px_120px_-60px_rgb(0_0_0/0.18)]">
        {/* Inside the plate on a phone, in the band between the name and the
            bars, so it never floats above an empty screen; straddling the
            plate's left edge from lg, like the other readouts. */}
        <div
          className="arrive absolute inset-x-0 top-[19%] z-10 flex justify-center lg:inset-x-auto lg:top-[21%] lg:-left-14"
          style={{ "--i": 0 } as React.CSSProperties}
        >
          {switcher}
        </div>
        <div className="absolute top-[5.5%] left-[6.7%] text-on-ki">
          <p className="font-label text-[19px] leading-tight font-bold italic">Christian McCaffrey</p>
          <p className="mt-0.5 text-[13px] text-on-ki/75">2025 regular season, San Francisco</p>
        </div>

        {HERO_IMAGE && (
          // A licensed cut-out, if one ever exists, stands in front of the season.
          // eslint-disable-next-line @next/next/no-img-element -- a local, fixed-size cut-out
          <img
            src={HERO_IMAGE.src}
            alt={HERO_IMAGE.alt}
            width={HERO_IMAGE.width}
            height={HERO_IMAGE.height}
            className="absolute right-0 bottom-0 z-[1] h-[82%] w-auto"
          />
        )}

        {/* The chart box: its insets are percentages of the plate, so its
            shape is fixed and the route's viewBox (BOX) matches it exactly. */}
        <div className="absolute top-[34%] right-[6.7%] bottom-[5.4%] left-[6.7%]">
          <div aria-hidden className="absolute inset-0 flex items-end" onMouseLeave={() => setHover(null)}>
            {SLOTS.map((week, i) => {
              const v = byWeek.get(week)?.points;
              return (
                <div
                  key={week}
                  className="relative flex h-full flex-1 items-end justify-center"
                  onMouseEnter={() => setHover(v === undefined ? null : week)}
                >
                  {v === undefined ? (
                    <span className="mb-0 h-0.5 w-2 bg-on-ki/30" />
                  ) : (
                    // Full height, cut down to the week's value with clip-path: a
                    // switch then repaints without re-laying-out the plate, and
                    // the cut keeps the 4px rounded data end. The re-cut eases
                    // only for a visitor who has not asked for less motion.
                    <span
                      className="rise block h-full w-[58%] max-w-6 bg-on-ki/85 hover:bg-on-ki motion-safe:transition-[clip-path,background-color] motion-safe:duration-500 motion-safe:ease-out"
                      style={
                        {
                          clipPath: `inset(${100 - (Math.max(0, v) / SCALE) * 100}% 0 0 0 round 4px 4px 0 0)`,
                          "--i": i,
                        } as React.CSSProperties
                      }
                    />
                  )}
                </div>
              );
            })}
          </div>
          <SeasonRoute
            key={drawn}
            values={values}
            max={SCALE}
            width={BOX.w}
            height={BOX.h}
            lead={130}
            arrow
            glow
            draw
            drawDelay={drawn === 0 ? 780 : 60}
            drawDuration={drawn === 0 ? 1100 : 650}
          />
          {shown && (
            <div
              aria-hidden
              className="pointer-events-none absolute z-10 w-max -translate-x-1/2 -translate-y-full rounded-control bg-lift px-2.5 py-1.5 text-[12px] shadow-float"
              style={{
                left: `${((shown.week - 0.5) / SLOTS.length) * 100}%`,
                // Above the bar, but never up into the switch: a tall week's
                // readout sits over its own bar instead.
                bottom: `calc(${Math.min((Math.max(0, shown.points) / SCALE) * 100, 78)}% + 10px)`,
              }}
            >
              <span className="tabular font-semibold text-ink">{formatPoints(shown.points)}</span>
              <span className="text-mute">
                {" "}
                in week {shown.week}
                {shown.posRank !== null && `, RB${shown.posRank}`}
              </span>
            </div>
          )}
        </div>
      </div>

      {HERO_IMAGE && <p className="mt-2 text-right text-xs text-faint">{HERO_IMAGE.credit}</p>}

      <div className="mt-3 flex flex-wrap justify-center gap-2 lg:contents">
        {/* The two lower readouts straddle the plate's bottom edge: the chart
            keeps a margin there, so under every ruleset they cover no week. */}
        <div className="lg:absolute lg:top-[7%] lg:-right-5 lg:z-10 xl:-right-8 2xl:-right-12">{readouts[0]}</div>
        <div className="lg:absolute lg:-bottom-6 lg:-left-14 lg:z-10">{readouts[1]}</div>
        <div className="lg:absolute lg:-right-5 lg:-bottom-6 lg:z-10 xl:-right-8 2xl:-right-16">{readouts[2]}</div>
      </div>
    </figure>
  );
}
