import { RankingsWorkspace } from "@/components/rankings/RankingsWorkspace";

/**
 * Home is the rankings workspace. A visitor comes here to look at a board, so
 * the first screen is the board -- not an introduction to it.
 */
export default function Home() {
  return <RankingsWorkspace />;
}
