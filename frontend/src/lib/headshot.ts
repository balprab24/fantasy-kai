/**
 * A player's headshot, derived from his ESPN id -- never stored.
 *
 * The id is already on the player (nflverse ships it; 1,306 of the 1,308
 * QB/RB/WR/TE with a stat line carry one, 2026-09-28), and the image is a pure
 * function of it, so a stored URL would only be a second copy that can drift.
 *
 * ESPN's `combiner` endpoint resizes and crops on their side: a full headshot
 * is ~237 KB, a 64px square ~6 KB, and a board can show 600 rows. `px` is the
 * pixel size requested -- pass twice the CSS size so it is sharp on a 2x
 * screen. A missing headshot is a 404 (measured), so `<img onError>` is a
 * reliable fallback signal.
 */
const ESPN_ID = /^[0-9]{1,12}$/;

export function headshotUrl(espnId: string | null | undefined, px: number): string | null {
  // The API already sends digits or null; checked again because this string is
  // about to become part of a URL.
  if (!espnId || !ESPN_ID.test(espnId)) return null;
  const size = Math.max(16, Math.min(512, Math.round(px)));
  return (
    "https://a.espncdn.com/combiner/i?img=/i/headshots/nfl/players/full/" +
    `${espnId}.png&w=${size}&h=${size}&scale=crop&cquality=80`
  );
}

/** ESPN's full headshot is a 600x436 head-and-shoulders cut-out with a true alpha channel (measured). */
export const CUTOUT_ASPECT = 436 / 600;

/**
 * The whole cut-out, not a square crop of it: the player page stands it on
 * its plate, where the image's flat bottom edge becomes the plate's edge. The
 * combiner keeps the alpha channel at any size, but asking past the native
 * 600px only upscales (660px came back 320 KB against the native 260 KB), so
 * `px` -- device pixels wide -- is capped there.
 */
export function headshotCutoutUrl(espnId: string | null | undefined, px: number): string | null {
  if (!espnId || !ESPN_ID.test(espnId)) return null;
  const w = Math.max(120, Math.min(600, Math.round(px)));
  const h = Math.round(w * CUTOUT_ASPECT);
  return `https://a.espncdn.com/combiner/i?img=/i/headshots/nfl/players/full/${espnId}.png&w=${w}&h=${h}`;
}
