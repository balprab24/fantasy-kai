/**
 * A section of the player view: a rounded panel with its name in the display
 * voice, an optional note on what it covers or a control for it, and its
 * content straight on the panel -- never a box inside the box.
 */
export function Panel({
  title,
  meta,
  action,
  children,
  className = "",
  bodyClassName = "px-5 pb-5 sm:px-6 sm:pb-6",
}: {
  title: string;
  meta?: React.ReactNode;
  /** A control for the panel's own view (per game or totals), at the header's end. */
  action?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  bodyClassName?: string;
}) {
  return (
    <section aria-label={title} className={`min-w-0 rounded-panel border border-line bg-surface ${className}`}>
      <header className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 pt-4 pb-3 sm:px-6">
        <div className="flex min-w-0 flex-wrap items-baseline gap-x-4 gap-y-1">
          <h2 className="type-heading text-ink">{title}</h2>
          {meta && <p className="text-[13px] text-mute">{meta}</p>}
        </div>
        {action}
      </header>
      <div className={bodyClassName}>{children}</div>
    </section>
  );
}
