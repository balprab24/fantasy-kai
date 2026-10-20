import type { StatKey } from "./types";

/**
 * The landing's ruleset sheet ("Your league. Everywhere."): every rate a
 * ruleset sets, grouped the way a league's settings page groups them, with
 * the values worked out from a real set of rates rather than typed as text.
 * Pure and import-free, so `node --test` can hold it to the V3 migration.
 *
 * A row may stand for more than one stat ("2-pt conversion" is passing,
 * rushing and receiving) only while they share one rate; `sheetRows` refuses
 * otherwise, so a merged row can never hide a difference.
 */
export const SHEET: { heading: string; rows: { label: string; stats: StatKey[] }[] }[] = [
  {
    heading: "Passing",
    rows: [
      { label: "Yards", stats: ["pass_yd"] },
      { label: "Touchdown", stats: ["pass_td"] },
      { label: "Interception", stats: ["pass_int"] },
    ],
  },
  {
    heading: "Rushing",
    rows: [
      { label: "Yards", stats: ["rush_yd"] },
      { label: "Touchdown", stats: ["rush_td"] },
    ],
  },
  {
    heading: "Receiving",
    rows: [
      { label: "Catch", stats: ["rec"] },
      { label: "Yards", stats: ["rec_yd"] },
      { label: "Touchdown", stats: ["rec_td"] },
    ],
  },
  {
    heading: "Other",
    rows: [
      { label: "Fumble lost", stats: ["fum_lost"] },
      { label: "Return touchdown", stats: ["ret_td"] },
      { label: "2-pt conversion", stats: ["pass_2pt", "rush_2pt", "rec_2pt"] },
    ],
  },
];

const YARDS: ReadonlySet<StatKey> = new Set(["pass_yd", "rush_yd", "rec_yd"]);

/** A rate as a league's settings page says it: "1 per 25" yards, "½", "−2". */
export function sheetValue(stat: StatKey, rate: number): string {
  if (YARDS.has(stat) && rate > 0 && rate < 1) return `1 per ${Math.round(1 / rate)}`;
  if (rate === 0.5) return "½";
  return rate < 0 ? `−${Math.abs(rate)}` : String(rate);
}

export function sheetRows(rates: Record<StatKey, number>): { heading: string; rows: [string, string][] }[] {
  return SHEET.map((group) => ({
    heading: group.heading,
    rows: group.rows.map(({ label, stats }) => {
      const rate = rates[stats[0]];
      if (stats.some((s) => rates[s] !== rate)) {
        throw new Error(`"${label}" merges ${stats.join(", ")}, which are not one rate here`);
      }
      return [label, sheetValue(stats[0], rate)] as [string, string];
    }),
  }));
}
