import type { Position, StatKey, Usage } from "./types";

/**
 * Which box-score columns a player's tables show, decided once for the game
 * log, the career table and the season line alike -- so a column never appears
 * in one and not the other.
 *
 * A column is data the API sent, never a figure made here: the stat and usage
 * values are counts, shown as they are. The one sum is 2PT, which folds three
 * conversion stats into one cell; it adds counts, never points.
 */

export type StatGroup = "passing" | "rushing" | "receiving" | "other";

export interface StatSource {
  stats: Record<StatKey, number>;
  usage: Usage;
}

export interface StatColumn {
  key: string;
  group: StatGroup;
  /** Short header, e.g. "Yd". The group header says which yards. */
  label: string;
  /** Full name, for the header's tooltip and for screen readers. */
  title: string;
  value: (row: StatSource) => number;
}

/**
 * Every scorable stat has a home here, and `satisfies` makes that a build
 * error rather than a convention: add a `StatKey` and this object stops
 * compiling until someone decides where its column goes.
 */
const HOME = {
  pass_yd: "passing",
  pass_td: "passing",
  pass_int: "passing",
  pass_2pt: "other",
  rush_yd: "rushing",
  rush_td: "rushing",
  rush_2pt: "other",
  rec: "receiving",
  rec_yd: "receiving",
  rec_td: "receiving",
  rec_2pt: "other",
  fum_lost: "other",
  ret_td: "other",
} as const satisfies Record<StatKey, StatGroup>;

const stat = (key: StatKey) => (row: StatSource) => row.stats[key] ?? 0;

export const COLUMNS: StatColumn[] = [
  { key: "pass_cmp", group: "passing", label: "Cmp", title: "Completions", value: (r) => r.usage.passCmp },
  { key: "pass_att", group: "passing", label: "Att", title: "Pass attempts", value: (r) => r.usage.passAtt },
  { key: "pass_yd", group: HOME.pass_yd, label: "Yd", title: "Passing yards", value: stat("pass_yd") },
  { key: "pass_td", group: HOME.pass_td, label: "TD", title: "Passing touchdowns", value: stat("pass_td") },
  { key: "pass_int", group: HOME.pass_int, label: "Int", title: "Interceptions thrown", value: stat("pass_int") },
  { key: "rush_att", group: "rushing", label: "Car", title: "Carries", value: (r) => r.usage.rushAtt },
  { key: "rush_yd", group: HOME.rush_yd, label: "Yd", title: "Rushing yards", value: stat("rush_yd") },
  { key: "rush_td", group: HOME.rush_td, label: "TD", title: "Rushing touchdowns", value: stat("rush_td") },
  { key: "targets", group: "receiving", label: "Tgt", title: "Targets", value: (r) => r.usage.targets },
  { key: "rec", group: HOME.rec, label: "Rec", title: "Receptions", value: stat("rec") },
  { key: "rec_yd", group: HOME.rec_yd, label: "Yd", title: "Receiving yards", value: stat("rec_yd") },
  { key: "rec_td", group: HOME.rec_td, label: "TD", title: "Receiving touchdowns", value: stat("rec_td") },
  { key: "fum_lost", group: HOME.fum_lost, label: "FL", title: "Fumbles lost", value: stat("fum_lost") },
  {
    key: "two_pt",
    group: "other",
    label: "2PT",
    title: "Two-point conversions (passing, rushing and receiving)",
    value: (r) => (r.stats.pass_2pt ?? 0) + (r.stats.rush_2pt ?? 0) + (r.stats.rec_2pt ?? 0),
  },
  { key: "ret_td", group: HOME.ret_td, label: "Ret TD", title: "Kick and punt return touchdowns", value: stat("ret_td") },
];

export const GROUP_LABELS: Record<StatGroup, string> = {
  passing: "Passing",
  rushing: "Rushing",
  receiving: "Receiving",
  other: "Other",
};

/** Always shown for the position, in this order, even in a quiet season. */
const PRIMARY: Record<Position, StatGroup[]> = {
  QB: ["passing", "rushing"],
  RB: ["rushing", "receiving"],
  WR: ["receiving"],
  TE: ["receiving"],
};

const ORDER: StatGroup[] = ["passing", "rushing", "receiving"];

export interface ColumnGroup {
  group: StatGroup;
  columns: StatColumn[];
}

/**
 * The columns for a table of `rows`. A position's primary groups always show;
 * any other group shows when some row in the table has a non-zero value in it
 * (a receiver's carries, a running back's pass); the "other" columns -- fumbles,
 * conversions, return scores -- each show only when one happened. Decided over
 * every row of the table, so the columns line up down the whole of it.
 */
export function columnsFor(position: string, rows: StatSource[]): ColumnGroup[] {
  const any = (col: StatColumn) => rows.some((row) => col.value(row) !== 0);
  const primary = Object.hasOwn(PRIMARY, position) ? PRIMARY[position as Position] : [];
  const groups: StatGroup[] = [
    ...primary,
    ...ORDER.filter(
      (g) => !primary.includes(g) && COLUMNS.some((c) => c.group === g && any(c)),
    ),
  ];

  const out: ColumnGroup[] = groups.map((group) => ({
    group,
    columns: COLUMNS.filter((c) => c.group === group),
  }));
  const other = COLUMNS.filter((c) => c.group === "other" && any(c));
  if (other.length) out.push({ group: "other", columns: other });
  return out;
}

/** A count for a table cell: thousands separated, a real minus sign. */
export function formatCount(n: number): string {
  const s = Math.abs(n).toLocaleString("en-US", { maximumFractionDigits: 1 });
  return n < 0 ? `−${s}` : s;
}
