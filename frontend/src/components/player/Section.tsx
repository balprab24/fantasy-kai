/**
 * A segment of the report: its name in the display voice, what it covers, and
 * one hairline -- then the content straight on the page. No card: the heading
 * and the rule are the structure, the way a broadcast cuts to a new segment.
 */
export function Section({
  title,
  meta,
  children,
}: {
  title: string;
  meta?: string;
  children: React.ReactNode;
}) {
  return (
    <section aria-label={title} className="mt-14">
      <header className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1 border-b border-line pb-3">
        <h2 className="type-heading text-ink">{title}</h2>
        {/* Not tabular: this is prose. */}
        {meta && <p className="text-[13px] text-mute">{meta}</p>}
      </header>
      <div className="pt-5">{children}</div>
    </section>
  );
}
