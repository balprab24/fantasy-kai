"use client";

import { useId, useState } from "react";
import { STARTERS, formatPoints } from "@/lib/board";
import { matchup } from "@/lib/player";
import { lastGames, weekSlots, type ChartGame } from "@/lib/playerView";
import type { CareerSeason, CareerWeek, Position } from "@/lib/types";
import { SegmentedControl } from "../ui/SegmentedControl";

/** A round axis step: 5, 10, 20, 25, 50... whichever gives three to five lines. */
function niceStep(span: number) {
  const raw = span / 4;
  const pow = 10 ** Math.floor(Math.log10(raw));
  return [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= raw) ?? 10 * pow;
}

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

/** "Last 17" across seasons, or one season. */
export type ChartPeriod = "last" | number;
const LAST = 17;
const SEASON_CHOICES = 4;

/**
 * Fantasy points game by game: his last 17 regular-season games across
 * seasons, or any one season week by week. Every bar carries its figure.
 *
 * Built from plain elements rather than SVG: a column chart is a row of boxes
 * with heights, and boxes lay themselves out at any width without measuring.
 *
 * Two fills and only two, named in the legend: a game inside the position's
 * starter line that week (`STARTERS`, the 12-team line the board colours
 * per-game figures against) and every other game. The weekly rank behind it
 * is the API's; this component draws, it does not score. The one figure it
 * works out is the average line over the last 17, the mean of the 17 figures
 * on screen; a season's line is the API's own points per game.
 *
 * In one season every week from 1 to the last one played has a slot, and the
 * two kinds of "nothing" never look alike: a week with no game recorded is a
 * dashed, empty slot (a bye, an injury, a scratch -- the data does not say
 * which), while a game that scored zero is a ring on the zero line. Across
 * seasons only games take a slot, and a rule marks where one season ends.
 * One Tab stop: arrow keys walk the games, Home and End jump, and focus shows
 * the same readout hover does. The game log is the table view.
 */
export function PerformanceChart({
  seasons,
  position,
  scoringLabel,
  stale = false,
  periods = true,
  initial = "last",
}: {
  /** The career's regular seasons, newest first. */
  seasons: CareerSeason[];
  position: string;
  scoringLabel: string;
  stale?: boolean;
  /** The Last 17 / season switch; off where one season is all there is to show. */
  periods?: boolean;
  initial?: ChartPeriod;
}) {
  const chartId = useId();
  const [period, setPeriod] = useState<ChartPeriod>(initial);
  const one = typeof period === "number" ? (seasons.find((s) => s.season === period) ?? null) : null;
  const slots: ChartGame[] =
    period === "last" ? lastGames(seasons, LAST) : one ? weekSlots(one).map((s) => ({ season: one.season, ...s })) : [];
  const games = slots.flatMap((s) => (s.game ? [s.game] : []));

  const line = Object.hasOwn(STARTERS, position) ? STARTERS[position as Position] : undefined;
  const average = one ? one.pointsPerGame : games.length ? games.reduce((t, g) => t + g.points, 0) / games.length : 0;
  const values = games.map((g) => g.points);
  const high = values.length ? Math.max(...values) : 0;
  const low = values.length ? Math.min(...values) : 0;
  // A 10-point floor: a quiet stretch still gets a readable axis.
  const ceiling = Math.max(high, average, 10);
  const step = niceStep(ceiling - Math.min(0, low));
  const top = Math.ceil(ceiling / step) * step;
  const bottom = Math.floor(Math.min(0, low) / step) * step;
  const y = (v: number) => ((v - bottom) / (top - bottom)) * 100; // % from the bottom
  const ticks: number[] = [];
  for (let t = bottom; t <= top + 1e-9; t += step) ticks.push(t);

  const hasGap = slots.some((s) => s.game === null);
  const hasZero = games.some((g) => g.points === 0);
  const [active, setActive] = useState<number | null>(null);
  const [focusAt, setFocusAt] = useState(0);
  const shown = active !== null ? (slots[active] ?? null) : null;
  // Where a new season starts inside the last 17.
  const breaks = slots.map((s, i) => i > 0 && s.season !== slots[i - 1].season);
  const highAt = values.length ? slots.findIndex((s) => s.game?.points === high) : -1;

  const starter = (w: CareerWeek) => line !== undefined && w.posRank !== null && w.posRank <= line;
  const describe = (s: ChartGame) =>
    s.game
      ? `${period === "last" ? `${s.season}, ` : ""}week ${s.week}, ${matchup(s.game.opponent, s.game.home)}: ${formatPoints(s.game.points)} points` +
        (s.game.posRank !== null ? `, ${ordinal(s.game.posRank)} among ${position}s that week` : "")
      : `Week ${s.week}: no game recorded`;

  const move = (to: number) => {
    const i = Math.max(0, Math.min(slots.length - 1, to));
    setFocusAt(i);
    setActive(i);
    document.getElementById(`${chartId}-${i}`)?.focus();
  };

  // Last 17 and his four latest seasons: enough to compare this year with
  // recent ones, few enough for one row on a phone. Every season is in the
  // career table below.
  const choices = [
    { value: "last", label: `Last ${LAST}` },
    ...seasons.slice(0, SEASON_CHOICES).map((s) => ({ value: String(s.season), label: String(s.season) })),
  ];

  return (
    <figure className={`transition-opacity ${stale ? "opacity-60" : ""}`}>
      <figcaption className="flex flex-wrap items-center justify-between gap-x-5 gap-y-3">
        {periods && seasons.length > 0 ? (
          <SegmentedControl
            legend="Games shown"
            hideLegend
            fill
            options={choices}
            value={String(period)}
            onChange={(v) => {
              setPeriod(v === "last" ? "last" : Number(v));
              setActive(null);
              setFocusAt(0);
            }}
          />
        ) : (
          <span />
        )}
        <span className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[12px] text-mute">
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
            {period === "last" ? `${LAST}-game average` : "Season average"}
          </span>
        </span>
      </figcaption>

      {slots.length === 0 ? (
        <p className="py-10 text-[14px] text-mute">No regular-season games to chart.</p>
      ) : (
        <div className="mt-5 flex gap-2">
          {/* Y axis: round values only, recessive. */}
          <div aria-hidden className="relative h-[200px] w-7 shrink-0 sm:h-[240px]">
            {ticks.map((t) => (
              <span key={t} className="tabular absolute right-0 translate-y-1/2 text-[11px] text-faint" style={{ bottom: `${y(t)}%` }}>
                {t}
              </span>
            ))}
          </div>

          <div className="relative min-w-0 flex-1">
            <div className="relative h-[200px] sm:h-[240px]">
              {ticks.map((t) => (
                <span
                  key={t}
                  aria-hidden
                  className={`absolute inset-x-0 h-px ${t === 0 ? "bg-line-strong/70" : "bg-line"}`}
                  style={{ bottom: `${y(t)}%` }}
                />
              ))}
              {/* The average, labelled in text so it never relies on its colour. */}
              <span
                aria-hidden
                className="pointer-events-none absolute right-10 left-0 z-[1] border-t border-dashed border-ink/60"
                style={{ bottom: `${y(average)}%` }}
              >
                <span className="tabular absolute top-0 left-full -translate-y-1/2 pl-1.5 text-[11px] leading-[1.15] text-ink">
                  avg
                  <br />
                  {formatPoints(average)}
                </span>
              </span>

              <ol
                aria-label={`Fantasy points by game, ${period === "last" ? `his last ${slots.length} regular-season games` : `${period} regular season`}, ${scoringLabel}. Use the arrow keys to move between games.`}
                className="absolute inset-y-0 right-10 left-0 flex"
                onMouseLeave={() => setActive(null)}
              >
                {slots.map((s, i) => {
                  const w = s.game;
                  const barBottom = w ? y(Math.min(0, w.points)) : 0;
                  const barHeight = w ? Math.abs(y(w.points) - y(0)) : 0;
                  return (
                    <li key={`${s.season}-${s.week}`} className={`relative flex-1 ${breaks[i] ? "border-l border-dashed border-line-strong/60" : ""}`}>
                      {breaks[i] && (
                        <span aria-hidden className="tabular absolute -top-1 left-1 -translate-y-full text-[10px] whitespace-nowrap text-faint">
                          {s.season}
                        </span>
                      )}
                      {breaks[i] && (
                        <span aria-hidden className="tabular absolute -top-1 right-full mr-1 -translate-y-full text-[10px] whitespace-nowrap text-faint">
                          {slots[i - 1].season}
                        </span>
                      )}
                      <button
                        id={`${chartId}-${i}`}
                        type="button"
                        tabIndex={i === focusAt ? 0 : -1}
                        aria-label={describe(s)}
                        onMouseEnter={() => setActive(i)}
                        onFocus={() => {
                          setActive(i);
                          setFocusAt(i);
                        }}
                        onBlur={() => setActive(null)}
                        onKeyDown={(e) => {
                          if (e.key === "ArrowRight") move(i + 1);
                          else if (e.key === "ArrowLeft") move(i - 1);
                          else if (e.key === "Home") move(0);
                          else if (e.key === "End") move(slots.length - 1);
                          else return;
                          e.preventDefault();
                        }}
                        className={`group absolute inset-0 cursor-default rounded-[4px] transition-colors ${active === i ? "bg-highlight" : ""}`}
                      >
                        {w && w.points === 0 ? (
                          <span
                            aria-hidden
                            className="absolute left-1/2 size-2.5 -translate-x-1/2 translate-y-1/2 rounded-full border-2 border-chart-rest bg-surface"
                            style={{ bottom: `${y(0)}%` }}
                          />
                        ) : w ? (
                          <span
                            className={`absolute left-1/2 w-[46%] max-w-5 -translate-x-1/2 transition-[filter] group-hover:brightness-125 group-focus-visible:brightness-125 ${
                              w.points >= 0 ? "rounded-t-[3px]" : "rounded-b-[3px]"
                            } ${starter(w) ? "bg-chart-starter" : "bg-chart-rest"}`}
                            style={{ bottom: `${barBottom}%`, height: `max(${barHeight}%, 2px)` }}
                          />
                        ) : (
                          <span
                            aria-hidden
                            className="absolute left-1/2 h-5 w-[46%] max-w-5 -translate-x-1/2 rounded-t-[3px] border border-b-0 border-dashed border-line-strong"
                            style={{ bottom: `${y(0)}%` }}
                          />
                        )}
                        {w && (
                          <span
                            aria-hidden
                            // Every bar's figure from sm; on a phone only the best game's, where
                            // seventeen labels would run into each other.
                            className={`tabular absolute left-1/2 -translate-x-1/2 text-[10px] font-semibold whitespace-nowrap ${
                              starter(w) ? "text-q-good" : "text-mute"
                            } ${i === highAt ? "" : "max-sm:hidden"}`}
                            style={{ bottom: `calc(${y(Math.max(0, w.points))}% + 3px)` }}
                          >
                            {formatPoints(w.points)}
                          </span>
                        )}
                      </button>
                    </li>
                  );
                })}
              </ol>

              {shown && (
                // Hidden from assistive tech: each game's button already says all of
                // this in its label.
                <div
                  aria-hidden
                  className="pointer-events-none absolute top-0 z-10 w-max max-w-56 -translate-x-1/2 rounded-control border border-line bg-lift px-3 py-2 text-[13px] shadow-float"
                  style={{
                    left: `clamp(4.5rem, calc((100% - 2.5rem) * ${(active! + 0.5) / slots.length}), calc(100% - 4.5rem))`,
                  }}
                >
                  {shown.game ? (
                    <>
                      <p className="tabular text-ink">
                        <span className="font-semibold">{formatPoints(shown.game.points)}</span> pts
                        {shown.game.posRank !== null && (
                          <span className="text-mute">
                            , {position}
                            {shown.game.posRank} that week
                          </span>
                        )}
                      </p>
                      <p className="mt-0.5 text-mute">
                        {shown.season} week {shown.week}, {matchup(shown.game.opponent, shown.game.home)}
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="text-ink">Week {shown.week}</p>
                      <p className="mt-0.5 text-mute">No game recorded</p>
                    </>
                  )}
                </div>
              )}
            </div>

            {/* X axis: week numbers. */}
            <ol aria-hidden className="mt-2 mr-10 flex">
              {slots.map((s, i) => (
                <li
                  key={`${s.season}-${s.week}`}
                  className={`tabular flex-1 text-center text-[11px] text-faint ${i % 2 === 1 && slots.length > 10 ? "max-sm:invisible" : ""}`}
                >
                  {s.week}
                </li>
              ))}
            </ol>
          </div>
        </div>
      )}
    </figure>
  );
}
