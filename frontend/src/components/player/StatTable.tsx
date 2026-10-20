import { Fragment } from "react";
import {
  GROUP_LABELS,
  formatCount,
  type ColumnGroup,
  type StatSource,
} from "@/lib/playerStats";
import { heatStep } from "@/lib/playerView";

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
  /** The cell's ground: a tile shaded by what the value means (a week's finish). */
  tone?: (row: R) => string | undefined;
}

/**
 * The shade of a box-score tile, from nothing to his biggest game in that
 * column (`heatStep`). Ink at rising strength rather than a hue: the hues are
 * taken (blue is "you are here", green is "a starter's week"), and a ramp of
 * one neutral reads the same to every eye.
 */
export const HEAT = ["bg-ink/[0.025]", "bg-ink/[0.06]", "bg-ink/[0.1]", "bg-ink/[0.15]", "bg-ink/[0.22]"];

/**
 * The game log and the career table are one table with a different first few
 * columns, so they are one component: the box-score columns come from
 * `columnsFor` (lib/playerStats.ts), decided over every row, and line up the
 * same way in both.
 *
 * A grid of tiles: every figure sits on its own small ground, shaded so the
 * big games stand out of the column without being read one by one. The
 * box-score tiles are shaded against his own range in that column (the API
 * sends no league distribution to shade against, and the legend says so);
 * lead columns bring their own ground (`tone`), which the fantasy columns use
 * for where the week finished at his position. Every value is printed, so the
 * shade is never the only cue.
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

  // Each column's biggest value over the body rows (never the summary row,
  // which would flatten every week against a season total).
  const columns = groups.flatMap((g) => g.columns);
  const max = new Map(columns.map((c) => [c.key, Math.max(0, ...rows.map((r) => c.value(source(r))))]));

  const tile = "h-8 rounded-[3px] px-2 whitespace-nowrap";
  // Pinned and opaque, so the columns scrolling under it on a phone do not
  // show through.
  const pin = "sticky left-0 z-[1] bg-surface";
  const renderRow = (row: R, isFoot: boolean) => {
    const stats = source(row);
    return lead
      .map((col, i) => (
        <td
          key={col.key}
          className={`${tile} ${col.align === "left" ? "text-left" : "text-right"} ${
            i === 0 ? pin : isFoot ? "bg-ink/[0.06]" : (col.tone?.(row) ?? "bg-ink/[0.025]")
          } ${isFoot ? "font-semibold" : ""}`}
        >
          {isFoot && i === 0 && foot ? foot.label : col.cell(row)}
        </td>
      ))
      .concat(
        groups.flatMap((g) =>
          g.columns.map((c, ci) => {
            const v = c.value(stats);
            const shade = isFoot ? "bg-ink/[0.06]" : HEAT[heatStep(v, max.get(c.key) ?? 0)];
            return (
              <td
                key={c.key}
                className={`${tile} tabular text-right ${shade} ${v === 0 ? "text-faint" : "text-ink"} ${isFoot ? "font-semibold" : ""} ${ci === 0 ? "border-l-[6px] border-l-transparent bg-clip-padding" : ""}`}
              >
                {formatCount(v)}
              </td>
            );
          }),
        ),
      );
  };

  return (
    <div className="overflow-x-auto px-3 pb-1 sm:px-4">
      {/* Separate borders and a 2px gap: each figure is a tile. Groups are
          told apart by a wider gap before their first column. */}
      <table className="w-full border-separate border-spacing-[2px] text-[13px]">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="font-label text-[11px] font-semibold text-faint">
            {leadGroups.map((g, i) => (
              <th
                key={`${g.label}-${i}`}
                scope="colgroup"
                colSpan={g.span}
                className={`h-6 px-2 text-left ${i === 0 ? pin : ""}`}
              >
                {g.label}
              </th>
            ))}
            {groups.map((g) => (
              <th
                key={g.group}
                scope="colgroup"
                colSpan={g.columns.length}
                className="h-6 border-l-[6px] border-l-transparent px-2 text-left"
              >
                {GROUP_LABELS[g.group]}
              </th>
            ))}
          </tr>
          <tr className="font-label text-[11px] text-mute">
            {lead.map((col, i) => (
              <th
                key={col.key}
                scope="col"
                title={col.title}
                className={`h-7 px-2 font-semibold whitespace-nowrap ${col.width ?? ""} ${col.align === "left" ? "text-left" : "text-right"} ${i === 0 ? pin : ""}`}
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
                    className={`h-7 px-2 text-right font-semibold whitespace-nowrap ${ci === 0 ? "border-l-[6px] border-l-transparent" : ""}`}
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
                className={`group [&>td]:transition-[filter] hover:[&>td]:brightness-125 ${extra.className ?? ""}`}
              >
                {renderRow(row, false)}
              </tr>
            );
          })}
        </tbody>
        {foot && (
          <tfoot>
            <tr>{renderRow(foot.row, true)}</tr>
          </tfoot>
        )}
      </table>
    </div>
  );
}
