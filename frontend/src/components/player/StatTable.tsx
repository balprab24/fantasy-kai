import { Fragment } from "react";
import {
  GROUP_LABELS,
  formatCount,
  type ColumnGroup,
  type StatSource,
} from "@/lib/playerStats";

/** A column before the box score: week, opponent, points, rank... */
export interface LeadColumn<R> {
  key: string;
  label: string;
  /** Full name for the header tooltip, when the label is an abbreviation. */
  title?: string;
  /** The group header above it; consecutive columns sharing one merge. */
  group: string;
  align?: "left" | "right";
  /** A width hint, so a short column does not soak up a wide table's slack. */
  width?: string;
  cell: (row: R) => React.ReactNode;
}

/**
 * The game log and the career table are one table with a different first few
 * columns, so they are one component: the box-score columns come from
 * `columnsFor` (lib/playerStats.ts), decided over every row, and line up the
 * same way in both.
 *
 * Wide by nature -- a running back's log is eleven numbers a week -- so it
 * scrolls sideways inside its own box on a narrow screen, with the first
 * column pinned, rather than shrinking the type until nobody can read it.
 */
export function StatTable<R>({
  caption,
  lead,
  groups,
  rows,
  source,
  rowKey,
  rowProps,
  foot,
}: {
  caption: string;
  lead: LeadColumn<R>[];
  groups: ColumnGroup[];
  rows: R[];
  source: (row: R) => StatSource;
  rowKey: (row: R) => string;
  rowProps?: (row: R) => { className?: string; "aria-current"?: boolean };
  /** A summary row -- supplied whole by the caller, never summed here. */
  foot?: { row: R; label: string };
}) {
  // Lead group headers: merge runs of the same label.
  const leadGroups: { label: string; span: number }[] = [];
  for (const col of lead) {
    const last = leadGroups[leadGroups.length - 1];
    if (last && last.label === col.group) last.span++;
    else leadGroups.push({ label: col.group, span: 1 });
  }

  const cellBase = "h-9 px-2 whitespace-nowrap";
  // Pinned and opaque, so the columns scrolling under it on a phone do not
  // show through; it follows the row's hover to the same opaque step.
  const pin = "sticky left-0 z-[1] bg-canvas group-hover:bg-well";
  const renderRow = (row: R, isFoot: boolean) => {
    const stats = source(row);
    return lead
      .map((col, i) => (
        <td
          key={col.key}
          className={`${cellBase} ${col.align === "left" ? "text-left" : "text-right"} ${i === 0 ? `${pin} pl-3` : ""} ${isFoot ? "font-medium" : ""}`}
        >
          {isFoot && i === 0 && foot ? foot.label : col.cell(row)}
        </td>
      ))
      .concat(
        groups.flatMap((g) =>
          g.columns.map((c, ci) => {
            const v = c.value(stats);
            return (
              <td
                key={c.key}
                className={`${cellBase} tabular text-right ${v === 0 ? "text-faint" : "text-ink"} ${ci === 0 ? "pl-4" : ""}`}
              >
                {formatCount(v)}
              </td>
            );
          }),
        ),
      );
  };

  return (
    <div className="overflow-x-auto">
      {/* Separate borders, drawn on cells: a sticky cell in a collapsed-border
          table leaves a hairline seam that scrolled columns show through.
          Groups are told apart by a wider gap, not a vertical rule. */}
      <table className="w-full border-separate border-spacing-0 text-[13px]">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="font-label text-[12px] font-semibold text-faint">
            {leadGroups.map((g, i) => (
              <th
                key={`${g.label}-${i}`}
                scope="colgroup"
                colSpan={g.span}
                className={`h-7 px-2 text-left ${i === 0 ? `${pin} pl-3` : ""}`}
              >
                {g.label}
              </th>
            ))}
            {groups.map((g) => (
              <th
                key={g.group}
                scope="colgroup"
                colSpan={g.columns.length}
                className="h-7 px-2 pl-4 text-left"
              >
                {GROUP_LABELS[g.group]}
              </th>
            ))}
          </tr>
          <tr className="font-label text-[12px] text-mute [&>th]:border-b [&>th]:border-line">
            {lead.map((col, i) => (
              <th
                key={col.key}
                scope="col"
                title={col.title}
                className={`h-8 px-2 font-semibold whitespace-nowrap ${col.width ?? ""} ${col.align === "left" ? "text-left" : "text-right"} ${i === 0 ? `${pin} pl-3` : ""}`}
              >
                {col.label}
              </th>
            ))}
            {groups.map((g) => (
              <Fragment key={g.group}>
                {g.columns.map((c, ci) => (
                  <th
                    key={c.key}
                    scope="col"
                    title={c.title}
                    className={`h-8 px-2 text-right font-semibold whitespace-nowrap ${ci === 0 ? "pl-4" : ""}`}
                  >
                    <abbr title={c.title} className="no-underline">
                      {c.label}
                    </abbr>
                  </th>
                ))}
              </Fragment>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const extra = rowProps?.(row) ?? {};
            return (
              <tr
                key={rowKey(row)}
                aria-current={extra["aria-current"] || undefined}
                className={`group [&>td]:border-b [&>td]:border-line [&>td]:transition-colors hover:[&>td]:bg-well ${extra.className ?? ""}`}
              >
                {renderRow(row, false)}
              </tr>
            );
          })}
        </tbody>
        {foot && (
          <tfoot>
            {/* Opaque, so the pinned first cell hides what scrolls under it. */}
            <tr className="[&>td]:bg-surface [&>td]:font-semibold">
              {renderRow(foot.row, true)}
            </tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}
