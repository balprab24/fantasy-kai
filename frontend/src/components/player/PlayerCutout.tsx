"use client";

import { useState } from "react";
import { CUTOUT_ASPECT, headshotCutoutUrl } from "@/lib/headshot";
import { initials } from "../rankings/PlayerAvatar";
import { positionGlow } from "../rankings/PositionBadge";

/**
 * The player, standing on his stage: ESPN's whole head-and-shoulders cut-out,
 * never a circle cropped out of it. The image's alpha channel is the real
 * outline of the player, and its flat bottom edge sits on the stage's bottom
 * edge, so the crop line reads as the floor.
 *
 * Decorative (`alt=""`): the name is the page's heading, right beside it. On a
 * missing id or a failed load (a missing headshot is a 404, measured) the same
 * box shows his initials as outlined display type in his position's hue -- a
 * deliberate fallback, not a hole -- so the stage keeps its shape and nothing
 * moves.
 */
export function PlayerCutout({
  name,
  espnId,
  position = "",
}: {
  name: string;
  espnId: string | null;
  position?: string;
}) {
  const src = headshotCutoutUrl(espnId, 600);
  const [failed, setFailed] = useState<string | null>(null);

  if (src && src !== failed) {
    return (
      // eslint-disable-next-line @next/next/no-img-element -- ESPN resizes on their side; next/image would add a remote-host config and spend Vercel's optimisation quota to do it again
      <img
        src={src}
        alt=""
        width={600}
        height={Math.round(600 * CUTOUT_ASPECT)}
        loading="eager"
        decoding="async"
        referrerPolicy="no-referrer"
        onError={() => setFailed(src)}
        className="block h-auto w-full select-none"
        draggable={false}
      />
    );
  }
  return (
    <div aria-hidden className="flex aspect-[600/436] w-full items-end justify-center overflow-hidden">
      <span
        className="type-display translate-y-[10%] text-[clamp(7rem,22vw,11rem)] text-transparent"
        style={{ WebkitTextStroke: `2px ${positionGlow(position, 0.55)}` }}
      >
        {initials(name)}
      </span>
    </div>
  );
}
