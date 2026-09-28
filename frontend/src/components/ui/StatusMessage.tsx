/**
 * The three things a board can say instead of showing rows: still working,
 * nothing matched, or something failed. They are separate on purpose -- the
 * board this replaced said "pick a ruleset" when the API was down, which is a
 * default hiding an absence.
 */
export function StatusMessage({
  tone,
  title,
  children,
  action,
}: {
  tone: "info" | "error";
  title: string;
  children?: React.ReactNode;
  action?: { label: string; onClick: () => void };
}) {
  const error = tone === "error";
  return (
    <div
      role={error ? "alert" : "status"}
      className={`rounded-lg border px-5 py-8 text-sm ${
        error ? "border-stat-loss/40 bg-stat-loss/5" : "border-line bg-raised/60"
      }`}
    >
      <p className={`font-medium ${error ? "text-stat-loss" : "text-ink"}`}>{title}</p>
      {children && <div className="mt-1 max-w-[62ch] leading-relaxed text-mute">{children}</div>}
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="mt-4 h-9 rounded-md border border-line-strong px-3 text-sm text-ink hover:bg-surface-2"
        >
          {action.label}
        </button>
      )}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`skeleton rounded ${className}`} />;
}
