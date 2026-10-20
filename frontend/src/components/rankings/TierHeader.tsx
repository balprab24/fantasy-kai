import { TIER_WINDOW, formatPoints, type TierLetter } from "@/lib/board";

/**
 * The tier letter is a plate cut on the logo's slant, standing over the rank
 * column. Only S is orange -- orange is "the best" -- and below it the ladder
 * steps down by brightness alone: blue means "you can act on this", and a
 * tier is not something you click.
 */
const PLATE: Record<TierLetter, string> = {
  S: "bg-ki/15 text-ki-text",
  A: "bg-lift text-ink",
  B: "bg-well text-ink",
  C: "bg-well text-mute",
  D: "bg-well text-mute",
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
  first = false,
}: {
  letter: TierLetter | null;
  count: number;
  high: number;
  low: number;
  unit: string;
  colSpan: number;
  /** The board's first group: no rule above it, the column header already is one. */
  first?: boolean;
}) {
  const players = `${count} ${count === 1 ? "player" : "players"}`;
  const range = high !== low ? `${formatPoints(high)} to ${formatPoints(low)}` : formatPoints(high);
  return (
    <tr>
      <th
        scope="rowgroup"
        colSpan={colSpan}
        className={`px-0 pb-3 text-left font-normal ${first ? "pt-4" : "border-t border-line pt-7"}`}
      >
        <div className="flex items-center gap-3 pl-3 sm:pl-4" title={letter ? RULE : undefined}>
          {letter ? (
            <span
              aria-hidden
              className={`slash flex h-8 w-11 shrink-0 items-center justify-center font-display text-[24px] leading-none font-black italic ${PLATE[letter]}`}
            >
              {letter}
            </span>
          ) : null}
          <span className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-0.5">
            <span className="text-[14px] font-semibold whitespace-nowrap text-ink">
              {letter ? <>Power tier {letter}</> : <>Beyond the top {TIER_WINDOW}</>}
            </span>
            <span className="text-[13px] whitespace-nowrap text-mute">
              {players}, {range} {unit}
              {!letter && ", not tiered"}
            </span>
          </span>
        </div>
      </th>
    </tr>
  );
}
