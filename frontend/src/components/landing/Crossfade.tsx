/**
 * A figure that changes during the hero's one-time entrance: the value from the
 * board it opens on (`then`) sits in the same cell as the value it lands on
 * (`now`), and CSS turns one into the other as the rows travel (`.fig-then`,
 * `.fig-now` in globals.css). `then` rests at opacity 0, so with no animation
 * -- reduced motion, or any moment after the entrance -- only `now` is seen.
 * Outside the entrance it is just the value.
 */
export function Crossfade({
  entering,
  then,
  now,
  align = "start",
  className = "",
}: {
  entering: boolean;
  then: React.ReactNode | null;
  now: React.ReactNode;
  align?: "start" | "end";
  className?: string;
}) {
  if (!entering) return <span className={className}>{now}</span>;
  return (
    <span
      className={`inline-grid ${align === "end" ? "justify-items-end" : "justify-items-start"} ${className}`}
    >
      {then !== null && <span className="fig-then opacity-0 [grid-area:1/1]">{then}</span>}
      <span className="fig-now [grid-area:1/1]">{now}</span>
    </span>
  );
}
