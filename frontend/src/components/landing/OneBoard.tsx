"use client";

import { useState } from "react";
import { positionHue } from "@/components/rankings/PositionBadge";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints, toBoardRows } from "@/lib/board";
import { lastPlace, movedBetween, skipsTo, slotOf } from "@/lib/oneBoard";
import {
  HALF_PPR_TOP20_2025,
  ONE_BOARD_2025,
  PPR_2025,
  PRESET_RATES,
  RULESETS,
  ZERO_PPR_TOP20_2025,
  type Ruleset,
} from "./previewData";

/** The places every board shows; one slot more holds the player followed, or the 9th. */
const TOP = 8;
const SLOTS = TOP + 1;

/** One row's height, px: rows travel by it. */
const ROW = 48;

/** The player the board follows. */
const FOLLOWED = ONE_BOARD_2025.find((p) => p.playerId === 16153)!; // Puka Nacua

/** Where the board opens, and what every move is counted from: the board without a point per catch. */
const OPENS_ON: Ruleset = "0 PPR";

/**
 * Each board's positional ranks ("WR1"), counted from rank 1 over the captured
 * top of it -- twenty deep under 0 PPR and Half PPR, sixty under PPR -- which
 * reaches every player the board can show, Nacua at 20th included.
 */
const POS_RANK = Object.fromEntries(
  (
    [
      ["0 PPR", ZERO_PPR_TOP20_2025],
      ["Half PPR", HALF_PPR_TOP20_2025],
      ["PPR", PPR_2025],
    ] as const
  ).map(([r, rows]) => [r, new Map(toBoardRows(rows).map((row) => [row.playerId, row.posRank]))]),
) as Record<Ruleset, Map<number, number | null>>;

/** The board under a ruleset, in words: what the caption says, and what a visitor's own switch announces. */
function summary(ruleset: Ruleset) {
  const shown = ONE_BOARD_2025.filter((p) => slotOf(p, ruleset, FOLLOWED, TOP) !== null).sort(
    (a, b) => a.by[ruleset].rank - b.by[ruleset].rank,
  );
  return `The top of the 2025 board under ${ruleset}: ${shown
    .map((p) => `${p.by[ruleset].rank}, ${p.name}, ${formatPoints(p.by[ruleset].points)}`)
    .join("; ")}.`;
}

/** "Christian McCaffrey" -> "C. McCaffrey". */
function shortName(name: string) {
  const [first, ...rest] = name.split(" ");
  return rest.length ? `${first[0]}. ${rest.join(" ")}` : name;
}

/**
 * One board, three rulesets: the 2025 season's top eight under whichever
 * preset is chosen, and the rows travel to their new places when the choice
 * changes -- the motion is the explanation, so nothing has to be compared
 * across columns. The three presets differ in one rate, what a catch is worth
 * (V3), and the board follows one player through it: Puka Nacua, 20th, then
 * 11th, then 2nd. When he is outside the top eight he takes the last slot,
 * under a dashed rule that says places were skipped; his row is lit, and once
 * he has moved it says how far.
 *
 * It opens on 0 PPR, Nacua 20th under the dashed rule, and waits: the hero
 * above already moves on its own, so this board moves only when the visitor
 * moves it (owner brief 2026-10-07), and each change is announced. 2025, the
 * last full season -- the hero's board is 2026, still being played. In the
 * page's own Daylight skin: the product's dark skin appears twice on the
 * landing, and this is neither (DESIGN.md, the Island Rule). Every figure is captured (`previewData.ts`); `lib/oneBoard.ts`
 * derives the slots, and `npm test` checks both.
 */
export function OneBoard() {
  const [ruleset, setRuleset] = useState<Ruleset>(OPENS_ON);
  // What a screen reader is told when the visitor changes the scoring.
  const [announced, setAnnounced] = useState("");

  const choose = (next: string) => {
    setRuleset(next as Ruleset);
    setAnnounced(summary(next as Ruleset));
  };

  const skips = skipsTo(FOLLOWED, ruleset, TOP);
  const moved = movedBetween(FOLLOWED, OPENS_ON, ruleset);
  const followedSlot = slotOf(FOLLOWED, ruleset, FOLLOWED, TOP);

  return (
    <figure className="min-w-0">
      {/* What the board shows, for anyone who reaches it; the live line below speaks for a change. */}
      <figcaption className="sr-only">{summary(ruleset)}</figcaption>
      <p className="sr-only" aria-live="polite">
        {announced}
      </p>

      <SegmentedControl
        legend="Scoring"
        hideLegend
        variant="stops"
        value={ruleset}
        onChange={choose}
        options={RULESETS.map((r) => ({
          value: r,
          className: "min-h-16 items-start justify-center px-3 sm:px-5",
          label: (
            <>
              <span className="text-[15px] leading-tight font-semibold sm:text-[17px]">{r}</span>
              <span className="mt-0.5 text-[12px] leading-tight text-mute sm:text-[13px]">
                {PRESET_RATES[r].rec} per catch
              </span>
            </>
          ),
        }))}
      />

      <div aria-hidden className="mt-6">
        <p className="text-[14px] text-mute">
          The top of the 2025 board under <span className="font-semibold text-ink">{ruleset}</span>, by season total.
        </p>
        <div className="mt-3 flex h-9 items-end border-b-2 border-ink pb-2 font-label text-[12px] font-semibold text-mute">
          <span className="w-10 shrink-0 pr-3 text-right sm:w-12">#</span>
          <span className="min-w-0 flex-1 pl-1">Player</span>
          <span className="w-14 shrink-0">Pos</span>
          <span className="w-16 shrink-0 text-right">Points</span>
          <span className="w-16 shrink-0 sm:w-24" />
        </div>

        <div className="relative overflow-hidden" style={{ height: SLOTS * ROW }}>
          {/* The rules between places, fixed: players travel past them. */}
          <div className="pointer-events-none absolute inset-x-0 top-0">
            {Array.from({ length: SLOTS }, (_, i) => (
              <div key={i} className="relative border-b border-line" style={{ height: ROW }}>
                {i === TOP - 1 && skips && <SkipRule />}
              </div>
            ))}
          </div>

          {ONE_BOARD_2025.map((p) => {
            const slot = slotOf(p, ruleset, FOLLOWED, TOP);
            const followed = p.playerId === FOLLOWED.playerId;
            const posRank = POS_RANK[ruleset].get(p.playerId);
            return (
              <div
                key={p.playerId}
                inert={slot === null}
                className={`absolute inset-x-0 top-0 flex items-center pl-10 motion-safe:transition-[transform,opacity] motion-safe:duration-700 motion-safe:ease-[cubic-bezier(0.16,1,0.3,1)] sm:pl-12 ${
                  followed ? "bg-lift" : ""
                }`}
                style={{
                  height: ROW,
                  transform: `translateY(${(slot ?? SLOTS) * ROW}px)`,
                  opacity: slot === null ? 0 : 1,
                }}
              >
                <span className="flex min-w-0 flex-1 items-baseline gap-2 pl-1">
                  <span className={`truncate text-[15px] text-ink sm:text-[16px] ${followed ? "font-bold" : "font-semibold"}`}>
                    <span className="sm:hidden">{shortName(p.name)}</span>
                    <span className="hidden sm:inline">{p.name}</span>
                  </span>
                  <span className="hidden shrink-0 text-[13px] text-mute sm:inline">{p.team}</span>
                </span>
                <span className="tabular w-14 shrink-0 text-[13px] font-semibold">
                  {/* No position hue reaches 4.5:1 on lift, so the lit row's letters are ink. */}
                  <span className={followed ? "text-ink" : positionHue(p.position).text}>{p.position}</span>
                  <span className="text-ink">{posRank ?? ""}</span>
                </span>
                <span className="tabular w-16 shrink-0 text-right text-[15px] font-semibold text-ink sm:text-[16px]">
                  {formatPoints(p.by[ruleset].points)}
                </span>
                <span className="w-16 shrink-0 pr-3 text-right text-[13px] font-semibold text-energy-text sm:w-24">
                  {followed && moved > 0 && (
                    <span className="inline-flex items-center gap-1 whitespace-nowrap">
                      <svg aria-hidden width="8" height="8" viewBox="0 0 8 8" className="shrink-0">
                        <path d="M4 1 7.5 7h-7z" fill="currentColor" />
                      </svg>
                      {moved}
                      <span className="hidden sm:inline"> places</span>
                    </span>
                  )}
                </span>
              </div>
            );
          })}

          {/* The places, fixed, above the rows: the lit row's ground never hides its number. */}
          <ol className="pointer-events-none absolute inset-y-0 left-0 w-10 sm:w-12">
            {Array.from({ length: SLOTS }, (_, i) => (
              <li
                key={i}
                className={`type-rank flex items-center justify-end pr-3 text-[15px] ${
                  i === followedSlot ? "font-bold text-energy-text" : "text-mute"
                }`}
                style={{ height: ROW }}
              >
                {i < TOP ? i + 1 : lastPlace(FOLLOWED, ruleset, TOP)}
              </li>
            ))}
          </ol>
        </div>
      </div>
    </figure>
  );
}

/** The rule under the eighth row when places are skipped: dashed, laid over the solid one it replaces. */
function SkipRule() {
  return (
    <span className="absolute inset-x-0 -bottom-px h-px bg-canvas">
      <span className="block border-b border-dashed border-line-strong" />
    </span>
  );
}
