"use client";

import { useState } from "react";
import { SegmentedControl } from "@/components/ui/SegmentedControl";
import { formatPoints } from "@/lib/board";
import { matchup } from "@/lib/player";
import type { StatKey } from "@/lib/types";
import {
  MCCAFFREY_2025_BY_RULESET,
  MCCAFFREY_2025_PPR,
  MCCAFFREY_2025_WEEK7,
  MCCAFFREY_2025_WEEK7_RECEIPTS,
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

/** What a catch is worth under each preset: the one rate they disagree on (V3). */
const PER_CATCH: { value: Ruleset; label: string }[] = [
  { value: "0 PPR", label: "0" },
  { value: "Half PPR", label: "0.5" },
  { value: "PPR", label: "1" },
];

/** Weeks 1-18 of the 2025 regular season, so a week without a game is a visible gap. */
const SLOTS = Array.from({ length: 18 }, (_, i) => i + 1);

function ordinal(n: number) {
  const tens = n % 100;
  if (tens >= 11 && tens <= 13) return `${n}th`;
  return `${n}${({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th"}`;
}

/**
 * The hero's number taken apart, the way the board makes it: one game's box
 * score times the ruleset's rates, then every game of the season scored the
 * same way and added up -- never one score over a season's summed stats,
 * because a yardage bonus belongs to the game that earned it.
 *
 * The catch rate is a real control, the ruleset builder's kind of thing: set
 * it to 0, 0.5 or 1 and the game, all seventeen weeks and the season are
 * re-priced, from captures of each preset (`previewData.ts`, which checks as
 * it loads that each line times its rates gives that preset's points). The
 * other rates are the same in all three presets, so they are printed, not
 * offered.
 */
export function GameToSeason() {
  const [ruleset, setRuleset] = useState<Ruleset>("PPR");
  const game = MCCAFFREY_2025_WEEK7;
  const { lines, total } = MCCAFFREY_2025_WEEK7_RECEIPTS[ruleset];
  const season = MCCAFFREY_2025_BY_RULESET.find((s) => s.ruleset === ruleset)!;
  const byWeek = new Map(season.weeks.map((w) => [w.week, w]));
  // Opponents are the same under every ruleset; the career capture carries them.
  // A week with no game is a bye only when the schedule says so (`SF_2025_BYE_WEEK`).
  const where = new Map(MCCAFFREY_2025_PPR.weeks.map((w) => [w.week, matchup(w.opponent, w.home)]));
  const s = game.stats;

  return (
    <div className="min-w-0">
      <p className="max-w-[40rem] text-[15px] leading-relaxed text-pretty text-mute">
        <span className="font-semibold text-ink">Christian McCaffrey</span>, week {game.week} of {game.season}, at
        home to Atlanta: {game.usage.rushAtt} carries for {s.rush_yd} yards and {s.rush_td} touchdowns,{" "}
        {s.rec} catches for {s.rec_yd} yards.
      </p>

      <div className="mt-6 flex flex-wrap items-end gap-x-5 gap-y-3">
        <SegmentedControl
          legend="Points per catch"
          options={PER_CATCH}
          value={ruleset}
          onChange={(v) => setRuleset(v as Ruleset)}
        />
        <p className="text-[13px] text-mute">
          That is the <span className="font-semibold text-ink">{ruleset}</span> preset.
        </p>
      </div>

      <table className="mt-4 w-full text-[15px]">
        <caption className="sr-only">
          Week {game.week} scored under {ruleset}: each stat, its rate, and the points it earns
        </caption>
        <tbody>
          {lines.map((l) => {
            const [one, many] = WORDS[l.stat] ?? [l.stat, l.stat];
            const rule = l.stat === "rec";
            return (
              <tr key={l.stat} className="[&>*]:border-b [&>*]:border-line">
                <th scope="row" className="py-2.5 pr-4 text-left font-normal text-ink">
                  <span className="tabular font-semibold">{l.count}</span> {l.count === 1 ? one : many}
                </th>
                <td
                  className={`tabular w-24 py-2.5 pr-4 text-right whitespace-nowrap ${rule ? "font-semibold text-energy-text" : "text-mute"}`}
                >
                  <span aria-hidden>&times; </span>
                  <span className="sr-only">times </span>
                  {l.rate}
                </td>
                <td className="tabular w-16 py-2.5 text-right text-ink">{formatPoints(l.points)}</td>
              </tr>
            );
          })}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" className="pt-3 text-left font-semibold text-ink">
              The game
            </th>
            <td />
            <td aria-live="polite" className="type-stat pt-3 text-right text-[1.75rem] text-ink">
              {formatPoints(total)}
            </td>
          </tr>
        </tfoot>
      </table>

      <div className="mt-12">
        <p className="text-[14px] text-mute">Every game of his season, scored the same way, then added up.</p>
        <ol className="mt-3 grid grid-cols-6 gap-y-3 border-b border-line pb-3 sm:grid-cols-9">
          {SLOTS.map((week) => {
            const w = byWeek.get(week);
            const lit = week === game.week;
            return (
              <li
                key={week}
                className={`flex flex-col pt-1.5 pr-2 ${lit ? "shadow-[inset_0_2px_0_var(--color-energy)]" : ""}`}
              >
                {/* Two deliberate lines, the week over its opponent, rather than one that
                    wraps wherever the column runs out. A phone keeps the week alone. */}
                <span className="text-[12px] leading-tight text-mute">
                  Wk {week}
                  <span className="sr-only">, </span>
                  <span className="hidden text-faint sm:block">
                    {where.get(week) ?? (week === SF_2025_BYE_WEEK ? "bye" : "no game")}
                  </span>
                </span>
                {w ? (
                  <span className={`tabular text-[15px] ${lit ? "font-semibold text-energy-text" : "text-ink"}`}>
                    <span className="sr-only">: </span>
                    {formatPoints(w.points)}
                  </span>
                ) : (
                  <span className="text-[15px] text-faint">
                    <span aria-hidden>—</span>
                    <span className="sr-only">: no game</span>
                  </span>
                )}
              </li>
            );
          })}
        </ol>
        <div className="mt-4 flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1">
          <p className="text-[15px] text-ink">
            <span className="font-semibold">The season</span>
            <span className="text-mute">
              , {season.gamesPlayed} games: RB{season.posRank}, and{" "}
              {season.overallRank === 1 ? "first" : ordinal(season.overallRank)} on the whole {ruleset} board
            </span>
          </p>
          {/* Orange read as a figure (ki-text): under PPR, the total the hero's board shows. */}
          <p aria-live="polite" className="type-stat text-[2.5rem] text-ki-text">
            {formatPoints(season.points)}
          </p>
        </div>
      </div>
    </div>
  );
}
