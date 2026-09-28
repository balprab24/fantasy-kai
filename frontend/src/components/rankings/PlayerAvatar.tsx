import { positionHue } from "./PositionBadge";

/** Name parts that are not a surname: "Marvin Harrison Jr." is MH, not MJ. */
const SUFFIXES = new Set(["jr", "jr.", "sr", "sr.", "ii", "iii", "iv", "v"]);

export function initials(name: string) {
  const parts = name.split(/\s+/).filter((p) => p && !SUFFIXES.has(p.toLowerCase()));
  if (parts.length === 0) return "?";
  const first = parts[0][0];
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

/**
 * A monogram disc, ringed in the position's hue.
 *
 * The API carries no headshot, and the design does not pretend it does. `src`
 * is the seam for one later: pass it and the same slot shows the image, with
 * the ring and size unchanged. Always decorative -- the name is right beside it.
 */
export function PlayerAvatar({
  name,
  position,
  src,
}: {
  name: string;
  position: string;
  src?: string;
}) {
  const ring = positionHue(position).ring;
  const shape = `size-7 shrink-0 rounded-full ring-1 ${ring}`;

  if (src) {
    // eslint-disable-next-line @next/next/no-img-element -- no image host is configured yet
    return <img src={src} alt="" aria-hidden className={`${shape} object-cover`} />;
  }
  return (
    <span
      aria-hidden
      className={`${shape} flex items-center justify-center bg-surface-2 text-[10.5px] font-semibold tracking-wide text-mute`}
    >
      {initials(name)}
    </span>
  );
}
