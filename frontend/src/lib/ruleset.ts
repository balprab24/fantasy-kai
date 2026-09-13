import type { StatKey } from "./types";

/**
 * The ruleset document, exactly as `RulesetJson` parses it. Unknown keys are
 * rejected by the server rather than ignored, so this type is a contract and
 * not a suggestion -- an extra field here is a 422, which is the correct and
 * deliberate behaviour.
 */
export interface RulesetDoc {
  version: 1;
  base: Record<StatKey, number>;
  position_overrides?: Partial<Record<OverridePosition, Partial<Record<StatKey, number>>>>;
  bonuses?: BonusDoc[];
}

export interface BonusDoc {
  stat: StatKey;
  gte: number;
  points: number;
}

export const OVERRIDE_POSITIONS = ["QB", "RB", "WR", "TE", "K", "DST"] as const;
export type OverridePosition = (typeof OVERRIDE_POSITIONS)[number];

/** Grouped the way a league's settings page groups them, not in `StatKey` order. */
export const STAT_GROUPS: { heading: string; stats: [StatKey, string][] }[] = [
  {
    heading: "Passing",
    stats: [
      ["pass_yd", "Yards"],
      ["pass_td", "Touchdowns"],
      ["pass_int", "Interceptions"],
      ["pass_2pt", "Two-point conversions"],
    ],
  },
  {
    heading: "Rushing",
    stats: [
      ["rush_yd", "Yards"],
      ["rush_td", "Touchdowns"],
      ["rush_2pt", "Two-point conversions"],
    ],
  },
  {
    heading: "Receiving",
    stats: [
      ["rec", "Receptions"],
      ["rec_yd", "Yards"],
      ["rec_td", "Touchdowns"],
      ["rec_2pt", "Two-point conversions"],
    ],
  },
  {
    heading: "Other",
    stats: [
      ["fum_lost", "Fumbles lost"],
      ["ret_td", "Return touchdowns"],
    ],
  },
];

export const ALL_STATS: StatKey[] = STAT_GROUPS.flatMap((g) => g.stats.map(([key]) => key));

/** Half PPR, which is what most real leagues run — a sensible blank slate. */
export function startingRuleset(): RulesetDoc {
  return {
    version: 1,
    base: {
      pass_yd: 0.04,
      pass_td: 4,
      pass_int: -2,
      pass_2pt: 2,
      rush_yd: 0.1,
      rush_td: 6,
      rush_2pt: 2,
      rec: 0.5,
      rec_yd: 0.1,
      rec_td: 6,
      rec_2pt: 2,
      fum_lost: -2,
      ret_td: 6,
    },
  };
}

/**
 * Mirrors `RulesetValidator` so a mistake is caught before a round trip. The
 * server stays authoritative -- this exists to make the form answer quickly,
 * never to decide what is allowed.
 */
export const LIMITS = {
  rate: 10,
  bonusPoints: 20,
  bonusThreshold: 1000,
  maxBonuses: 20,
} as const;

export function localProblems(doc: RulesetDoc): string[] {
  const problems: string[] = [];
  for (const [stat, rate] of Object.entries(doc.base)) {
    if (!Number.isFinite(rate)) problems.push(`${stat} needs a number.`);
    else if (Math.abs(rate) > LIMITS.rate)
      problems.push(`${stat} must be between −${LIMITS.rate} and ${LIMITS.rate}.`);
  }
  for (const bonus of doc.bonuses ?? []) {
    if (Math.abs(bonus.points) > LIMITS.bonusPoints)
      problems.push(`A bonus is worth more than ${LIMITS.bonusPoints} points.`);
    if (bonus.gte < 0 || bonus.gte > LIMITS.bonusThreshold)
      problems.push(`A bonus threshold must be between 0 and ${LIMITS.bonusThreshold}.`);
  }
  if ((doc.bonuses ?? []).length > LIMITS.maxBonuses)
    problems.push(`At most ${LIMITS.maxBonuses} bonuses.`);
  return problems;
}
