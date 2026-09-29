import type { SeasonType } from "./types";

/**
 * Small facts about a player page that are presentation, not scoring: which
 * season to open on, how old someone is today, what a playoff round is called.
 * Every point on the page comes from the API; nothing here adds or rounds one.
 */

/** Whole years on `today`. `birthDate` is ISO `YYYY-MM-DD`. */
export function ageOn(birthDate: string | null, today: Date): number | null {
  const m = birthDate ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(birthDate) : null;
  if (!m) return null;
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  let age = today.getFullYear() - year;
  const beforeBirthday =
    today.getMonth() + 1 < month || (today.getMonth() + 1 === month && today.getDate() < day);
  if (beforeBirthday) age -= 1;
  return age;
}

/**
 * The season a player page opens on.
 *
 * An explicit request wins, even for a season he did not play -- the page says
 * so rather than quietly showing another year. Otherwise the current season if
 * he has regular-season games in it, else his most recent one. `available` is
 * newest first, as the career endpoint returns it.
 */
export function pickSeason(
  requested: number | null,
  available: number[],
  current: number,
): number | null {
  if (requested !== null) return requested;
  if (available.includes(current)) return current;
  return available[0] ?? null;
}

const ROUNDS: Record<string, string> = {
  WC: "Wild card",
  DIV: "Divisional",
  CON: "Conference",
  SB: "Super Bowl",
};

/** "Wild card" for `WC`; the raw value for anything unrecognised; null for the regular season. */
export function playoffRound(seasonType: SeasonType): string | null {
  if (seasonType === "REG") return null;
  return ROUNDS[seasonType] ?? seasonType;
}

/** "vs NO", "@ BUF", or just the opponent when the venue is unknown. */
export function matchup(opponent: string | null, home: boolean | null): string {
  if (!opponent) return "—";
  if (home === null) return opponent;
  return home ? `vs ${opponent}` : `@ ${opponent}`;
}
