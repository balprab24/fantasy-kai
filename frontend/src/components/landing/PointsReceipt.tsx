import { formatPoints } from "@/lib/board";
import type { StatKey } from "@/lib/types";
import { MCCAFFREY_2025_PPR_RECEIPT } from "./previewData";

const WORDS: Partial<Record<StatKey, string>> = {
  rush_yd: "rushing yards",
  rush_td: "rushing touchdowns",
  rec: "catches",
  rec_yd: "receiving yards",
  rec_td: "receiving touchdowns",
};

/**
 * One real season, taken apart: each stat, its rate, what it is worth. It is
 * how every number on the board is made, shown once. The arithmetic, and the
 * check that it adds up to the API's total, are `previewData.ts`'s -- where
 * `npm test` runs them, rather than here, where a mismatch could only fail a
 * request.
 */
export function PointsReceipt() {
  const { lines, total } = MCCAFFREY_2025_PPR_RECEIPT;

  return (
    <table className="w-full text-[15px]">
      <caption className="pb-4 text-left text-[13px] text-mute">
        How a number is made: Christian McCaffrey&rsquo;s 2025 regular season, under PPR
      </caption>
      <tbody>
        {lines.map((l) => (
          <tr key={l.stat} className="[&>*]:border-b [&>*]:border-line">
            <th scope="row" className="py-2.5 pr-4 text-left font-normal text-ink">
              {l.count.toLocaleString("en-US")} {WORDS[l.stat]}
            </th>
            <td className="tabular py-2.5 pr-4 text-right text-mute">&times; {l.rate}</td>
            <td className="tabular py-2.5 text-right text-ink">{formatPoints(l.points)}</td>
          </tr>
        ))}
      </tbody>
      <tfoot>
        <tr>
          <th scope="row" className="pt-4 text-left font-semibold text-ink">
            Season total
          </th>
          <td />
          {/* The one orange figure on the page after the plate: the total the
              whole board is built from. Proportional, not tabular -- it stands alone. */}
          <td className="type-stat pt-4 text-right text-[2.5rem] text-ki">{formatPoints(total)}</td>
        </tr>
      </tfoot>
    </table>
  );
}
