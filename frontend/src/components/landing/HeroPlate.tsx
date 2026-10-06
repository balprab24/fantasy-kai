"use client";

import { useState } from "react";
import { Movement } from "@/components/Movement";
import { positionHue } from "@/components/rankings/PositionBadge";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints, starterWeeks, STARTERS } from "@/lib/board";
import { HERO_ROWS, entranceOf, movedBetween, posRankOf, slotOf } from "@/lib/heroBoard";
import { Crossfade } from "./Crossfade";
import { HeroReadout } from "./HeroReadout";
import { HERO_BOARD_2025, MCCAFFREY_2025_BY_RULESET, type Ruleset } from "./previewData";

/** The switch's order: what a catch is worth, low to high. */
const RULESETS: Ruleset[] = MCCAFFREY_2025_BY_RULESET.map((s) => s.ruleset);

/** One row's height, px: the product board's own. */
const ROW = 44;

/** The player the readout follows. */
const FOLLOWED = 14480; // Christian McCaffrey

/** The entrance's story: the board as it stands without the point per catch, then with it. */
const OPENS_ON: Ruleset = "0 PPR";
const LANDS_ON: Ruleset = "PPR";

/** "Christian McCaffrey" -> "C. McCaffrey". */
function shortName(name: string) {
  const [first, ...rest] = name.split(" ");
  return rest.length ? `${first[0]}. ${rest.join(" ")}` : name;
}

/**
 * The hero's focal object: the product itself, in its own dark skin on the
 * daylight page (`.primetime`) -- the top of the real 2025 board, a switch
 * that re-sorts it, and, docked under it like a broadcast lower-third, the
 * player the board follows. The page's argument is "your rules decide the
 * order"; the plate does not say so, it does it. Under 0 PPR the top five are
 * quarterbacks; give a catch a point and McCaffrey, Nacua, Robinson and Gibbs
 * take over.
 *
 * Once, on load, it plays that change: the board stands in 0 PPR order with
 * its 0 PPR figures and the switch on 0 PPR, holds, and travels into PPR order
 * as the switch slides and every figure turns into its PPR value; the moves
 * land last. All of it is CSS keyframes over what the server rendered -- the
 * finished PPR board -- so without the animation (reduced motion, a slow
 * script) the visitor sees that board, never a half-sorted one. After that
 * only the visitor moves it.
 *
 * Every figure is captured (`previewData.ts`); `lib/heroBoard.ts` derives the
 * places, positional ranks and moves, and `npm test` checks both. The rank
 * column does not move: places are fixed and players travel past them, as on
 * a broadcast leaderboard -- which is also why a rank never shows a number
 * from one board beside a name from another.
 */
export function HeroPlate() {
  const [ruleset, setRuleset] = useState<Ruleset>(LANDS_ON);
  const [previous, setPrevious] = useState<Ruleset>(OPENS_ON);
  // Bumped by each switch: the route redraws, and the entrance is over.
  const [switches, setSwitches] = useState(0);
  const entering = switches === 0;

  const choose = (next: string) => {
    if (next === ruleset) return;
    setPrevious(ruleset);
    setRuleset(next as Ruleset);
    setSwitches((n) => n + 1);
  };

  const season = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === ruleset)!;
  const opening = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === OPENS_ON)!;
  const shown = HERO_BOARD_2025.filter((p) => slotOf(p, ruleset) !== null).sort(
    (a, b) => a.by[ruleset].rank - b.by[ruleset].rank,
  );

  return (
    <figure className="primetime relative w-full overflow-hidden rounded-plate bg-canvas text-ink">
      <figcaption className="sr-only" aria-live="polite">
        The top {HERO_ROWS} of the 2025 regular season under {ruleset}, by season points:{" "}
        {shown
          .map((p) => `${p.by[ruleset].rank}, ${p.name}, ${formatPoints(p.by[ruleset].points)}`)
          .join("; ")}
        . Christian McCaffrey under {ruleset}: running back number {season.posRank},{" "}
        {formatPoints(season.points)} points, {formatPoints(season.pointsPerGame)} a game, a top-{STARTERS.RB}{" "}
        running back in {starterWeeks(season.weeks, "RB")} of {season.gamesPlayed} weeks.
      </figcaption>

      {/* The console, as the board has it: the rules first, then the recipe. */}
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2.5 bg-surface px-4 py-3 sm:px-5">
        <SegmentedControl
          legend="Scoring for this board"
          hideLegend
          variant="pill"
          value={ruleset}
          onChange={choose}
          thumbFrom={entering ? RULESETS.indexOf(OPENS_ON) : undefined}
          options={RULESETS.map((r) => ({
            value: r,
            label: r,
            className: entering
              ? r === OPENS_ON
                ? "still-chosen"
                : r === LANDS_ON
                  ? "not-chosen-yet"
                  : ""
              : "",
          }))}
        />
        <p className="text-[13px] text-mute">The top of the 2025 board, by season total.</p>
      </div>

      <div aria-hidden className="px-2 sm:px-3">
        <div className="flex h-9 items-end border-b border-line pb-2 font-label text-[12px] font-semibold text-mute">
          <span className="w-9 shrink-0 pr-3 text-right">#</span>
          <span className="min-w-0 flex-1 pl-2">Player</span>
          <span className="w-11 shrink-0">Pos</span>
          <span className="w-14 shrink-0 text-right">Points</span>
          <span className="w-[4.25rem] shrink-0 pr-2 text-right whitespace-nowrap">vs {previous}</span>
        </div>

        <div className="relative overflow-hidden" style={{ height: HERO_ROWS * ROW }}>
          {/* The places and the rules between rows, fixed: players travel past them. */}
          <ol className="absolute inset-y-0 left-0 w-9">
            {Array.from({ length: HERO_ROWS }, (_, i) => (
              <li
                key={i}
                className="type-rank flex h-11 items-center justify-end border-b border-line pr-3 text-[15px] text-mute"
              >
                {i + 1}
              </li>
            ))}
          </ol>
          <div className="pointer-events-none absolute inset-y-0 right-0 left-9">
            {Array.from({ length: HERO_ROWS }, (_, i) => (
              <div key={i} className="h-11 border-b border-line" />
            ))}
          </div>

          {HERO_BOARD_2025.map((p) => {
            const slot = slotOf(p, ruleset);
            const place = slot ?? HERO_ROWS;
            const start = entranceOf(p, OPENS_ON, LANDS_ON);
            const posRank = slot === null ? null : posRankOf(HERO_BOARD_2025, p, ruleset);
            // What this row said on the board the entrance opens on, if it was on it.
            const wasShown = entering && start.shown;
            const followed = p.playerId === FOLLOWED;
            return (
              <div
                key={p.playerId}
                inert={slot === null}
                // The followed row is lit, not edged: the whole row takes the "you are here" tint.
                className={`absolute inset-x-0 top-0 flex h-11 items-center pl-9 motion-safe:transition-[transform,opacity] motion-safe:duration-700 motion-safe:ease-[cubic-bezier(0.16,1,0.3,1)] ${
                  followed ? "bg-energy/15" : ""
                } ${entering ? "resort" : ""}`}
                style={
                  {
                    transform: `translateY(${place * ROW}px)`,
                    opacity: slot === null ? 0 : 1,
                    "--y0": `${(place + start.rows) * ROW}px`,
                    "--o0": start.shown ? 1 : 0,
                    // A row that neither moves nor leaves does not dim in flight.
                    "--o-mid": start.rows === 0 && start.shown && slot !== null ? 1 : 0.45,
                  } as React.CSSProperties
                }
              >
                <span className="flex min-w-0 flex-1 items-center gap-2 pr-2 pl-2">
                  <span className="truncate text-[15px] font-semibold text-ink">
                    {/* On a phone the first name steps down to its initial, so the surname -- the part people scan for -- is never the part cut off. */}
                    <span className="sm:hidden">{shortName(p.name)}</span>
                    <span className="hidden sm:inline">{p.name}</span>
                  </span>
                  <span className="hidden shrink-0 text-[12px] text-mute sm:inline">{p.team}</span>
                </span>
                <span className="tabular flex w-11 shrink-0 items-center text-[13px] font-semibold">
                  <span className={positionHue(p.position).text}>{p.position}</span>
                  <Crossfade
                    entering={entering}
                    then={wasShown ? posRankOf(HERO_BOARD_2025, p, OPENS_ON) : null}
                    now={posRank ?? ""}
                    className="text-ink"
                  />
                </span>
                <span className="tabular w-14 shrink-0 text-right text-[15px] font-semibold text-ink">
                  <Crossfade
                    entering={entering}
                    align="end"
                    then={wasShown ? formatPoints(p.by[OPENS_ON].points) : null}
                    now={formatPoints(p.by[ruleset].points)}
                  />
                </span>
                <span
                  className={`tabular w-[4.25rem] shrink-0 pr-2 text-right text-[13px] ${entering ? "settle-in" : ""}`}
                >
                  <Movement delta={movedBetween(p, previous, ruleset)} baseline={200} />
                </span>
              </div>
            );
          })}
        </div>
      </div>

      <HeroReadout season={season} opening={entering ? opening : null} switches={switches} />
    </figure>
  );
}
