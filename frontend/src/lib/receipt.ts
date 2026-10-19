import type { StatKey } from "./types";

/**
 * A stat line taken apart under a preset: each stat it recorded, its rate,
 * and what that is worth -- the landing's receipt. Pure and import-free, so
 * `node --test` can hold it to the API.
 *
 * This prices a line client-side, which the product otherwise never does, and
 * it is allowed here for one reason: the receipt's total is checked against
 * the points the API returned for the same game under the same preset
 * (`tests/lib.test.ts`, which also holds these rates to the V3 migration), so
 * the derivation on screen can never disagree with the engine. It only covers
 * the three presets -- rates are all linear there, with no bonuses -- and is
 * never used for a member's own ruleset, whose rules the API does not return.
 */

/** V3's rates for every stat but the one the presets differ on. */
const V3_BASE: Record<StatKey, number> = {
  pass_yd: 0.04,
  pass_td: 4,
  pass_int: -2,
  pass_2pt: 2,
  rush_yd: 0.1,
  rush_td: 6,
  rush_2pt: 2,
  rec: 0,
  rec_yd: 0.1,
  rec_td: 6,
  rec_2pt: 2,
  fum_lost: -2,
  ret_td: 6,
};

/** 0 PPR, Half PPR and PPR: V3's base with a catch at 0, 0.5 or 1. */
export function presetRates(rec: 0 | 0.5 | 1): Record<StatKey, number> {
  return { ...V3_BASE, rec };
}

export const STAT_NAMES: Record<StatKey, string> = {
  pass_yd: "Passing yards",
  pass_td: "Passing touchdowns",
  pass_int: "Interceptions",
  pass_2pt: "Passing 2-pt conversions",
  rush_yd: "Rushing yards",
  rush_td: "Rushing touchdowns",
  rush_2pt: "Rushing 2-pt conversions",
  rec: "Receptions",
  rec_yd: "Receiving yards",
  rec_td: "Receiving touchdowns",
  rec_2pt: "Receiving 2-pt conversions",
  fum_lost: "Fumbles lost",
  ret_td: "Return touchdowns",
};

export interface ReceiptLine {
  stat: StatKey;
  count: number;
  rate: number;
  points: number;
}

/**
 * Every stat the line recorded, priced. A catch is listed even at a rate of
 * zero -- under 0 PPR the receipt says "12 x 0 = 0", which is the point.
 */
export function priceLine(stats: Record<StatKey, number>, rates: Record<StatKey, number>) {
  const lines: ReceiptLine[] = (Object.keys(rates) as StatKey[])
    .filter((stat) => stats[stat] !== 0)
    .map((stat) => ({ stat, count: stats[stat], rate: rates[stat], points: stats[stat] * rates[stat] }));
  return { lines, total: lines.reduce((sum, l) => sum + l.points, 0) };
}
