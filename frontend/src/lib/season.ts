/**
 * Which NFL season "now" is, by the same rule the backend uses.
 *
 * Mirrors `IngestProperties.seasonFor` in the Java ingest package, which is
 * what `/api/v1/rankings` falls back to when no `season` is sent: a season is
 * named for the year it starts in, and from March on that is the current
 * calendar year. January and February belong to the previous season -- the
 * 2025 playoffs are played in 2026.
 *
 * Mirrored rather than fetched because the page needs the number before any
 * request goes out: for the season select, the header, and player links. If
 * the Java rule changes, change this with it.
 *
 * It names a season that may have no games yet -- from March until kickoff the
 * rule already points at the coming year. That is honest: the board says so and
 * offers the previous season, rather than quietly showing last year's data
 * under this year's name.
 */
export function currentSeason(now: Date = new Date()): number {
  return now.getMonth() >= 2 ? now.getFullYear() : now.getFullYear() - 1;
}

/** `fantasykai.ingest.first-season` in `application.yml`. */
export const FIRST_SEASON = 2020;

/** Every season the API holds, newest first. */
export function seasons(now: Date = new Date()): number[] {
  const out: number[] = [];
  for (let y = currentSeason(now); y >= FIRST_SEASON; y--) out.push(y);
  return out;
}
