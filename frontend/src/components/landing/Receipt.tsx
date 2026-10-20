"use client";

import { useId, useState } from "react";
import { formatPoints } from "@/lib/board";
import { formatRate } from "@/lib/mixer";
import { matchup } from "@/lib/player";
import { STAT_NAMES, presetRates, priceLine } from "@/lib/receipt";
import { FOLLOWED, RECEIPT_GAME, type CatchRate } from "./heroData";

const RATES: CatchRate[] = [0, 0.5, 1];
const PRESET: Record<CatchRate, string> = { 0: "0 PPR", 0.5: "Half PPR", 1: "PPR" };

/** A count for a receipt line: thousands separated, one decimal where it has one. */
function count(n: number) {
  return n.toLocaleString("en-US", { maximumFractionDigits: 2 });
}

/**
 * One real game taken apart: the followed player's most catches this season,
 * every stat it recorded, its rate under the chosen preset, and what that is
 * worth -- adding up to the total the API returned for that game under that
 * preset. `lib/receipt.ts` prices the lines; `tests/lib.test.ts` holds every
 * total to the API's own figure, so the receipt can never disagree with the
 * engine. Below it, the three totals side by side.
 */
export function Receipt() {
  const [rate, setRate] = useState<CatchRate>(0.5);
  const name = useId();
  const { lines } = priceLine(RECEIPT_GAME.stats, presetRates(rate));
  const total = RECEIPT_GAME.points[rate];
  const max = Math.max(...RATES.map((r) => RECEIPT_GAME.points[r]));

  return (
    <div className="rounded-plate border border-line bg-surface">
      <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-3 border-b border-line px-5 py-4 sm:px-7">
        <p className="text-[14px] text-mute">
          <span className="font-semibold text-ink">{FOLLOWED.name}</span>, week {RECEIPT_GAME.week}{" "}
          {matchup(RECEIPT_GAME.opponent, RECEIPT_GAME.home)}
        </p>
        <fieldset className="flex items-center gap-3">
          <legend className="sr-only">Your league pays per catch</legend>
          <span aria-hidden className="text-[13px] text-mute">
            Per catch
          </span>
          <span className="flex gap-1 rounded-control bg-well p-1 shadow-[inset_0_0_0_1px_var(--color-line)]">
            {RATES.map((r) => (
              <label key={r} className="relative">
                <input
                  type="radio"
                  name={name}
                  value={r}
                  checked={r === rate}
                  onChange={() => setRate(r)}
                  className="peer sr-only"
                />
                <span className="flex h-10 min-w-11 cursor-pointer items-center justify-center rounded-[6px] px-3 font-label text-[17px] font-bold text-mute transition-colors select-none peer-checked:bg-lift peer-checked:text-ink peer-checked:shadow-[inset_0_-2px_0_var(--color-energy)] peer-focus-visible:outline-2 peer-focus-visible:outline-offset-1 peer-focus-visible:outline-energy hover:text-ink">
                  {formatRate(r)}
                </span>
              </label>
            ))}
          </span>
        </fieldset>
      </div>

      <table className="w-full border-separate border-spacing-0 text-[15px]">
        <caption className="sr-only">
          {FOLLOWED.name}&rsquo;s week {RECEIPT_GAME.week} stat line priced under {PRESET[rate]}
        </caption>
        <thead className="sr-only">
          <tr>
            <th scope="col">Stat</th>
            <th scope="col">Count</th>
            <th scope="col">Points each</th>
            <th scope="col">Points</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l) => (
            <tr key={l.stat} className="[&>*]:h-14 [&>*]:border-b [&>*]:border-line">
              <th scope="row" className="pl-5 text-left font-normal text-ink sm:pl-7">
                {STAT_NAMES[l.stat]}
              </th>
              <td className="tabular text-right text-mute">{count(l.count)}</td>
              <td className="tabular w-24 text-right text-mute sm:w-32">
                <span aria-hidden>&times; </span>
                <span className={l.stat === "rec" ? "font-semibold text-energy-text" : ""}>{l.rate}</span>
              </td>
              <td className="tabular w-24 pr-5 text-right font-semibold text-ink sm:w-32 sm:pr-7">{formatPoints(l.points)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row" className="pt-5 pb-1 pl-5 text-left text-[14px] font-semibold text-mute sm:pl-7">
              Fantasy points, {PRESET[rate]}
            </th>
            <td colSpan={3} className="pt-4 pr-5 pb-1 text-right sm:pr-7">
              <span className="type-figure text-[44px] text-ink">{formatPoints(total)}</span>
            </td>
          </tr>
        </tfoot>
      </table>

      {/* The same game under all three, so the one rate's whole effect is in view. */}
      <ul className="grid grid-cols-3 gap-3 px-5 pt-3 pb-6 sm:px-7" aria-label="The same game under each preset">
        {RATES.map((r) => {
          const chosen = r === rate;
          return (
            <li key={r} className="min-w-0">
              <div className="flex h-24 items-end">
                <div
                  className={`w-full rounded-t-[6px] transition-[height,background-color] duration-500 ease-[var(--ease-out)] ${chosen ? "bg-energy" : "bg-chart-rest"}`}
                  style={{ height: `${(RECEIPT_GAME.points[r] / max) * 100}%` }}
                />
              </div>
              <p className="mt-2 flex items-baseline justify-between gap-2">
                <span className={`text-[13px] ${chosen ? "font-semibold text-ink" : "text-mute"}`}>{PRESET[r]}</span>
                <span className={`tabular text-[15px] font-semibold ${chosen ? "text-ink" : "text-mute"}`}>
                  {formatPoints(RECEIPT_GAME.points[r])}
                </span>
              </p>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
