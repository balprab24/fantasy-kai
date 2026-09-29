import { Suspense } from "react";
import {
  RankingsWorkspace,
  RankingsWorkspaceFallback,
} from "@/components/rankings/RankingsWorkspace";

/**
 * Home is the rankings workspace. A visitor comes here to look at a board, so
 * the first screen is the board -- not an introduction to it.
 *
 * Suspense because the board reads its filters from the URL, and this route is
 * prerendered: without a boundary the build fails, and with one only the board
 * waits for the browser.
 */
export default function Home() {
  return (
    <Suspense fallback={<RankingsWorkspaceFallback />}>
      <RankingsWorkspace />
    </Suspense>
  );
}
