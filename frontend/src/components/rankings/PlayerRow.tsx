import Link from "next/link";
import { STARTERS, formatPoints, type BoardRow, type Metric, type Quality } from "@/lib/board";
import { Movement } from "../Movement";
import { PlayerAvatar } from "./PlayerAvatar";
import { PositionBadge } from "./PositionBadge";

/**
 * Where each column shows, defined once so a header cell and its body cells
 * cannot disagree. Columns step away in order of importance -- games below
 * `lg`, team and the gauge below `md`, position and movement below `sm` -- and
 * what steps away from a phone folds into the second line under the name, so
 * at 390px the board fits with no sideways scroll: rank, player, the number
 * the board is sorted by, and the other figure.
 *
 * The rank column's width is the tier letter's too (`TierHeader`), so the
 * letters stand over the ranks they head.
 */
export const COL = {
  rank: "w-10 pr-2 text-right sm:w-12 sm:pr-3",
  player: "pr-3 sm:w-64 lg:w-72",
  pos: "hidden w-14 pr-3 sm:table-cell",
  team: "hidden w-14 pr-3 md:table-cell",
  games: "hidden w-12 pr-3 text-right lg:table-cell",
  // The only column without a width from md up, so it absorbs a wide
  // screen's slack and its bar becomes a real gauge -- instead of a dead gap
  // opening between a player's name and their position.
  primary: "w-16 pr-2 text-right sm:pr-3 md:w-auto",
  secondary: "w-12 pr-1 text-right sm:w-16 sm:pr-3",
  move: "hidden w-16 pr-2 text-right sm:table-cell",
} as const;

export const COLUMN_COUNT = Object.keys(COL).length;

const cell = "h-12 border-b border-line transition-colors group-hover:bg-well sm:h-11";

/**
 * One player on the board: a line, not a card. The rows sit on the page
 * itself with a hairline between them, so a board reads as one list of
 * players rather than sixty separate boxes.
 *
 * The name is the only real anchor -- one Tab stop per row -- and its
 * `::after` stretches over the whole row, so the row is the click target.
 * Without `href` (the landing page's slice of a board) the name is text.
 */
export function PlayerRow({
  row,
  delta,
  baseline,
  metric,
  href,
  onOpen,
  settle = true,
}: {
  row: BoardRow;
  /** Places moved since the previous ruleset; see `Movement`. */
  delta: number | null;
  /** How many rows the previous ruleset's board had loaded. */
  baseline: number;
  metric: Metric;
  href?: string;
  /** Called as the player link is followed, so the board can note its place. */
  onOpen?: () => void;
  /** Flash a row that moved (a ruleset switch); off where rows never move, like the landing's slice. */
  settle?: boolean;
}) {
  const other = metric === "points" ? row.pointsPerGame : row.points;
  const moved = delta !== 0;
  return (
    <tr className={`group relative ${settle && delta ? "settled" : ""}`}>
      <td className={`${cell} ${COL.rank} type-rank text-[15px] text-mute`}>{row.rank}</td>
      <th scope="row" className={`${cell} ${COL.player} text-left font-normal`}>
        <div className="flex min-w-0 items-center gap-3">
          <PlayerAvatar name={row.name} espnId={row.espnId} />
          <span className="min-w-0">
            {href ? (
              <Link
                href={href}
                onClick={onOpen}
                className="block truncate text-[15px] font-semibold text-ink after:absolute after:inset-0 focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
              >
                {row.name}
              </Link>
            ) : (
              <span className="block truncate text-[15px] font-semibold text-ink">{row.name}</span>
            )}
            {/* What a phone's missing columns carry, on one quiet line. No
                team is left out, not called "FA": the data says only that
                none is on record, which is not the same as a free agent. */}
            <span className="flex items-center gap-1.5 text-[12px] text-mute sm:hidden">
              <PositionBadge position={row.position} rank={row.posRank} />
              {row.team && <span>{row.team}</span>}
              {moved && (
                <span className="relative z-[2]">
                  <Movement delta={delta} baseline={baseline} />
                </span>
              )}
            </span>
          </span>
        </div>
      </th>
      <td className={`${cell} ${COL.pos} text-[13px]`}>
        {/* Raised above the row's link overlay so its tooltip can be reached. */}
        <span className="relative z-[2]">
          <PositionBadge position={row.position} rank={row.posRank} />
        </span>
      </td>
      <td className={`${cell} ${COL.team} text-[13px] text-mute`}>{row.team ?? "—"}</td>
      <td className={`${cell} ${COL.games} tabular text-[13px] text-mute`}>{row.gamesPlayed}</td>
      <td className={`${cell} ${COL.primary}`}>
        <div className="flex items-center justify-end gap-5 md:pl-8">
          <span aria-hidden className="hidden h-[3px] flex-1 bg-white/[0.06] md:block">
            <span className="block h-full bg-mute/60" style={{ width: `${row.share * 100}%` }} />
          </span>
          {metric === "pointsPerGame" ? (
            <PerGame row={row} className="text-[15px] font-semibold" />
          ) : (
            <span className="tabular text-[15px] font-semibold text-ink">{formatPoints(row.value)}</span>
          )}
        </div>
      </td>
      <td className={`${cell} ${COL.secondary} text-[13px]`}>
        {metric === "points" ? (
          <PerGame row={row} />
        ) : (
          <span className="tabular text-mute">{formatPoints(other)}</span>
        )}
      </td>
      <td className={`${cell} ${COL.move} tabular text-[13px]`}>
        <span className="relative z-[2]">
          <Movement delta={delta} baseline={baseline} />
        </span>
      </td>
    </tr>
  );
}

/**
 * One hue, not three: green is the only colour a quality gets, and it means
 * "inside a 12-team league's starters at his position" everywhere -- here, in
 * the player's tables and in the weekly chart. The bench range is plain ink
 * and beyond it steps down to faint, so red never means both "RB" and "bad".
 * Full class names, so Tailwind can see them.
 */
export const QUALITY_TEXT: Record<Quality, string> = {
  good: "text-q-good",
  mid: "text-ink",
  poor: "text-faint",
};

const QUALITY_WORDS: Record<Quality, string> = {
  good: "starter range",
  mid: "bench range",
  poor: "below bench range",
};

/**
 * Points per game, coloured by where it stands among players at the same
 * position (`lib/board.ts` `Quality`). The only figure in a row that is
 * coloured by how good it is: rank and total are already said by the rank,
 * the tier and the bar, and movement is deliberately neutral (see
 * `Movement`). The words travel with the colour, as a tooltip and for screen
 * readers; a figure too thin to judge stays plain and says why.
 */
function PerGame({ row, className = "" }: { row: BoardRow; className?: string }) {
  const q = row.quality;
  const title =
    q && row.ppgPosRank !== null
      ? `${row.position} ${row.ppgPosRank} by points per game — ${QUALITY_WORDS[q]} (a 12-team league starts ${STARTERS[row.position]})`
      : "Too few games on this board to judge the per-game figure";
  return (
    <span title={title} className={`tabular ${q ? QUALITY_TEXT[q] : "text-mute"} ${className}`}>
      {formatPoints(row.pointsPerGame)}
      {q && <span className="sr-only">, {QUALITY_WORDS[q]}</span>}
    </span>
  );
}
