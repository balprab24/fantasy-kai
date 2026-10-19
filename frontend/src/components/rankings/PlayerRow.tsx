import Link from "next/link";
import { STARTERS, formatPoints, type BoardRow, type Metric, type Quality } from "@/lib/board";
import { PositionChip } from "../ui/PositionChip";
import { Movement } from "../Movement";
import { PlayerAvatar } from "./PlayerAvatar";

/**
 * Where each column shows, defined once so a header cell and its body cells
 * cannot disagree. Columns step away in order of importance -- games below
 * `lg`, team and the gauge below `md`, position and movement below `sm` -- and
 * what steps away from a phone folds into the second line under the name, so
 * at 390px the board fits with no sideways scroll: rank, player, the number
 * the board is sorted by, and the other figure.
 *
 * The rank column's width is the tier plate's too (`TierHeader`), so the
 * plates stand over the ranks they head.
 */
export const COL = {
  rank: "w-11 pl-3 pr-1 text-right sm:w-16 sm:pl-4 sm:pr-3",
  player: "pr-3 sm:w-[17rem] lg:w-[19rem]",
  pos: "hidden w-20 pr-3 sm:table-cell",
  team: "hidden w-16 pr-3 md:table-cell",
  games: "hidden w-12 pr-3 text-right lg:table-cell",
  // The only column without a width from md up, so it absorbs a wide
  // screen's slack and its bar becomes a real gauge.
  primary: "w-[4.5rem] pr-2 text-right sm:pr-4 md:w-auto",
  secondary: "w-14 pr-3 text-right sm:w-20 sm:pr-4",
  move: "hidden w-20 pr-4 text-right sm:table-cell",
} as const;

export const COLUMN_COUNT = Object.keys(COL).length;

const cell = "h-[3.75rem] border-t border-line transition-colors duration-150 group-hover:bg-well sm:h-[3.25rem]";

/**
 * One player on the board: a line, not a card. Rows sit on the board's panel
 * with a hairline between them, so a board reads as one ranked list rather
 * than sixty boxes.
 *
 * The name is the only real anchor -- one Tab stop per row -- and its
 * `::after` stretches over the whole row, so the row is the click target.
 * Without `href` (a slice of a board shown outside the product) the name is
 * text. `open` marks the player whose view is open over the board: the row
 * keeps an energy edge, so closing the view lands the eye where it left.
 */
export function PlayerRow({
  row,
  delta,
  baseline,
  metric,
  href,
  onOpen,
  open = false,
  settle = true,
  avatar = true,
  showMove = true,
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
  open?: boolean;
  /** Flash a row that moved (a ruleset switch); off where rows never move. */
  settle?: boolean;
  /** The face beside the name. Off where no likeness may be shown. */
  avatar?: boolean;
  /** The movement column, shown once there is a previous ruleset to compare with. */
  showMove?: boolean;
}) {
  const other = metric === "points" ? row.pointsPerGame : row.points;
  const moved = delta !== 0;
  return (
    <tr
      aria-current={open || undefined}
      className={`group relative ${settle && delta ? "settled" : ""} ${open ? "[&>*]:bg-energy/[0.09]" : ""}`}
    >
      <td
        className={`${cell} ${COL.rank} type-figure text-[17px] ${row.rank <= 3 ? "text-ink" : "text-mute"} ${open ? "shadow-[inset_3px_0_0_var(--color-energy)]" : ""}`}
      >
        {row.rank}
      </td>
      <th scope="row" className={`${cell} ${COL.player} text-left font-normal`}>
        <div className="flex min-w-0 items-center gap-3">
          {avatar && <PlayerAvatar name={row.name} espnId={row.espnId} size="md" />}
          <span className="min-w-0">
            {href ? (
              <Link
                href={href}
                onClick={onOpen}
                // The player opens over the board (an intercepted route), so
                // the board must stay exactly where it is underneath.
                scroll={false}
                className="block truncate text-[16px] font-semibold text-ink after:absolute after:inset-0 group-hover:text-white focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
              >
                {row.name}
              </Link>
            ) : (
              <span className="block truncate text-[16px] font-semibold text-ink">{row.name}</span>
            )}
            {/* What a phone's missing columns carry, on one quiet line. No
                team is left out, not called "FA": the data says only that
                none is on record, which is not the same as a free agent. */}
            <span className="mt-1 flex items-center gap-2 text-[12px] text-mute sm:hidden">
              <PositionChip position={row.position} rank={row.posRank} />
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
      <td className={`${cell} ${COL.pos}`}>
        {/* Raised above the row's link overlay so its tooltip can be reached. */}
        <span className="relative z-[2]">
          <PositionChip position={row.position} rank={row.posRank} size="md" />
        </span>
      </td>
      <td className={`${cell} ${COL.team} text-[13px] font-medium text-mute`}>{row.team ?? "—"}</td>
      <td className={`${cell} ${COL.games} tabular text-[13px] text-mute`}>{row.gamesPlayed}</td>
      <td className={`${cell} ${COL.primary}`}>
        <div className="flex items-center justify-end gap-5 md:pl-10">
          <span aria-hidden className="hidden h-1 flex-1 overflow-hidden rounded-full bg-track md:block">
            <span className="block h-full rounded-full bg-mute/50" style={{ width: `${row.share * 100}%` }} />
          </span>
          {metric === "pointsPerGame" ? (
            <PerGame row={row} className="text-[16px] font-semibold" />
          ) : (
            <span className="tabular text-[16px] font-semibold text-ink">{formatPoints(row.value)}</span>
          )}
        </div>
      </td>
      <td className={`${cell} ${COL.secondary} text-[14px]`}>
        {metric === "points" ? (
          <PerGame row={row} />
        ) : (
          <span className="tabular text-mute">{formatPoints(other)}</span>
        )}
      </td>
      {showMove && (
        <td className={`${cell} ${COL.move} tabular text-[13px]`}>
          <span className="relative z-[2]">
            <Movement delta={delta} baseline={baseline} />
          </span>
        </td>
      )}
    </tr>
  );
}

/**
 * One hue, not three: green is the only colour a quality gets, and it means
 * "inside a 12-team league's starters at his position" everywhere -- here, in
 * the player's tables and in the weekly chart. The bench range is plain ink
 * and beyond it steps down to faint, so red never means both "RB" and "bad".
 */
export const QUALITY_TEXT: Record<Quality, string> = {
  good: "text-q-good",
  mid: "text-ink",
  poor: "text-faint",
};

export const QUALITY_WORDS: Record<Quality, string> = {
  good: "starter range",
  mid: "bench range",
  poor: "below bench range",
};

/**
 * Points per game, coloured by where it stands among players at the same
 * position (`lib/board.ts` `Quality`). The words travel with the colour, as a
 * tooltip and for screen readers; a figure too thin to judge stays plain and
 * says why.
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
