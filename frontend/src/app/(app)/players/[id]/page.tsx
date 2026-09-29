"use client";

import { useParams } from "next/navigation";
import { PlayerWorkspace } from "@/components/player/PlayerWorkspace";

/**
 * A player's page. A route rather than a modal over the board: it deep-links,
 * survives a refresh, and needs the full width for a game log -- and the board
 * keeps its place through the URL and `lib/boardReturn.ts` instead.
 *
 * Keyed on the id so a different player never inherits the last one's local
 * state (a failed headshot, the chart's focused week).
 */
export default function PlayerPage() {
  const { id } = useParams<{ id: string }>();
  // Fifteen digits at most: still an exact integer in a JavaScript number.
  const playerId = /^[1-9][0-9]{0,14}$/.test(id) ? Number(id) : 0;
  return <PlayerWorkspace key={playerId} playerId={playerId} />;
}
