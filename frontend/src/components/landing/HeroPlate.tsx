"use client";

import { useState } from "react";
import { Movement } from "@/components/Movement";
import { positionHue } from "@/components/rankings/PositionBadge";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints } from "@/lib/board";
import { HERO_SLOTS, HERO_TOP, entranceOf, heroSkips, heroSlotOf, movedBetween, posRankOf } from "@/lib/heroBoard";
import { Crossfade } from "./Crossfade";
import { HERO_IMAGE } from "./heroImage";
import { HERO_BOARD_2025, MCCAFFREY_2025_BY_RULESET, PRESET_RATES, type Ruleset } from "./previewData";

/** The switch's order: what a catch is worth, low to high. */
const RULESETS: Ruleset[] = MCCAFFREY_2025_BY_RULESET.map((s) => s.ruleset);

/** One row's height, px. */
const ROW = 40;

/** The player the plate is about, and the board follows. */
const FOLLOWED = HERO_BOARD_2025.find((p) => p.playerId === 14480)!; // Christian McCaffrey

/** The entrance's story: the board as it stands without the point per catch, then with it. */
const OPENS_ON: Ruleset = "0 PPR";
const LANDS_ON: Ruleset = "PPR";

const RB = positionHue("RB").text;

/** "Christian McCaffrey" -> "C. McCaffrey". */
function shortName(name: string) {
  const [first, ...rest] = name.split(" ");
  return rest.length ? `${first[0]}. ${rest.join(" ")}` : name;
}

/**
 * The hero's focal object, in the product's own dark skin on the daylight
 * page (`.primetime`): one player's season scored by each of the three
 * presets, side by side and big enough to read in a glance -- McCaffrey is
 * 7th under 0 PPR and 1st under Half PPR and PPR, 314.6 points becoming 416.6
 * from the same seventeen games. The three formats are also the switch, and
 * under them sits the top of the board each one produces: five quarterbacks
 * under 0 PPR; McCaffrey, Nacua, Robinson and Gibbs under PPR. The page's
 * argument is "your rules decide the order"; the plate does not say so, it
 * does it.
 *
 * Once, on load, it plays that change: the chosen format stands on 0 PPR and
 * the board in 0 PPR order with its 0 PPR figures, holds, and travels into PPR
 * as the chosen step slides across; the moves land last. All of it is CSS
 * keyframes over what the server rendered -- the finished PPR board -- so
 * without the animation (reduced motion, a slow script) the visitor sees that
 * board, never a half-sorted one. After that only the visitor moves it.
 *
 * Every figure is captured (`previewData.ts`); `lib/heroBoard.ts` derives the
 * slots, positional ranks and moves, and `npm test` checks both. The rank
 * column stands still and players travel past it, as on a broadcast
 * leaderboard, so a rank never shows a number from one board beside a name
 * from another. The board follows McCaffrey: when he is outside its top five
 * (under 0 PPR, 7th) he takes the last slot, under a dashed rule that says
 * places were skipped.
 */
export function HeroPlate() {
  const [ruleset, setRuleset] = useState<Ruleset>(LANDS_ON);
  // Bumped by each switch: once it moves, the entrance is over.
  const [switches, setSwitches] = useState(0);
  const entering = switches === 0;
  // Moves are always counted from the board without a point per catch -- what catches did -- so
  // the same board shows the same moves however the visitor got to it. That board itself has none.
  const moves = ruleset !== OPENS_ON;

  const choose = (next: string) => {
    if (next === ruleset) return;
    setRuleset(next as Ruleset);
    setSwitches((n) => n + 1);
  };

  const shown = HERO_BOARD_2025.filter((p) => heroSlotOf(p, ruleset, FOLLOWED) !== null).sort(
    (a, b) => a.by[ruleset].rank - b.by[ruleset].rank,
  );
  // The last slot's place: the next one down, or McCaffrey's own when the board skips to him.
  const lastPlace = (r: Ruleset) => (heroSkips(FOLLOWED, r) ? FOLLOWED.by[r].rank : HERO_SLOTS);
  const skips = heroSkips(FOLLOWED, ruleset);
  const skippedOnOpening = entering && heroSkips(FOLLOWED, OPENS_ON);

  return (
    <figure className="primetime relative w-full overflow-hidden rounded-plate bg-canvas text-ink">
      <figcaption className="sr-only" aria-live="polite">
        Christian McCaffrey&apos;s 2025 season, scored three ways:{" "}
        {MCCAFFREY_2025_BY_RULESET.map(
          (s) =>
            `under ${s.ruleset}, ${s.overallRank} overall and running back number ${s.posRank}, with ${formatPoints(s.points)} points`,
        ).join("; ")}
        . The top of the 2025 board under {ruleset}:{" "}
        {shown.map((p) => `${p.by[ruleset].rank}, ${p.name}, ${formatPoints(p.by[ruleset].points)}`).join("; ")}.
      </figcaption>

      {/* The player, named the way a broadcast names him. */}
      <div className="relative px-5 pt-5 sm:px-6 sm:pt-6">
        {HERO_IMAGE && (
          // A licensed cut-out, if one ever exists, stands at the head's right edge.
          // eslint-disable-next-line @next/next/no-img-element -- a local, fixed-size cut-out
          <img
            src={HERO_IMAGE.src}
            alt=""
            width={HERO_IMAGE.width}
            height={HERO_IMAGE.height}
            className="pointer-events-none absolute top-0 right-4 h-28 w-auto"
          />
        )}
        <p className="type-display text-[clamp(2.25rem,3.4vw,3.25rem)]">Christian McCaffrey</p>
        <p className="mt-2 text-[14px] text-pretty text-mute">
          <span className={`font-semibold ${RB}`}>Running back</span>, San Francisco.{" "}
          {/* A phone keeps the instruction and drops the restatement, so the three formats stay in the first screen. */}
          <span className="hidden sm:inline">
            His 2025 season scored three ways: pick one and the board under it re-sorts.
          </span>
          <span className="sm:hidden">Pick a format and the board under it re-sorts.</span>
        </p>
        {HERO_IMAGE && <p className="mt-1 text-xs text-faint">{HERO_IMAGE.credit}</p>}
      </div>

      {/* The three formats, each one a figure and all three the switch. */}
      <div className="mt-4 px-3 sm:mt-5 sm:px-4">
        <SegmentedControl
          legend="Scoring format"
          hideLegend
          variant="stops"
          value={ruleset}
          onChange={choose}
          thumbFrom={entering ? RULESETS.indexOf(OPENS_ON) : undefined}
          options={MCCAFFREY_2025_BY_RULESET.map((s, i) => {
            const before = MCCAFFREY_2025_BY_RULESET[i - 1];
            return {
              value: s.ruleset,
              className: "px-3 pt-3 pb-3 sm:px-4 sm:pt-3.5 sm:pb-3.5",
              label: (
                <>
                  <span className="text-[14px] leading-tight font-semibold sm:text-[15px]">{s.ruleset}</span>
                  <span className="text-[12px] leading-tight text-mute">
                    {PRESET_RATES[s.ruleset].rec} per catch
                  </span>
                  <span className="mt-3 flex items-baseline gap-1.5 sm:mt-4">
                    <span className="type-display text-[clamp(2.75rem,4.6vw,4.25rem)] leading-[0.8]">
                      #{s.overallRank}
                    </span>
                    <span className="text-[12px] text-mute">overall</span>
                  </span>
                  <span className="type-stat mt-2.5 text-[1.25rem] sm:text-[1.625rem]">
                    {formatPoints(s.points)}
                    <span className="ml-1 font-sans text-[12px] font-normal text-mute">pts</span>
                  </span>
                  <span className="mt-1.5 font-label text-[13px] font-semibold">
                    <span className={RB}>RB</span>
                    {s.posRank}
                  </span>
                  {/* What the step bought, and from what: the rates section says the same of the season. */}
                  {before && (
                    <span className="mt-1 text-[12px] leading-snug text-pretty text-mute">
                      <span className="tabular font-semibold text-ink">+{formatPoints(s.points - before.points)}</span>{" "}
                      from catches
                    </span>
                  )}
                </>
              ),
            };
          })}
        />
      </div>

      {/* The board each format produces. */}
      <div aria-hidden className="px-2 pt-5 pb-5 sm:px-3">
        <p className="px-2 text-[13px] text-mute">
          {/* Whole sentences, so the one being left and the one arrived at share a width and no gap opens. */}
          <Crossfade
            entering={entering}
            then={entering ? <BoardRecipe ruleset={OPENS_ON} /> : null}
            now={<BoardRecipe ruleset={ruleset} />}
          />
        </p>
        <div className="mt-2 flex h-8 items-end border-b border-line pb-1.5 font-label text-[12px] font-semibold text-mute">
          <span className="w-9 shrink-0 pr-3 text-right">#</span>
          <span className="min-w-0 flex-1 pl-2">Player</span>
          <span className="w-11 shrink-0">Pos</span>
          <span className="w-14 shrink-0 text-right">Points</span>
          <span
            className={`w-[4.25rem] shrink-0 pr-2 text-right whitespace-nowrap ${entering ? "settle-in" : ""} ${moves ? "" : "invisible"}`}
          >
            vs {OPENS_ON}
          </span>
        </div>

        <div className="relative overflow-hidden" style={{ height: HERO_SLOTS * ROW }}>
          {/* The places and the rules between rows, fixed: players travel past them. */}
          <ol className="absolute inset-y-0 left-0 w-9">
            {Array.from({ length: HERO_SLOTS }, (_, i) => (
              <li
                key={i}
                className="type-rank flex items-center justify-end pr-3 text-[15px] text-mute"
                style={{ height: ROW }}
              >
                {i < HERO_TOP ? (
                  i + 1
                ) : (
                  <Crossfade
                    entering={entering}
                    align="end"
                    then={entering ? lastPlace(OPENS_ON) : null}
                    now={lastPlace(ruleset)}
                  />
                )}
              </li>
            ))}
          </ol>
          <div className="pointer-events-none absolute inset-x-0 top-0">
            {Array.from({ length: HERO_SLOTS }, (_, i) => (
              <div key={i} className="relative border-b border-line" style={{ height: ROW }}>
                {/* Under the fifth row, a dashed rule when the last slot skips places to reach McCaffrey. */}
                {i === HERO_TOP - 1 && (
                  <>
                    {skippedOnOpening && <SkipRule className="fig-then opacity-0" />}
                    {skips && <SkipRule className={entering ? "fig-now" : ""} />}
                  </>
                )}
              </div>
            ))}
          </div>

          {HERO_BOARD_2025.map((p) => {
            const slot = heroSlotOf(p, ruleset, FOLLOWED);
            const place = slot ?? HERO_SLOTS;
            const start = entranceOf(heroSlotOf(p, OPENS_ON, FOLLOWED), heroSlotOf(p, LANDS_ON, FOLLOWED), HERO_SLOTS);
            const posRank = slot === null ? null : posRankOf(HERO_BOARD_2025, p, ruleset);
            // What this row said on the board the entrance opens on, if it was on it.
            const wasShown = entering && start.shown;
            const followed = p.playerId === FOLLOWED.playerId;
            return (
              <div
                key={p.playerId}
                inert={slot === null}
                // The followed row is lit, not edged: the whole row takes the "you are here" tint.
                className={`absolute inset-x-0 top-0 flex items-center pl-9 motion-safe:transition-[transform,opacity] motion-safe:duration-700 motion-safe:ease-[cubic-bezier(0.16,1,0.3,1)] ${
                  followed ? "bg-energy/15" : ""
                } ${entering ? "resort" : ""}`}
                style={
                  {
                    height: ROW,
                    transform: `translateY(${place * ROW}px)`,
                    opacity: slot === null ? 0 : 1,
                    "--y0": `${(place + start.rows) * ROW}px`,
                    "--o0": start.shown ? 1 : 0,
                    // In flight the rows step well back, so McCaffrey's climb is the one move that reads;
                    // a row that neither moves nor leaves does not dim.
                    "--o-mid": followed || (start.rows === 0 && start.shown && slot !== null) ? 1 : 0.2,
                  } as React.CSSProperties
                }
              >
                <span className="flex min-w-0 flex-1 items-center gap-2 pr-2 pl-2">
                  <span className={`truncate text-[15px] text-ink ${followed ? "font-semibold" : "font-medium"}`}>
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
                  {moves && <Movement delta={movedBetween(p, OPENS_ON, ruleset)} baseline={200} />}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </figure>
  );
}

/** The rule under the fifth row when places are skipped: dashed, laid over the solid one it replaces. */
function SkipRule({ className }: { className: string }) {
  return (
    <span className={`absolute inset-x-0 -bottom-px h-px bg-canvas ${className}`}>
      <span className="block border-b border-dashed border-line-strong" />
    </span>
  );
}

/** The board's recipe, in a sentence. */
function BoardRecipe({ ruleset }: { ruleset: Ruleset }) {
  return (
    <>
      The top of the 2025 board under <span className="font-semibold text-ink">{ruleset}</span>, by season total.
    </>
  );
}
