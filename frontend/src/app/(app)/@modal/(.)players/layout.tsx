import { PlayerOverlay } from "@/components/player/PlayerOverlay";

/**
 * A player opened from inside the product -- a row on the board, a mover --
 * is intercepted here and shown over the page instead of replacing it. The URL
 * is the player's own (`/players/[id]`), so it can be shared, and a refresh or
 * a cold link renders the full page instead. The overlay lives in this layout,
 * not the page, so stepping to the previous or next player keeps it open
 * rather than closing and reopening it.
 */
export default function PlayerOverlayLayout({ children }: { children: React.ReactNode }) {
  return <PlayerOverlay>{children}</PlayerOverlay>;
}
