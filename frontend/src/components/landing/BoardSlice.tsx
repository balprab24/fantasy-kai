import { COL, COLUMN_COUNT, PlayerRow } from "@/components/rankings/PlayerRow";
import { TierHeader } from "@/components/rankings/TierHeader";
import { assignTiers, toBoardRows, type Tier } from "@/lib/board";
import { PPR_2025, PPR_FROM_ZERO_2025 } from "./previewData";

/** Rows shown: the whole S tier and the start of A, as the real board draws them. */
const SHOWN = 11;

/** The first `count` rows of a tiered board, still grouped by tier. */
function firstRows(tiers: Tier[], count: number): Tier[] {
  return tiers.reduce<Tier[]>((out, tier) => {
    const room = count - out.reduce((n, t) => n + t.rows.length, 0);
    return room > 0 ? [...out, { ...tier, rows: tier.rows.slice(0, room) }] : out;
  }, []);
}

/**
 * The top of the real 2025 PPR board, drawn with the board's own rows and
 * tier dividers -- not a picture of the product, a slice of it. Tiers are cut
 * over the full top 60 (`previewData.ts` says why sixty), then only the first
 * rows are shown. The movement column is real too: each player's move from the
 * 0 PPR board to this one.
 *
 * A demonstration, not a tool: `inert` and hidden from screen readers, because
 * the section beside it says in words what it shows. Monograms, not headshots:
 * the landing page shows no player's likeness (owner decision 2026-09-28,
 * kept 2026-09-29).
 */
export function BoardSlice() {
  const board = toBoardRows(PPR_2025);
  const full = assignTiers(board).tiers;
  // A divider counts its whole tier, even where only its first rows are shown.
  const size = new Map(full.map((t) => [t.letter, t.rows.length]));
  const tiers = firstRows(full, SHOWN);

  return (
    <div>
      {/* The board's recipe, said the way the real board says it: a ranking
          without its ruleset is a number with no meaning here. */}
      <p className="mb-4 text-[15px] text-mute">
        Scored under <span className="font-semibold text-ink">PPR</span> for the 2025 season, by
        season total.
      </p>
      <div
        aria-hidden
        inert
        className="[mask-image:linear-gradient(to_bottom,black_72%,transparent)] select-none"
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
