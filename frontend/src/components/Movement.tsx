/**
 * How far a player moved when the ruleset changed.
 *
 * This is the only place the product says "and here is the difference". The
 * arrow carries the direction and the number carries the distance, so neither
 * depends on colour -- and the colour stays nearly neutral on purpose (ink
 * for up, a quieter grey for down), never a green/red pair: that would encode
 * good/bad, a judgement the data does not support. A player who gains 40
 * places under full PPR has not improved -- the ruler changed.
 */
export function Movement({ delta, baseline }: { delta: number | null; baseline: number }) {
  if (delta === null) {
    // Every ruleset ranks the same players on the same board, so a player
    // missing from the baseline was not unranked -- they were below the rows
    // loaded at the time. Say that, rather than "new".
    return (
      <span
        className="text-xs whitespace-nowrap text-mute"
        title={`Outside the top ${baseline} under the previous ruleset`}
      >
        <span className="sr-only">previously outside the top </span>
        <span aria-hidden>was </span>
        {baseline}+
      </span>
    );
  }
  if (delta === 0) {
    return (
      <span className="text-faint">
        <span aria-hidden>—</span>
        <span className="sr-only">no change</span>
      </span>
    );
  }
  const up = delta > 0;
  return (
    <span className={`inline-flex items-center gap-1 whitespace-nowrap ${up ? "text-ink" : "text-mute"}`}>
      <svg aria-hidden width="8" height="8" viewBox="0 0 8 8" className="shrink-0">
        <path d={up ? "M4 1 7.5 7h-7z" : "M.5 1h7L4 7z"} fill="currentColor" />
      </svg>
      <span className="sr-only">{up ? "up " : "down "}</span>
      {Math.abs(delta)}
    </span>
  );
}
