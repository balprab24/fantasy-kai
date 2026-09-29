/**
 * Position hues, spelled out in full so Tailwind can see every class name.
 * Colour is the second cue here, never the first: the badge always carries
 * its letters, and the avatar ring is decoration on top of that.
 */
const HUE: Record<string, { text: string; border: string; ring: string }> = {
  QB: { text: "text-pos-qb", border: "border-pos-qb/45", ring: "ring-pos-qb/55" },
  RB: { text: "text-pos-rb", border: "border-pos-rb/45", ring: "ring-pos-rb/55" },
  WR: { text: "text-pos-wr", border: "border-pos-wr/45", ring: "ring-pos-wr/55" },
  TE: { text: "text-pos-te", border: "border-pos-te/45", ring: "ring-pos-te/55" },
};
const NEUTRAL = { text: "text-mute", border: "border-line-strong", ring: "ring-line-strong" };

export function positionHue(position: string) {
  return HUE[position] ?? NEUTRAL;
}

const NAMES: Record<string, string> = {
  QB: "quarterback",
  RB: "running back",
  WR: "wide receiver",
  TE: "tight end",
};

/**
 * @param basis what the rank is a rank of, for the tooltip: "on this board" on
 *   the board, "by season points" on a player page -- the two can differ on a
 *   per-game board, and the tooltip is where that is said.
 */
export function PositionBadge({
  position,
  rank,
  basis = "on this board",
}: {
  position: string;
  rank: number | null;
  basis?: string;
}) {
  const hue = positionHue(position);
  return (
    <span
      title={rank === null ? undefined : `${ordinal(rank)} ${NAMES[position] ?? position} ${basis}`}
      className={`tabular inline-flex h-6 min-w-12 items-center justify-center gap-1 rounded border px-1.5 text-xs font-semibold ${hue.text} ${hue.border}`}
    >
      {position}
      {rank !== null && <span className="text-ink">{rank}</span>}
    </span>
  );
}

function ordinal(n: number) {
  const tens = n % 100;
  const suffix =
    tens >= 11 && tens <= 13 ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th";
  return `${n}${suffix}`;
}
