"use client";

import { useState } from "react";
import { headshotUrl } from "@/lib/headshot";
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
 * CSS size, pixel size requested (2x, for a sharp image on a retina screen),
 * and the monogram's type size. Every size is a fixed box, so an image that
 * arrives late -- or never -- moves nothing around it.
 */
const SIZES = {
  sm: { box: "size-8", px: 64, text: "text-[11px]" },
  lg: { box: "size-24", px: 192, text: "text-2xl" },
} as const;

/**
 * A player's headshot, ringed in his position's hue, on a quiet plate -- the
 * source is a cut-out PNG, so the plate is what it sits on.
 *
 * Falls back to a monogram when there is no ESPN id and when the image fails:
 * a missing headshot is a 404 (measured), so `onError` fires and the same box
 * shows initials instead. The failure is remembered per URL, so a different
 * player in the same slot gets a fresh try. Always decorative -- the name is
 * right beside it.
 */
export function PlayerAvatar({
  name,
  position,
  espnId,
  size = "sm",
}: {
  name: string;
  position: string;
  espnId?: string | null;
  size?: keyof typeof SIZES;
}) {
  const { box, px, text } = SIZES[size];
  const src = headshotUrl(espnId, px);
  const [failed, setFailed] = useState<string | null>(null);
  const ring = positionHue(position).ring;
  const shape = `${box} shrink-0 overflow-hidden rounded-full bg-surface-2 ring-1 ${ring}`;

  if (src && src !== failed) {
    return (
      <span aria-hidden className={shape}>
        {/* eslint-disable-next-line @next/next/no-img-element -- ESPN resizes on their side; next/image would add a remote-host config and spend Vercel's optimisation quota to do it again */}
        <img
          src={src}
          alt=""
          width={px / 2}
          height={px / 2}
          // The workspace's large headshot is above the fold; a board row's
          // may be 500 rows down.
          loading={size === "lg" ? "eager" : "lazy"}
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
      className={`${shape} flex items-center justify-center font-semibold tracking-wide text-mute ${text}`}
    >
      {initials(name)}
    </span>
  );
}
