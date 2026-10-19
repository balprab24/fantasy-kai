import { BUTTON_SECONDARY } from "./buttons";
import { Icon } from "./Icon";

/**
 * The three things a view can say instead of showing data: still working,
 * nothing here, or something failed. They are separate on purpose -- the
 * board this replaced said "pick a ruleset" when the API was down, which is a
 * default hiding an absence.
 *
 * A panel with an icon that names the kind of message, a title that says what
 * happened, a line that says what to do, and at most one action. A failure
 * tints toward danger; an empty state stays on the surface step.
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
      className={`flex gap-4 rounded-panel border px-5 py-6 text-sm sm:px-6 ${
        error ? "border-danger/30 bg-danger/[0.06]" : "border-line bg-surface"
      }`}
    >
      <span
        aria-hidden
        className={`mt-0.5 flex size-9 shrink-0 items-center justify-center rounded-full ${
          error ? "bg-danger/15 text-danger" : "bg-well text-mute"
        }`}
      >
        <Icon name={error ? "alert" : "info"} size={18} />
      </span>
      <div className="min-w-0">
        <p className={`text-[15px] font-semibold ${error ? "text-danger" : "text-ink"}`}>{title}</p>
        {children && <div className="mt-1 max-w-[56ch] leading-relaxed text-mute">{children}</div>}
        {action && (
          <button type="button" onClick={action.onClick} className={`mt-4 ${BUTTON_SECONDARY}`}>
            {action.label}
          </button>
        )}
      </div>
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`skeleton rounded-control ${className}`} />;
}
