import { ordinal, positionName } from "../rankings/PositionBadge";

/**
 * Full class names, so Tailwind can see every one. The letters carry the
 * position; the hue is the second cue, and here it may also tint the chip's
 * own ground (14%) -- measured: every hue keeps >= 5.2:1 over its tint on
 * canvas, surface and well (globals.css).
 */
const CHIP: Record<string, string> = {
  QB: "bg-pos-qb/14 text-pos-qb",
  RB: "bg-pos-rb/14 text-pos-rb",
  WR: "bg-pos-wr/14 text-pos-wr",
  TE: "bg-pos-te/14 text-pos-te",
};
const NEUTRAL = "bg-track text-mute";

const SIZE = {
  sm: "h-5 px-2 text-[11px] gap-0.5",
  md: "h-6 px-2.5 text-[12px] gap-0.5",
  lg: "h-8 px-3.5 text-[15px] gap-1",
} as const;

/**
 * A position as a mark: its letters on the logo's slant, tinted in its hue,
 * with the positional rank after it in ink when one is known ("WR 1"). The
 * board's rows, the player stage and the landing all use this one shape, so a
 * position reads the same everywhere.
 *
 * @param basis what the rank is a rank of, for the tooltip: "on this board" on
 *   the board, "by 2025 season points" on a player page.
 */
export function PositionChip({
  position,
  rank = null,
  basis = "on this board",
  size = "sm",
  className = "",
}: {
  position: string;
  rank?: number | null;
  basis?: string;
  size?: keyof typeof SIZE;
  className?: string;
}) {
  return (
    <span
      title={rank === null ? positionName(position) : `${ordinal(rank)} ${positionName(position)} ${basis}`}
      className={`slash inline-flex shrink-0 items-center font-label font-bold whitespace-nowrap tabular ${SIZE[size]} ${CHIP[position] ?? NEUTRAL} ${className}`}
    >
      <span>{position}</span>
      {rank !== null && <span className="text-ink">{rank}</span>}
    </span>
  );
}
