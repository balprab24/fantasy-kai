import { COL, COLUMN_COUNT, PlayerRow } from "@/components/rankings/PlayerRow";
import { TierHeader } from "@/components/rankings/TierHeader";
import { assignTiers, toBoardRows, type Tier } from "@/lib/board";
import { PPR_2025, PPR_FROM_ZERO_2025 } from "./previewData";

/**
 * Ranks shown: 6 to 16, across the first tier break (350.4 to 338.2) -- the
 * part of the board where "tiers are drawn where the points drop off" is
 * visible. The hero already shows the top of the same board.
 */
const FROM = 6;
const TO = 16;

/** The rows ranked `from` to `to` of a tiered board, still grouped by tier. */
function rowsBetween(tiers: Tier[], from: number, to: number): Tier[] {
  return tiers
    .map((tier) => ({ ...tier, rows: tier.rows.filter((r) => r.rank >= from && r.rank <= to) }))
    .filter((tier) => tier.rows.length > 0);
}

/**
 * A slice of the real 2025 PPR board, drawn with the board's own rows, bars
 * and tier dividers -- not a picture of the product, a piece of it. Tiers are
 * cut over the full top 60 (`previewData.ts` says why sixty), then only ranks
 * 6 to 16 are shown, faded at the foot because the board goes on (not at the
 * head: a fade there erased the column headers, which key every figure). The
 * movement column is real too: each player's move from the 0 PPR board.
 *
 * A demonstration, not a tool: `inert` and hidden from screen readers, because
 * the section beside it says in words what it shows. No faces: the landing
 * page shows no player's likeness (owner decision 2026-09-28, kept 2026-09-29),
 * and a monogram says nothing the name does not.
 */
export function BoardSlice() {
  const board = toBoardRows(PPR_2025);
  const full = assignTiers(board).tiers;
  // A divider counts its whole tier, even where only its first rows are shown.
  const size = new Map(full.map((t) => [t.letter, t.rows.length]));
  const tiers = rowsBetween(full, FROM, TO);

  return (
    <div>
      {/* The board's recipe, said the way the real board says it: a ranking
          without its ruleset is a number with no meaning here. */}
      <p className="mb-2 text-[15px] text-mute">
        Scored under <span className="font-semibold text-ink">PPR</span> for the 2025 season, by season total.
        Ranks {FROM} to {TO}, where the first tier ends.
      </p>
      <div
        aria-hidden
        inert
        className="[mask-image:linear-gradient(to_bottom,black_80%,transparent)] select-none"
      >
        <table className="w-full border-separate border-spacing-0 text-sm">
          <thead>
            <tr className="text-left [&>th]:h-9 [&>th]:border-b [&>th]:border-line [&>th]:font-label [&>th]:text-[12px] [&>th]:font-semibold [&>th]:text-mute">
              <th className={COL.rank}>#</th>
              <th className={COL.player}>Player</th>
              <th className={COL.pos}>Pos</th>
              <th className={COL.team}>Team</th>
              <th className={COL.games}>G</th>
              <th className={COL.primary}>Points</th>
              <th className={COL.secondary}>Per G</th>
              <th className={`${COL.move} whitespace-nowrap`}>vs 0 PPR</th>
            </tr>
          </thead>
          {tiers.map((tier) => (
            <tbody key={tier.letter}>
              <TierHeader
                letter={tier.letter}
                count={size.get(tier.letter) ?? tier.rows.length}
                high={tier.high}
                low={tier.low}
                unit="pts"
                colSpan={COLUMN_COUNT}
              />
              {tier.rows.map((row) => {
                const was = PPR_FROM_ZERO_2025[row.playerId];
                return (
                  <PlayerRow
                    key={row.playerId}
                    row={row}
                    delta={was === undefined ? null : was - row.rank}
                    baseline={200}
                    metric="points"
                    settle={false}
                    avatar={false}
                  />
                );
              })}
            </tbody>
          ))}
        </table>
      </div>
    </div>
  );
}
