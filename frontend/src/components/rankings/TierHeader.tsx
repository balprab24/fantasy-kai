import { formatPoints, type TierLetter } from "@/lib/board";
import { COL } from "./PlayerRow";

/** A tier and its column names share one compact band. */
export function TierHeader({ letter, count, high, low, perGame, showMove, view }: {
  letter: TierLetter | null; count: number; high: number; low: number;
  perGame: boolean; showMove: boolean; view: "overview" | "usage";
}) {
  return (
    <tr className="h-6 bg-well text-left font-label text-[11px] text-mute [&>th]:h-6 [&>th]:font-semibold">
      <th scope="col" className={`${COL.rank} ${letter === "S" ? "text-ki-text" : "text-ink"}`} aria-label={letter ? `Tier ${letter}` : "Rank"}>{letter ?? "#"}</th>
      <th scope="col" className={COL.player} title={`${count} players, ${formatPoints(high)} to ${formatPoints(low)} ${perGame ? "points per game" : "points"}`}>
        {letter ? `Player · tier ${letter}` : "Player"}
      </th>
      <th scope="col" className={COL.pos}>Position</th>
      <th scope="col" className={COL.team} title="Current team">Team</th>
      <th scope="col" className={COL.games} title="Games played">G</th>
      <th scope="col" className={COL.primary}>{perGame ? "Per game" : "Points"}</th>
      <th scope="col" className={COL.secondary}>{perGame ? "Total" : "Per game"}</th>
      <th scope="col" className={COL.recent} title="Points per game over the last four league weeks">Recent</th>
      <th scope="col" className={COL.form} title="Recent points per game minus season points per game">Form ±</th>
      <th scope="col" className={COL.detail}>{view === "usage" ? "Pass att / carries / targets" : "Last 4 weeks · oldest first"}</th>
      {showMove && <th scope="col" className={COL.move}>Move</th>}
    </tr>
  );
}
