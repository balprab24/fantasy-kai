"use client";

import { useState } from "react";
import { STARTERS, formatPoints } from "@/lib/board";
import { matchup } from "@/lib/player";
import { weekSlots } from "@/lib/playerView";
import type { CareerSeason, CareerWeek, Position } from "@/lib/types";

/** A round axis step: 5, 10, 20, 25, 50... whichever gives three to five lines. */
function niceStep(span: number) {
  const raw = span / 4;
  const pow = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
  return step;
}

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

/**
 * Fantasy points by week, one season.
 *
 * Built from plain elements rather than SVG: a column chart is a row of boxes
 * with heights, and boxes lay themselves out at any width without measuring.
 *
 * Two fills and only two, each named in the legend: a week inside the
 * position's starter line that week (`STARTERS`, the same 12-team line the
 * board colours per-game figures against) and every other week. The weekly
 * rank behind it comes from the API; this component draws, it does not score.
 *
 * Every regular-season week from 1 to the last one played has a slot, and the
 * two kinds of "nothing" never look alike: a week with no game recorded is a
 * dashed, empty slot (a bye, an injury, a scratch -- the data does not say
 * which, so the chart does not either), while a game that scored zero is a
 * game -- a ring on the zero line, with its figure. One Tab stop: arrow keys
 * walk the weeks, Home and End jump, and focus shows the same readout hover
 * does. The game log is the table view.
 */
export function WeeklyChart({
  season,
  position,
  scoringLabel,
  stale = false,
  caption = true,
}: {
  season: CareerSeason;
  position: string;
  scoringLabel: string;
  stale?: boolean;
  /** The sentence over the chart; off where the panel's heading already says it. */
  caption?: boolean;
}) {
  const line = Object.hasOwn(STARTERS, position) ? STARTERS[position as Position] : undefined;
  const slots = weekSlots(season);
  const lastWeek = slots.length;

  const values = season.weeks.map((w) => w.points);
  const high = Math.max(...values);
  const low = Math.min(...values);
  // A 10-point floor: a quiet season still gets a readable axis, not one
  // drawn in quarter points.
  const ceiling = Math.max(high, season.pointsPerGame, 10);
  const step = niceStep(ceiling - Math.min(0, low));
  const top = Math.ceil(ceiling / step) * step;
  const bottom = Math.floor(Math.min(0, low) / step) * step;
  const span = top - bottom;
  const y = (v: number) => ((v - bottom) / span) * 100; // % from the bottom
  const ticks: number[] = [];
  for (let t = bottom; t <= top + 1e-9; t += step) ticks.push(t);

  const highWeek = high > 0 ? season.weeks.find((w) => w.points === high)?.week : undefined;
  const hasGap = slots.some((s) => s.game === null);
  const hasZero = season.weeks.some((w) => w.points === 0);
  const [active, setActive] = useState<number | null>(null);
  const [focusWeek, setFocusWeek] = useState(slots[0]?.week ?? 1);
  const shownSlot = active !== null ? slots[active - 1] ?? null : null;
  const shown = shownSlot?.game ?? null;

  const starter = (w: CareerWeek) => line !== undefined && w.posRank !== null && w.posRank <= line;
  const describe = (w: CareerWeek) =>
    `Week ${w.week}, ${matchup(w.opponent, w.home)}: ${formatPoints(w.points)} points` +
    (w.posRank !== null ? `, ${ordinal(w.posRank)} among ${position}s that week` : "");

  const move = (to: number) => {
    const clamped = Math.max(1, Math.min(lastWeek, to));
    setFocusWeek(clamped);
    setActive(clamped);
    document.getElementById(`wk-${season.season}-${clamped}`)?.focus();
  };

  return (
    <figure className={`transition-opacity ${stale ? "opacity-60" : ""}`}>
      <figcaption className="flex flex-wrap items-center justify-between gap-x-5 gap-y-2">
        {caption && (
          <span className="text-sm text-mute">
            Fantasy points by week, {season.season} regular season, {scoringLabel}
          </span>
        )}
        <span className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-mute">
          {line !== undefined && (
            <>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[2px] bg-chart-starter" />
                Top-{line} {position} week
              </span>
              <span className="inline-flex items-center gap-1.5">
                <span aria-hidden className="size-2.5 rounded-[2px] bg-chart-rest" />
                Other week
              </span>
            </>
          )}
          {hasZero && (
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden className="size-2.5 rounded-full border-2 border-chart-rest" />
              Zero points
            </span>
          )}
          {hasGap && (
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden className="h-3 w-2.5 rounded-[2px] border border-dashed border-line-strong" />
              No game recorded
            </span>
          )}
          <span className="inline-flex items-center gap-1.5">
            <span aria-hidden className="w-3 border-t border-dashed border-ink" />
            Season average
          </span>
        </span>
      </figcaption>

      <div className="mt-4 flex gap-2">
        {/* Y axis: round values only, recessive. */}
        <div aria-hidden className="relative h-[180px] w-7 shrink-0 sm:h-[220px]">
          {ticks.map((t) => (
            <span
              key={t}
              className="tabular absolute right-0 translate-y-1/2 text-[11px] text-faint"
              style={{ bottom: `${y(t)}%` }}
            >
              {t}
            </span>
          ))}
        </div>

        <div className="relative min-w-0 flex-1">
          <div className="relative h-[180px] sm:h-[220px]">
            {ticks.map((t) => (
              <span
                key={t}
                aria-hidden
                className={`absolute inset-x-0 h-px ${t === 0 ? "bg-line-strong/70" : "bg-line"}`}
                style={{ bottom: `${y(t)}%` }}
              />
            ))}
            {/* Season average, labelled in text so it never relies on its colour. */}
            <span
              aria-hidden
              className="pointer-events-none absolute right-10 left-0 z-[1] border-t border-dashed border-ink/70"
              style={{ bottom: `${y(season.pointsPerGame)}%` }}
            >
              <span className="tabular absolute top-0 left-full -translate-y-1/2 pl-1.5 text-[11px] leading-[1.15] text-ink">
                avg
                <br />
                {formatPoints(season.pointsPerGame)}
              </span>
            </span>

            <ol
              aria-label={`Fantasy points by week, ${season.season}. Use the arrow keys to move between weeks.`}
              className="absolute inset-y-0 right-10 left-0 flex"
              onMouseLeave={() => setActive(null)}
            >
              {slots.map(({ week, game: w }) => {
                const positive = w ? w.points >= 0 : true;
                const barBottom = w ? y(Math.min(0, w.points)) : 0;
                const barHeight = w ? Math.abs(y(w.points) - y(0)) : 0;
                return (
                  <li key={week} className="relative flex-1">
                    <button
                      id={`wk-${season.season}-${week}`}
                      type="button"
                      tabIndex={week === focusWeek ? 0 : -1}
                      aria-label={w ? describe(w) : `Week ${week}: no game recorded`}
                      onMouseEnter={() => setActive(week)}
                      onFocus={() => {
                        // Focus by pointer moves the roving stop too, so Tab
                        // returns to the week last touched, not to week 1.
                        setActive(week);
                        setFocusWeek(week);
                      }}
                      onBlur={() => setActive(null)}
                      onKeyDown={(e) => {
                        if (e.key === "ArrowRight") move(week + 1);
                        else if (e.key === "ArrowLeft") move(week - 1);
                        else if (e.key === "Home") move(1);
                        else if (e.key === "End") move(lastWeek);
                        else return;
                        e.preventDefault();
                      }}
                      className={`group absolute inset-0 cursor-default rounded-[4px] transition-colors ${active === week ? "bg-highlight" : ""}`}
                    >
                      {w && w.points === 0 ? (
                        <span
                          aria-hidden
                          className="absolute left-1/2 size-2.5 -translate-x-1/2 translate-y-1/2 rounded-full border-2 border-chart-rest bg-surface"
                          style={{ bottom: `${y(0)}%` }}
                        />
                      ) : w ? (
                        <span
                          className={`absolute left-1/2 w-[58%] max-w-7 -translate-x-1/2 transition-[filter] group-hover:brightness-125 group-focus-visible:brightness-125 ${
                            positive ? "rounded-t-[4px]" : "rounded-b-[4px]"
                          } ${starter(w) ? "bg-chart-starter" : "bg-chart-rest"}`}
                          style={{
                            bottom: `${barBottom}%`,
                            height: `max(${barHeight}%, 2px)`,
                          }}
                        />
                      ) : (
                        <span
                          aria-hidden
                          className="absolute left-1/2 h-5 w-[58%] max-w-7 -translate-x-1/2 rounded-t-[4px] border border-b-0 border-dashed border-line-strong"
                          style={{ bottom: `${y(0)}%` }}
                        />
                      )}
                      {w && week === highWeek && (
                        <span
                          aria-hidden
                          className="tabular absolute left-1/2 -translate-x-1/2 text-[11px] font-semibold whitespace-nowrap text-ink"
                          style={{ bottom: `calc(${y(w.points)}% + 4px)` }}
                        >
                          {formatPoints(w.points)}
                        </span>
                      )}
                    </button>
                  </li>
                );
              })}
            </ol>

            {shownSlot && (
              // Hidden from assistive tech: each week's button already says all of
              // this in its label, and announcing it twice is noise.
              <div
                aria-hidden
                className="pointer-events-none absolute top-0 z-10 w-max max-w-56 -translate-x-1/2 rounded-control border border-line bg-lift px-3 py-2 text-[13px] shadow-float"
                style={{
                  left: `clamp(4.5rem, calc((100% - 2.5rem) * ${(shownSlot.week - 0.5) / lastWeek}), calc(100% - 4.5rem))`,
                }}
              >
                {shown ? (
                  <>
                    {/* Values lead, labels follow: the reader already has the week. */}
                    <p className="tabular text-ink">
                      <span className="font-semibold">{formatPoints(shown.points)}</span> pts
                      {shown.posRank !== null && (
                        <span className="text-mute">
                          , {position}
                          {shown.posRank} that week
                        </span>
                      )}
                    </p>
                    <p className="mt-0.5 text-mute">
                      Week {shown.week}, {matchup(shown.opponent, shown.home)}
                    </p>
                    {shown.posRank !== null && line !== undefined && (
                      <p className="mt-0.5 text-mute">
                        {starter(shown) ? `Inside the top ${line}` : `Outside the top ${line}`}
                      </p>
                    )}
                  </>
                ) : (
                  <>
                    <p className="text-ink">Week {shownSlot.week}</p>
                    <p className="mt-0.5 text-mute">No game recorded</p>
                  </>
                )}
              </div>
            )}
          </div>

          {/* X axis: week numbers, the one label every slot carries -- at full
              faint, played or not. */}
          <ol aria-hidden className="mt-2 mr-10 flex">
            {slots.map(({ week }) => (
              <li
                key={week}
                // On a phone eighteen two-digit labels run into each other, so
                // every other one steps back; the slots themselves all stay.
                className={`tabular flex-1 text-center text-[11px] text-faint ${week % 2 === 0 && lastWeek > 10 ? "max-sm:invisible" : ""}`}
              >
                {week}
              </li>
            ))}
          </ol>
        </div>
      </div>
    </figure>
  );
}
