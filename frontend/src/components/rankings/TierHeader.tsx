import { TIER_WINDOW, formatPoints, type TierLetter } from "@/lib/board";

/**
 * The tier letter is the divider: set big, in the display voice, and the
 * ladder fades down it. Only S is orange -- orange is "the best" -- and that
 * is all it needs: no glow (the product keeps one, the landing's drawn route).
 * Below S the ladder is brightness alone: blue means "you can act on this",
 * and a tier is not something you click.
 */
const LETTER: Record<TierLetter, string> = {
  S: "text-ki",
  A: "text-ink",
  B: "text-mute",
  C: "text-faint",
  D: "text-faint",
};

const RULE = `Tiers are natural breaks in points across the top ${TIER_WINDOW} of this board: everyone in a tier is closer to each other than to the tier next door.`;

/**
 * The first row of a tier's `<tbody>`. A `<th scope="rowgroup">`, so a screen
 * reader moving through the rows hears which tier it is in, not just a rank.
 */
export function TierHeader({
  letter,
  count,
  high,
  low,
  unit,
  colSpan,
}: {
  letter: TierLetter | null;
  count: number;
  high: number;
  low: number;
  unit: string;
  colSpan: number;
}) {
  const players = `${count} ${count === 1 ? "player" : "players"}`;
  const range = high !== low ? `${formatPoints(high)} to ${formatPoints(low)}` : formatPoints(high);
  return (
    <tr>
      <th scope="rowgroup" colSpan={colSpan} className="px-0 pt-8 pb-2 text-left font-normal">
        <div className="flex items-end gap-3" title={letter ? RULE : undefined}>
          {letter ? (
            <span
              aria-hidden
              className={`w-10 shrink-0 pr-1 text-right font-display text-[34px] leading-[0.8] font-black italic sm:w-12 sm:pr-3 ${LETTER[letter]}`}
            >
              {letter}
            </span>
          ) : null}
          <span className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-0.5 pb-0.5">
            <span className="text-[13px] font-semibold whitespace-nowrap text-ink">
              {letter ? <>Power tier {letter}</> : <>Beyond the top {TIER_WINDOW}</>}
            </span>
            <span className="text-[13px] whitespace-nowrap text-mute">
              {players}, {range} {unit}
              {!letter && ", not tiered"}
            </span>
          </span>
          <span aria-hidden className="mb-1.5 h-px min-w-6 flex-1 bg-line" />
        </div>
      </th>
    </tr>
  );
}
