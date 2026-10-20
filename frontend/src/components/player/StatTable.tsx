import { Fragment } from "react";
import {
  GROUP_LABELS,
  formatCount,
  type ColumnGroup,
  type StatSource,
} from "@/lib/playerStats";
import type { HeatStep } from "@/lib/playerView";

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
 * A tile's ground on the four-step scale (`HeatStep`: green, yellow, orange,
 * red): a deep ground of the colour with the figure in the colour itself, so
 * a table of them stays quiet and the hue is carried by the number (5.2:1 at
 * the least, on green). A tile with no step -- a stat with no league cut-off
 * for the position, a game with no snap reading, a summary row -- sits on the table's own
 * faint ground in ink.
 * Cells print their figure without a colour of their own, so they take
 * whichever the tile sets.
 */
export const TILE: Record<HeatStep, string> = {
  0: "bg-heat-green/20 text-heat-green",
  1: "bg-heat-yellow/20 text-heat-yellow",
  2: "bg-heat-orange/20 text-heat-orange",
  3: "bg-heat-red/20 text-heat-red-text",
};
export const NEUTRAL_TILE = "bg-ink/[0.025]";

/**
 * The game log and the career table are one table with a different first few
 * columns, so they are one component: the box-score columns come from
 * `columnsFor` (lib/playerStats.ts), decided over every row, and line up the
 * same way in both.
 *
 * A grid of tiles: every figure sits on its own small ground, coloured by
 * where it falls -- green, yellow, orange, red -- so the big games and the
 * quiet ones stand out of a column without being read one by one. The caller
 * decides the step (`shade` for the box score, `tone` for a lead column);
 * this table only paints it. Every value is printed, so the colour is never
 * the only cue.
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
  shade,
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
  /** A box-score tile's step on the scale, or null for the faint ground. */
  shade?: (key: string, value: number, row: R) => HeatStep | null;
}) {
  // Lead group headers: merge runs of the same label.
  const leadGroups: { label: string; span: number }[] = [];
  for (const col of lead) {
    const last = leadGroups[leadGroups.length - 1];
    if (last && last.label === col.group) last.span++;
    else leadGroups.push({ label: col.group, span: 1 });
  }

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
            i === 0 ? pin : isFoot ? "bg-ink/[0.06]" : (col.tone?.(row) ?? NEUTRAL_TILE)
          } ${isFoot ? "font-semibold" : ""}`}
        >
          {isFoot && i === 0 && foot ? foot.label : col.cell(row)}
        </td>
      ))
      .concat(
        groups.flatMap((g) =>
          g.columns.map((c, ci) => {
            const v = c.value(stats);
            const step = isFoot ? null : (shade?.(c.key, v, row) ?? null);
            const ground = isFoot ? "bg-ink/[0.06]" : step === null ? NEUTRAL_TILE : TILE[step];
            return (
              <td
                key={c.key}
                className={`${tile} tabular text-right ${ground} ${step !== null ? "" : v === 0 ? "text-faint" : "text-ink"} ${isFoot ? "font-semibold" : ""} ${ci === 0 ? "border-l-[6px] border-l-transparent bg-clip-padding" : ""}`}
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
