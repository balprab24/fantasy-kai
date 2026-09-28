import { TIER_WINDOW, formatPoints, type TierLetter } from "@/lib/board";

/**
 * Energy fades down the ladder, and only the top of it glows. The S glyph's
 * aura is the single glow in the table -- if every tier had one, none would.
 * Below S the ladder is brightness alone: blue means "interactive" in this
 * product, and a tier is not something you click.
 */
const GLYPH: Record<TierLetter, string> = {
  S: "border-ki text-ki shadow-[0_0_12px_-2px_rgb(255_138_61/0.6)]",
  A: "border-ink text-ink",
  B: "border-line-strong text-ink",
  C: "border-line-strong text-mute",
  D: "border-line-strong text-mute",
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
  return (
    <tr>
      <th scope="rowgroup" colSpan={colSpan} className="px-0 pt-6 pb-2 text-left font-normal">
        {/* On a phone the table scrolls sideways; the label is pinned so a
            tier row never becomes a bare rule. */}
        <div className="flex items-center gap-3" title={letter ? RULE : undefined}>
          <span className="flex items-center gap-3 max-md:sticky max-md:left-0">
          {letter ? (
            <span
              aria-hidden
              className={`inline-flex h-6 w-8 -skew-x-12 items-center justify-center rounded-[3px] border bg-paper ${GLYPH[letter]}`}
            >
              <span className="font-display skew-x-12 text-[15px] leading-none font-bold">
                {letter}
              </span>
            </span>
          ) : (
            <span aria-hidden className="inline-block h-6 w-8" />
          )}
          <span className="eyebrow whitespace-nowrap">
            {letter ? <>Power Tier {letter}</> : <>Beyond the top {TIER_WINDOW}</>}
          </span>
          <span className="tabular hidden text-xs whitespace-nowrap text-faint sm:inline">
            {count} {count === 1 ? "player" : "players"} · {formatPoints(high)}
            {high !== low && <> to {formatPoints(low)}</>} {unit}
            {!letter && " · not tiered"}
          </span>
          </span>
          <span aria-hidden className="h-px flex-1 bg-line" />
        </div>
      </th>
    </tr>
  );
}
