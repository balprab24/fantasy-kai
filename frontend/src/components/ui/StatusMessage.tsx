import { BUTTON_SECONDARY } from "./buttons";

/**
 * The three things a board can say instead of showing rows: still working,
 * nothing matched, or something failed. They are separate on purpose -- the
 * board this replaced said "pick a ruleset" when the API was down, which is a
 * default hiding an absence.
 *
 * A tonal block, not a bordered card: the surface step says "this is a
 * message", and a failure tints it toward danger rather than drawing a box.
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
      className={`rounded-control px-5 py-6 text-sm ${error ? "bg-danger/[0.07]" : "bg-surface"}`}
    >
      <p className={`font-semibold ${error ? "text-danger" : "text-ink"}`}>{title}</p>
      {children && <div className="mt-1 max-w-[52ch] leading-relaxed text-mute">{children}</div>}
      {action && (
        <button type="button" onClick={action.onClick} className={`mt-4 ${BUTTON_SECONDARY}`}>
          {action.label}
        </button>
      )}
    </div>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`skeleton rounded-control ${className}`} />;
}
