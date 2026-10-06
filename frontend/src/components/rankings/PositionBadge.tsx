/**
 * Position hues, spelled out in full so Tailwind can see every class name.
 * Colour is the second cue here, never the first: the badge always carries its
 * letters. As marks the four hues fail colour-blind separation (globals.css),
 * so they only ever colour letters.
 */
const HUE: Record<string, { text: string; glow: string }> = {
  QB: { text: "text-pos-qb", glow: "rgb(195 166 255" },
  RB: { text: "text-pos-rb", glow: "rgb(255 107 107" },
  WR: { text: "text-pos-wr", glow: "rgb(92 200 255" },
  TE: { text: "text-pos-te", glow: "rgb(255 143 212" },
};
const NEUTRAL = { text: "text-mute", glow: "rgb(154 166 189" };

export function positionHue(position: string) {
  return HUE[position] ?? NEUTRAL;
}

/** The hue as an rgb() at an alpha, for the player plate's aura. */
export function positionGlow(position: string, alpha: number) {
  return `${positionHue(position).glow} / ${alpha})`;
}

const NAMES: Record<string, string> = {
  QB: "quarterback",
  RB: "running back",
  WR: "wide receiver",
  TE: "tight end",
};

export function positionName(position: string) {
  return NAMES[position] ?? position;
}

/**
 * "RB1": the letters in the position's hue, the rank in ink. Type, not a box --
 * the board is a column of these, and a column of outlined pills was one more
 * row of containers between the eye and the names.
 *
 * @param basis what the rank is a rank of, for the tooltip: "on this board" on
 *   the board, "by season points" on a player page -- the two can differ on a
 *   per-game board, and the tooltip is where that is said.
 */
export function PositionBadge({
  position,
  rank,
  basis = "on this board",
  className = "",
}: {
  position: string;
  rank: number | null;
  basis?: string;
  className?: string;
}) {
  const hue = positionHue(position);
  return (
    <span
      title={rank === null ? undefined : `${ordinal(rank)} ${positionName(position)} ${basis}`}
      className={`tabular font-semibold whitespace-nowrap ${className}`}
    >
      <span className={hue.text}>{position}</span>
      {rank !== null && <span className="text-ink">{rank}</span>}
    </span>
  );
}

export function ordinal(n: number) {
  const tens = n % 100;
  const suffix =
    tens >= 11 && tens <= 13 ? "th" : ({ 1: "st", 2: "nd", 3: "rd" } as Record<number, string>)[n % 10] ?? "th";
  return `${n}${suffix}`;
}
