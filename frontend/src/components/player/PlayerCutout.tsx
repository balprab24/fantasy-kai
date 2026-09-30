"use client";

import { useState } from "react";
import { CUTOUT_ASPECT, headshotCutoutUrl } from "@/lib/headshot";
import { initials } from "../rankings/PlayerAvatar";

/**
 * The player, standing on his plate: ESPN's whole head-and-shoulders cut-out,
 * never a circle cropped out of it. The image's alpha channel is the real
 * outline of the player, and its flat bottom edge is placed on the plate's
 * bottom edge, so the crop line reads as the plate and no mask is needed.
 *
 * Decorative (`alt=""`): the name is the page's heading, right beside it. On a
 * missing id or a failed load (a missing headshot is a 404, measured) the same
 * box shows the player's initials in the display face, faint, so the plate
 * keeps its shape and nothing moves.
 */
export function PlayerCutout({ name, espnId }: { name: string; espnId: string | null }) {
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
      <span className="type-display translate-y-[8%] text-[9rem] text-white/[0.07]">{initials(name)}</span>
    </div>
  );
}
