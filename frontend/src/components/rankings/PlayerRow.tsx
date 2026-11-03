import Link from "next/link";
import { STARTERS, formatPoints, type BoardRow, type Metric, type Quality } from "@/lib/board";
import { PositionChip } from "../ui/PositionChip";
import { Movement } from "../Movement";
import { PlayerAvatar } from "./PlayerAvatar";
import { COL } from "./BoardColumns";

const cell = "h-[38px] border-t border-line transition-colors duration-150 group-hover:bg-well";

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
  view = "overview",
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
  view?: "overview" | "usage";
}) {
  const other = metric === "points" ? row.pointsPerGame : row.points;
  return (
    <tr
      data-player-row
      aria-current={open || undefined}
      className={`group relative ${settle && delta ? "settled" : ""} ${open ? "[&>*]:bg-energy/[0.09]" : ""}`}
    >
      <td
        className={`${cell} ${COL.rank} tabular type-figure text-[17px] ${row.rank <= 3 ? "text-ink" : "text-mute"} ${open ? "shadow-[inset_3px_0_0_var(--color-energy)]" : ""}`}
      >
        {row.rank}
      </td>
      <th scope="row" className={`${cell} ${COL.player} text-left font-normal`}>
        <div className="flex min-w-0 items-center gap-2">
          {avatar && (
            <span className="flex shrink-0 max-[359px]:hidden">
              <PlayerAvatar name={row.name} espnId={row.espnId} size="md" />
            </span>
          )}
          <span className="min-w-0">
            {href ? (
              <Link
                href={href}
                onClick={onOpen}
                // The player opens over the board (an intercepted route), so
                // the board must stay exactly where it is underneath.
                scroll={false}
                className="block truncate text-[14px] font-semibold text-ink after:absolute after:inset-0 group-hover:text-white focus-visible:outline-none focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-energy"
              >
                {row.name}
              </Link>
            ) : (
              <span className="block truncate text-[14px] font-semibold text-ink">{row.name}</span>
            )}
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
        <div className="flex items-center justify-end">
          {metric === "pointsPerGame" ? (
            <PerGame row={row} className="text-[14px] font-semibold" />
          ) : (
            <span className="tabular text-[14px] font-semibold text-ink">{formatPoints(row.value)}</span>
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
      <td className={`${cell} ${COL.recent} tabular text-[13px] text-ink`}>{row.recentPpg == null ? "—" : formatPoints(row.recentPpg)}</td>
      <td className={`${cell} ${COL.form} tabular text-[13px] text-mute`} title="Recent points per game minus season points per game">
        {row.form == null ? "—" : `${row.form > 0 ? "+" : ""}${formatPoints(row.form)}`}
      </td>
      {view === "usage" ? (
        <>
          <td className={`${cell} ${COL.usage} tabular text-[13px] text-mute`}>{row.usage?.passAtt ?? "—"}</td>
          <td className={`${cell} ${COL.usage} tabular text-[13px] text-mute`}>{row.usage?.rushAtt ?? "—"}</td>
          <td className={`${cell} ${COL.usage} tabular text-[13px] text-mute`}>{row.usage?.targets ?? "—"}</td>
        </>
      ) : (
        <td className={`${cell} ${COL.detail} tabular text-[12px] text-mute`}>
          <div className="flex items-center justify-end gap-1">
            {row.recentWeeks?.length ? row.recentWeeks.map((week) => (
              <span key={week.week} title={`Week ${week.week}: ${week.points === null ? "no recorded game" : formatPoints(week.points) + " points"}`} className="min-w-9 rounded-nested bg-well px-1 py-0.5 text-ink">{week.points === null ? "—" : formatPoints(week.points)}</span>
            )) : "—"}
          </div>
        </td>
      )}
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
      : "Per-game rank unavailable or too few games to qualify";
  return (
    <span title={title} className={`tabular rounded-nested px-1.5 py-0.5 ${q === "good" ? "bg-q-good/10" : "bg-well"} ${q ? QUALITY_TEXT[q] : "text-mute"} ${className}`}>
      {formatPoints(row.pointsPerGame)}
      {q && <span className="sr-only">, {QUALITY_WORDS[q]}</span>}
    </span>
  );
}
