"use client";

import { useState } from "react";
import { headshotUrl } from "@/lib/headshot";

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
 * CSS size, pixel size requested (2x, for a sharp image on a retina screen),
 * and the monogram's type size. Every size is a fixed box, so an image that
 * arrives late -- or never -- moves nothing around it.
 */
const SIZES = {
  sm: { box: "size-8", px: 64, text: "text-[11px]" },
} as const;

/**
 * A player's face on the board: ESPN's cut-out, cropped square, on a quiet
 * well. No ring in the position's hue any more -- the letters beside it
 * already say the position, and a ring on every row was colour doing nothing.
 *
 * Falls back to a monogram when there is no ESPN id and when the image fails:
 * a missing headshot is a 404 (measured), so `onError` fires and the same box
 * shows initials instead. The failure is remembered per URL, so a different
 * player in the same slot gets a fresh try. Always decorative -- the name is
 * right beside it.
 */
export function PlayerAvatar({
  name,
  espnId,
  size = "sm",
}: {
  name: string;
  espnId?: string | null;
  size?: keyof typeof SIZES;
}) {
  const { box, px, text } = SIZES[size];
  const src = headshotUrl(espnId, px);
  const [failed, setFailed] = useState<string | null>(null);
  const shape = `${box} shrink-0 overflow-hidden rounded-full bg-well`;

  if (src && src !== failed) {
    return (
      <span aria-hidden className={shape}>
        {/* eslint-disable-next-line @next/next/no-img-element -- ESPN resizes on their side; next/image would add a remote-host config and spend Vercel's optimisation quota to do it again */}
        <img
          src={src}
          alt=""
          width={px / 2}
          height={px / 2}
          // A board row may be 500 rows down.
          loading="lazy"
          decoding="async"
          referrerPolicy="no-referrer"
          onError={() => setFailed(src)}
          className="size-full object-cover"
        />
      </span>
    );
  }
  return (
    <span
      aria-hidden
      className={`${shape} flex items-center justify-center font-label font-semibold text-mute ${text}`}
    >
      {initials(name)}
    </span>
  );
}
