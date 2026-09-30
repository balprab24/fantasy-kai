import { PositionBadge } from "@/components/rankings/PositionBadge";
import { formatPoints } from "@/lib/board";
import { NACUA_SEASONS_PPR } from "./previewData";

/**
 * A player's finished seasons, each with where he finished at his position --
 * the career table the player page carries, cut to the three seasons Puka
 * Nacua has played. It is what the section's "every season since 2020 with
 * where they finished" looks like, rather than a sentence claiming it.
 */
export function CareerLine() {
  return (
    <table className="w-full text-[15px]">
      <caption className="pb-3 text-left text-[13px] text-mute">His finished seasons, under PPR</caption>
      <thead>
        <tr className="text-right [&>th]:h-8 [&>th]:border-b [&>th]:border-line [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute">
          <th scope="col" className="text-left">
            Season
          </th>
          <th scope="col">G</th>
          <th scope="col">Points</th>
          <th scope="col">Per G</th>
          <th scope="col">Finish</th>
        </tr>
      </thead>
      <tbody>
        {NACUA_SEASONS_PPR.map((s) => (
          <tr key={s.season} className="text-right [&>*]:h-11 [&>*]:border-b [&>*]:border-line">
            <th scope="row" className="tabular text-left font-semibold text-ink">
              {s.season}
            </th>
            <td className="tabular text-mute">{s.gamesPlayed}</td>
            <td className="tabular font-semibold text-ink">{formatPoints(s.points)}</td>
            <td className="tabular text-mute">{formatPoints(s.pointsPerGame)}</td>
            <td>
              <PositionBadge position="WR" rank={s.posRank} basis="by season points under PPR" />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
