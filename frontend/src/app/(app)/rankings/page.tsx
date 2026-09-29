import { Suspense } from "react";
import {
  RankingsWorkspace,
  RankingsWorkspaceFallback,
} from "@/components/rankings/RankingsWorkspace";

/**
 * The rankings workspace -- the first screen of the product once signed in.
 *
 * Suspense because the board reads its filters from the URL, and this route is
 * prerendered: without a boundary the build fails, and with one only the board
 * waits for the browser.
 */
export default function RankingsPage() {
  return (
    <Suspense fallback={<RankingsWorkspaceFallback />}>
      <RankingsWorkspace />
    </Suspense>
  );
}
