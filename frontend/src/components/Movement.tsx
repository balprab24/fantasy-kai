/**
 * How far a player moved when the ruleset changed.
 *
 * This is the only place the product says "and here is the difference", so it
 * is shown as a signed integer rather than a coloured arrow: the number of
 * places is the information, and a green/red pair would encode good/bad, which
 * is a judgement the data does not support. A player who gains 40 places under
 * full PPR has not improved -- the ruler changed.
 */
export function Movement({ delta }: { delta: number | null }) {
  if (delta === null) {
    return (
      <span className="text-faint" title="Not ranked under the previous ruleset">
        new
      </span>
    );
  }
  if (delta === 0) {
    return <span className="text-faint">—</span>;
  }
  return (
    <span className={delta > 0 ? "text-field" : "text-mute"}>
      {delta > 0 ? `+${delta}` : delta}
    </span>
  );
}
