import { Suspense } from "react";
import {
  RankingsWorkspace,
  RankingsWorkspaceFallback,
} from "@/components/rankings/RankingsWorkspace";

/** Same board as `/`; see `app/page.tsx` for why it sits in Suspense. */
export default function RankingsPage() {
  return (
    <Suspense fallback={<RankingsWorkspaceFallback />}>
      <RankingsWorkspace />
    </Suspense>
  );
}
