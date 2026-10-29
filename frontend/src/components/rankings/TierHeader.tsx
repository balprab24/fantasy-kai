import { TIER_WINDOW, formatPoints, type TierLetter } from "@/lib/board";

/**
 * The tier letter is the divider: set big, in the display voice, and the
 * ladder fades down it. Only S is orange -- orange is "the best" -- and that
 * is all it needs: no glow (the product keeps one, the landing's drawn route).
 * Below S the ladder is brightness alone: blue means "you can act on this",
 * and a tier is not something you click.
 */
const LETTER: Record<TierLetter, string> = {
  S: "text-ki-text",
  A: "text-ink",
  B: "text-mute",
  C: "text-faint",
  D: "text-faint",
};

const RULE = `Tiers are natural breaks in points across the top ${TIER_WINDOW} of this board: everyone in a tier is closer to each other than to the tier next door.`;

/**
 * The first row of a tier's `<tbody>`. A `<th scope="rowgroup">`, so a screen
 * reader moving through the rows hears which tier it is in, not just a rank.
 *
 * `band`: the member board opens each tier on a strip of the surface step --
 * the board is long and dense there, and the strip is where the eye finds its
 * place again. Square, like every structural thing, and the letter keeps its
 * own colour: the strip separates, it never ranks. Off on the landing, whose
 * dividers stay a letter and a hairline.
 */
export function TierHeader({
  letter,
  count,
  high,
  low,
  unit,
  colSpan,
  band = false,
}: {
  letter: TierLetter | null;
  count: number;
  high: number;
  low: number;
  unit: string;
  colSpan: number;
  band?: boolean;
}) {
  const players = `${count} ${count === 1 ? "player" : "players"}`;
  const range = high !== low ? `${formatPoints(high)} to ${formatPoints(low)}` : formatPoints(high);
  return (
    <tr>
      <th
        scope="rowgroup"
        colSpan={colSpan}
        className={`px-0 text-left font-normal ${band ? "pt-6" : "pt-8 pb-2"}`}
      >
        <div
          className={`flex gap-3 ${band ? "h-12 items-center border-b border-line bg-surface" : "items-end"}`}
          title={letter ? RULE : undefined}
        >
          {letter ? (
            <span
              aria-hidden
              className={`w-10 shrink-0 pr-1 text-right font-display text-[34px] leading-[0.8] font-black italic sm:w-12 sm:pr-3 ${LETTER[letter]}`}
            >
              {letter}
            </span>
          ) : band ? (
            // The strip has no padding of its own: the empty letter slot keeps
            // the words over the names, as in a lettered tier.
            <span aria-hidden className="w-10 shrink-0 sm:w-12" />
          ) : null}
          <span
            className={`flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-0.5 ${band ? "" : "pb-0.5"}`}
          >
            <span className="text-[13px] font-semibold whitespace-nowrap text-ink">
              {letter ? <>Power tier {letter}</> : <>Beyond the top {TIER_WINDOW}</>}
            </span>
            <span className="text-[13px] whitespace-nowrap text-mute">
              {players}, {range} {unit}
              {!letter && ", not tiered"}
            </span>
          </span>
          {!band && <span aria-hidden className="mb-1.5 h-px min-w-6 flex-1 bg-line" />}
        </div>
      </th>
    </tr>
  );
}
