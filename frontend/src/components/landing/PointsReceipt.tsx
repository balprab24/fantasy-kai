import { formatPoints } from "@/lib/board";
import type { StatKey } from "@/lib/types";
import { MCCAFFREY_2025_PPR } from "./previewData";

/**
 * The PPR preset's rates for the stats on this receipt, as V3 seeds them
 * (`V3__seed_scoring_presets.sql`, "Full PPR"). Copied, so it is checked: the
 * lines below must add up to the season total the API returned, or rendering
 * throws -- and this page is prerendered, so that is `npm run build` failing
 * rather than a wrong sum going out.
 */
const PPR: Partial<Record<StatKey, number>> = {
  rush_yd: 0.1,
  rush_td: 6,
  rec: 1,
  rec_yd: 0.1,
  rec_td: 6,
};

const WORDS: Partial<Record<StatKey, string>> = {
  rush_yd: "rushing yards",
  rush_td: "rushing touchdowns",
  rec: "catches",
  rec_yd: "receiving yards",
  rec_td: "receiving touchdowns",
};

/**
 * One real season, taken apart: each stat, its rate, what it is worth. It is
 * how every number on the board is made, shown once.
 *
 * Summed per stat over the season rather than per game, which is exact here
 * only because the PPR preset has no threshold bonuses -- with bonuses the
 * board scores each game first, which is why it always does.
 */
export function PointsReceipt() {
  const season = MCCAFFREY_2025_PPR;
  const lines = (Object.keys(PPR) as StatKey[])
    .filter((stat) => season.stats[stat] !== 0)
    .map((stat) => ({
      stat,
      count: season.stats[stat],
      rate: PPR[stat]!,
      points: season.stats[stat] * PPR[stat]!,
    }));
  const total = lines.reduce((sum, l) => sum + l.points, 0);
  const rest = (Object.keys(season.stats) as StatKey[]).filter(
    (stat) => !(stat in PPR) && season.stats[stat] !== 0,
  );
  if (rest.length > 0 || Math.abs(total - season.points) >= 0.05) {
    throw new Error(
      `PointsReceipt: ${formatPoints(total)} from the rates here, ${season.points} from the API` +
        (rest.length ? `; unpriced stats: ${rest.join(", ")}` : ""),
    );
  }

  return (
    // `tabular` on the points column only: Schibsted's tabular comma and period
    // carry extra sidebearing (globals.css), which reads as "1 , 202" in prose.
    <table className="my-8 w-full max-w-[26rem] text-[15px]">
      <caption className="pb-3 text-left text-sm text-mute">
        Christian McCaffrey&rsquo;s 2025 regular season, under PPR
      </caption>
      <tbody>
        {lines.map((l) => (
          <tr key={l.stat} className="border-b border-line">
            <th scope="row" className="py-2 pr-4 text-left font-normal text-ink">
              {l.count.toLocaleString("en-US")} {WORDS[l.stat]}
            </th>
            <td className="py-2 pr-4 text-right text-faint">&times; {l.rate}</td>
            <td className="tabular py-2 text-right text-ink">{formatPoints(l.points)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row" className="pt-3 text-left font-medium text-ink">
            Season total
          </th>
          <td />
          <td className="font-display tabular pt-3 text-right text-xl font-bold text-ki">{formatPoints(total)}</td>
        </tr>
      </tfoot>
    </table>
  );
}
