import { formatPoints, type TierLetter } from "@/lib/board";
import { COL } from "./BoardColumns";

/** Tier separators identify the row group; column names live in the sticky header. */
export function TierHeader({ letter, count, high, low, perGame, showMove, view }: {
  letter: TierLetter | null; count: number; high: number; low: number;
  perGame: boolean; showMove: boolean; view: "overview" | "usage";
}) {
  return (
    <tr className="bg-well text-left font-label text-[11px] leading-4 text-mute">
      <th
        scope="rowgroup"
        colSpan={(view === "usage" ? 12 : 10) + (showMove ? 1 : 0)}
        className="h-4 px-2 font-semibold"
        title={`${count} players, ${formatPoints(high)} to ${formatPoints(low)} ${perGame ? "points per game" : "points"}`}
      >
        <span className={`sticky left-2 inline-block ${letter === "S" ? "text-ki-text" : "text-ink"}`}>
          {letter ? `Tier ${letter}` : "Remaining players"}
        </span>
      </th>
    </tr>
  );
}

/** Used visually in the sticky band and semantically in the body table's hidden thead. */
export function ColumnHeaders({ perGame, showMove, view }: {
  perGame: boolean; showMove: boolean; view: "overview" | "usage";
}) {
  return (
    <tr className="h-6 bg-well text-left font-label text-[11px] whitespace-nowrap text-mute [&>th]:h-6 [&>th]:bg-well [&>th]:font-semibold">
      <th scope="col" className={`${COL.rank} text-ink`} aria-label="Rank">#</th>
      <th scope="col" className={COL.player}>Player</th>
      <th scope="col" className={COL.pos}>Position</th>
      <th scope="col" className={COL.team} title="Current team">Team</th>
      <th scope="col" className={COL.games} title="Games played">G</th>
      <th scope="col" className={COL.primary}>{perGame ? "Per game" : "Points"}</th>
      <th scope="col" className={COL.secondary}>{perGame ? "Total" : "Per game"}</th>
      <th scope="col" className={COL.recent} title="Points per game over the last four league weeks">Recent</th>
      <th scope="col" className={COL.form} title="Recent points per game minus season points per game">Form ±</th>
      {view === "usage" ? (
        <>
          <th scope="col" className={COL.usage} title="Pass attempts in the selected window">Pass att</th>
          <th scope="col" className={COL.usage} title="Carries in the selected window">Carries</th>
          <th scope="col" className={COL.usage} title="Targets in the selected window">Targets</th>
        </>
      ) : <th scope="col" className={COL.detail}>Last 4 weeks · oldest first</th>}
      {showMove && <th scope="col" className={COL.move}>Move</th>}
    </tr>
  );
}
