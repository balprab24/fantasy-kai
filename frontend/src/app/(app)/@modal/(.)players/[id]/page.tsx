"use client";

import { useParams } from "next/navigation";
import { PlayerWorkspace } from "@/components/player/PlayerWorkspace";
import { useOverlayClose } from "@/components/player/PlayerOverlay";

/** The player, as the overlay shows him; the same view as the full page. */
export default function PlayerOverlayPage() {
  const { id } = useParams<{ id: string }>();
  const close = useOverlayClose();
  // Fifteen digits at most: still an exact integer in a JavaScript number.
  const playerId = /^[1-9][0-9]{0,14}$/.test(id) ? Number(id) : 0;
  return <PlayerWorkspace key={playerId} playerId={playerId} variant="overlay" onClose={close} />;
}
