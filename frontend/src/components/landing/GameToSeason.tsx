"use client";

import { useState } from "react";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints } from "@/lib/board";
import { catchCaps } from "@/lib/heroBoard";
import { matchup } from "@/lib/player";
import type { StatKey } from "@/lib/types";
import {
  MCCAFFREY_2025_BY_RULESET,
  MCCAFFREY_2025_PPR,
  MCCAFFREY_2025_WEEK7,
  MCCAFFREY_2025_WEEK7_RECEIPTS,
  PRESET_RATES,
  SF_2025_BYE_WEEK,
  type Ruleset,
} from "./previewData";

/** A stat's name in a sentence, singular and plural. */
const WORDS: Partial<Record<StatKey, [string, string]>> = {
  pass_yd: ["passing yard", "passing yards"],
  pass_td: ["passing touchdown", "passing touchdowns"],
  rush_yd: ["rushing yard", "rushing yards"],
  rush_td: ["rushing touchdown", "rushing touchdowns"],
  rec: ["catch", "catches"],
  rec_yd: ["receiving yard", "receiving yards"],
  rec_td: ["receiving touchdown", "receiving touchdowns"],
};

/** The presets in the control's order, low to high by what a catch is worth: the one rate they disagree on (V3). */
const RULESETS: Ruleset[] = MCCAFFREY_2025_BY_RULESET.map((s) => s.ruleset);

/** Weeks 1-18 of the 2025 regular season, so a week without a game is a visible gap. */
const SLOTS = Array.from({ length: 18 }, (_, i) => i + 1);

/** One scale for every rate, so a switch shows the change instead of rescaling it away. */
const SCALE = 40;

const ZERO = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === "0 PPR")!;

/**
 * Each week's cap at a point a catch, the tallest it gets. A cap is laid out at
 * this height and scaled down from its foot for a lower rate, so a switch moves
 * eighteen bars by transform rather than by re-laying them out.
 */
const FULL_CAP = new Map(
  catchCaps(ZERO.weeks, MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === "PPR")!.weeks).map((w) => [w.week, w.cap]),
);

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

const pct = (points: number) => `${(Math.max(0, points) / SCALE) * 100}%`;

/**
 * The hero's number taken apart, the way the board makes it, and the one rate
 * the presets disagree on handed to the visitor: what their league pays for a
 * catch. One game is an equation -- the yards and touchdowns, which every
 * preset scores alike, plus the catches times that rate -- and the season is
 * every game scored the same way and added up, never one score over a
 * season's summed stats, because a yardage bonus belongs to the game that
 * earned it.
 *
 * Each week is a bar in two parts: slate for what the rate does not touch (the
 * week under 0 PPR) and a blue cap for what catches add at the chosen rate
 * (`catchCaps`). Set it to 0, 0.5 or 1 and the equation's term, the game,
 * every cap and the season move together, all from captures of each preset
 * (`previewData.ts` checks as it loads that each line times its rates gives
 * that preset's points). The other rates are the same in all three presets,
 * so they are printed, not offered.
 */
export function GameToSeason() {
  const [ruleset, setRuleset] = useState<Ruleset>("PPR");
  const game = MCCAFFREY_2025_WEEK7;
  const s = game.stats;
  const { lines } = MCCAFFREY_2025_WEEK7_RECEIPTS[ruleset];
  const catches = lines.find((l) => l.stat === "rec")!;
  const fixed = lines.filter((l) => l.stat !== "rec");
  const season = MCCAFFREY_2025_BY_RULESET.find((x) => x.ruleset === ruleset)!;
  const weeks = new Map(catchCaps(ZERO.weeks, season.weeks).map((w) => [w.week, w]));
  const fromCatches = season.points - ZERO.points;
  // Opponents are the same under every ruleset; the career capture carries them.
  const where = new Map(MCCAFFREY_2025_PPR.weeks.map((w) => [w.week, matchup(w.opponent, w.home)]));

  return (
    <div className="min-w-0">
      {/* The rate, said as the sentence a league's settings page would say it. */}
      <div className="flex flex-wrap items-center gap-x-4 gap-y-3">
        <p className="text-[20px] font-semibold text-ink sm:text-[22px]">Your league pays</p>
        <div className="flex items-center gap-x-4">
          <div className="w-[13.5rem] sm:w-[15rem]">
            <SegmentedControl
              legend="Points your league pays for a catch"
              hideLegend
              variant="stops"
              value={ruleset}
              onChange={(v) => setRuleset(v as Ruleset)}
              options={RULESETS.map((r) => ({
                value: r,
                label: PRESET_RATES[r].rec,
                className: "type-stat min-h-12 items-center justify-center text-[1.375rem]",
              }))}
            />
          </div>
          <p className="text-[20px] font-semibold text-ink sm:text-[22px]">a catch</p>
        </div>
      </div>
      <p className="mt-2.5 text-[13px] text-mute">
        That is the <span className="font-semibold text-ink">{ruleset}</span> preset.
      </p>

      {/* One game, as an equation: what the rate does not touch, plus what it does. */}
      <div className="mt-10 sm:mt-12">
        <p className="max-w-[40rem] text-[15px] leading-relaxed text-pretty text-mute">
          <span className="font-semibold text-ink">Christian McCaffrey, week {game.week}</span>, at home to
          Atlanta: {game.usage.rushAtt} carries for {s.rush_yd} yards and {s.rush_td} touchdowns, {s.rec} catches
          for {s.rec_yd} yards.
        </p>
        {/* One line from sm, each term over its name. A phone writes it as a column sum -- 32.1, + 7 x 1,
            = 39.1 -- each term beside its name, because names under a one-line equation that narrow run
            into one another. Every cell is placed explicitly for both. */}
        <div
          aria-hidden
          className="mt-5 grid w-fit max-w-full grid-cols-[auto_auto_auto] items-baseline gap-x-3 gap-y-1 sm:grid-cols-[repeat(5,auto)] sm:gap-x-5 sm:gap-y-1.5"
        >
          <span className="type-stat col-start-2 row-start-1 text-[2rem] text-ink sm:col-start-1 sm:text-[2.75rem]">
            {formatPoints(game.points["0 PPR"])}
          </span>
          <Operator className="col-start-1 row-start-2 sm:col-start-2 sm:row-start-1">+</Operator>
          <span className="type-stat col-start-2 row-start-2 text-[2rem] whitespace-nowrap text-ink sm:col-start-3 sm:row-start-1 sm:text-[2.75rem]">
            {catches.count} <span className="text-mute">&times;</span>{" "}
            <span className="text-energy-text">{catches.rate}</span>
          </span>
          <Operator className="col-start-1 row-start-3 sm:col-start-4 sm:row-start-1">=</Operator>
          <span className="type-display col-start-2 row-start-3 text-[3.25rem] text-ink sm:col-start-5 sm:row-start-1 sm:text-[4.5rem]">
            {formatPoints(game.points[ruleset])}
          </span>

          <Caption className="col-start-3 row-start-1 sm:col-start-1 sm:row-start-2">
            <Swatch className="bg-chart-rest" />
            yards and TDs
          </Caption>
          <Caption className="col-start-3 row-start-2 sm:col-start-3 sm:row-start-2">
            <Swatch className="bg-chart-starter" />
            catches &times; your rate
          </Caption>
          <Caption className="col-start-3 row-start-3 sm:col-start-5 sm:row-start-2">week {game.week} points</Caption>
        </div>
        <p className="sr-only" aria-live="polite">
          Week {game.week} under {ruleset}: {formatPoints(game.points["0 PPR"])} for yards and touchdowns, plus{" "}
          {catches.count} catches at {catches.rate} each, makes {formatPoints(game.points[ruleset])} points.
        </p>
        <p className="mt-5 max-w-[38rem] text-[13px] leading-relaxed text-pretty text-mute">
          The {formatPoints(game.points["0 PPR"])} is the same in every preset:{" "}
          {fixed.map((l, i) => {
            const [one, many] = WORDS[l.stat] ?? [l.stat, l.stat];
            return (
              <span key={l.stat}>
                {i > 0 && (i === fixed.length - 1 ? " and " : ", ")}
                <span className="tabular text-ink">{l.count}</span> {l.count === 1 ? one : many} &times; {l.rate}
              </span>
            );
          })}
          .
        </p>
      </div>

      {/* The season: every game scored the same way, then added up. */}
      <div className="mt-12 sm:mt-14">
        {/* No legend: the equation's captions are its key, in the same two colours. */}
        <p className="text-[15px] text-ink">Every game of his season, scored the same way.</p>

        <div aria-hidden className="mt-4">
          {/* --plot is the bars' own height (the box less its headroom), for the label riding week 7's top. */}
          <div className="flex h-36 border-b border-line-strong pt-7 [--plot:116px] sm:h-44 sm:[--plot:148px]">
            {SLOTS.map((week) => {
              const w = weeks.get(week);
              // Every played week has one: FULL_CAP comes from the same weeks, and catchCaps refuses a mismatch.
              const full = FULL_CAP.get(week) ?? 0;
              const lit = week === game.week;
              const edge = week <= 3 ? "left-0" : week >= 16 ? "right-0" : "left-1/2 -translate-x-1/2";
              return (
                <div key={week} className="group relative flex h-full flex-1 flex-col items-center justify-end">
                  {w ? (
                    <>
                      <span className="relative flex h-full w-[58%] max-w-6 flex-col justify-end">
                        {/* The game the equation scores, its two parts said on its top; the rest are on hover. */}
                        {lit && (
                          <span
                            className="tabular absolute bottom-0 left-1/2 text-[12px] font-semibold whitespace-nowrap text-ink motion-safe:transition-transform motion-safe:duration-[450ms] motion-safe:ease-out"
                            style={{
                              transform: `translate(-50%, calc(-1 * var(--plot) * ${(w.base + w.cap) / SCALE} - 8px))`,
                            }}
                          >
                            {formatPoints(w.base)}
                            {w.cap > 0 && (
                              <>
                                {" "}
                                + <span className="text-energy-text">{formatPoints(w.cap)}</span>
                              </>
                            )}
                          </span>
                        )}
                        <span
                          className="block w-full shrink-0 origin-bottom rounded-t-[4px] bg-chart-starter motion-safe:transition-transform motion-safe:duration-[450ms] motion-safe:ease-out"
                          style={{
                            height: pct(full),
                            marginBottom: full > 0 ? 2 : 0,
                            transform: `scaleY(${full > 0 ? w.cap / full : 0})`,
                          }}
                        />
                        <span
                          className={`block w-full shrink-0 bg-chart-rest motion-safe:transition-[border-radius] motion-safe:duration-[450ms] ${
                            w.cap > 0 ? "rounded-t-none" : "rounded-t-[4px]"
                          }`}
                          style={{ height: pct(w.base) }}
                        />
                      </span>
                      {/* The week, on hover: the two parts and their sum. */}
                      <span
                        className={`pointer-events-none absolute bottom-full z-10 mb-1 hidden rounded-control bg-surface px-2.5 py-1.5 text-[12px] leading-snug whitespace-nowrap text-ink shadow-float group-hover:block ${edge}`}
                      >
                        <span className="font-semibold">
                          Week {week} {where.get(week)}
                        </span>
                        : {formatPoints(w.base + w.cap)}
                        <span className="block text-mute">{formatPoints(w.cap)} from catches</span>
                      </span>
                    </>
                  ) : (
                    <span className="h-px w-2 bg-line-strong" />
                  )}
                </div>
              );
            })}
          </div>
          <div className="mt-1.5 flex">
            {SLOTS.map((week) => (
              <span
                key={week}
                className={`flex-1 text-center text-[11px] ${week === game.week ? "font-semibold text-ink" : "text-faint"}`}
              >
                {week === SF_2025_BYE_WEEK && !weeks.has(week) ? "bye" : week}
              </span>
            ))}
          </div>
        </div>

        <ol className="sr-only">
          {SLOTS.map((week) => {
            const w = weeks.get(week);
            return (
              <li key={week}>
                Week {week}
                {w
                  ? ` ${where.get(week)}: ${formatPoints(w.base + w.cap)} points, ${formatPoints(w.cap)} of them from catches`
                  : week === SF_2025_BYE_WEEK
                    ? ": bye"
                    : ": no game"}
              </li>
            );
          })}
        </ol>

        <div className="mt-6 flex flex-wrap items-end justify-between gap-x-8 gap-y-3">
          <div>
            <p className="text-[15px] text-ink">
              <span className="font-semibold">The season</span>
              <span className="text-mute">
                , {season.gamesPlayed} games: RB{season.posRank}, and{" "}
                {season.overallRank === 1 ? "first" : ordinal(season.overallRank)} on the whole {ruleset} board
              </span>
            </p>
            <p className="mt-1.5 flex items-center gap-2 text-[14px] text-mute">
              <Swatch className="bg-chart-starter" />
              {fromCatches > 0 ? (
                <span>
                  <span className="tabular font-semibold text-ink">{formatPoints(fromCatches)}</span> of it from
                  catches
                </span>
              ) : (
                <span>None of it from catches</span>
              )}
            </p>
          </div>
          {/* Orange read as a figure (ki-text): under PPR, the total the hero shows. */}
          <p aria-live="polite" className="type-display text-[3.5rem] text-ki-text sm:text-[4.75rem]">
            {formatPoints(season.points)}
            <span className="sr-only"> points in the season</span>
          </p>
        </div>
      </div>
    </div>
  );
}

function Operator({ className, children }: { className: string; children: React.ReactNode }) {
  return <span className={`type-stat text-[1.5rem] text-mute sm:text-[2rem] ${className}`}>{children}</span>;
}

/**
 * A term's name: beside it on a phone, under it from sm -- where it has no width of its own, so a
 * long name never pushes the equation's terms apart.
 */
function Caption({ className, children }: { className: string; children: React.ReactNode }) {
  return (
    <span
      className={`flex items-center gap-1.5 text-[12px] leading-tight whitespace-nowrap text-mute sm:w-0 sm:self-start sm:text-[13px] ${className}`}
    >
      {children}
    </span>
  );
}

/** A series' key: the mark's colour beside text in a text colour. */
function Swatch({ className }: { className: string }) {
  return <span aria-hidden className={`inline-block size-2.5 shrink-0 rounded-[2px] ${className}`} />;
}
