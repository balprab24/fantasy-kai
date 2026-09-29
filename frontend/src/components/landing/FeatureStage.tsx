"use client";

import { LANDING_SECTIONS } from "@/lib/landing";
import { PhonePreview } from "./PhonePreview";
import { useActiveSection } from "./useActiveSection";

const SECTION_IDS = LANDING_SECTIONS.map((s) => s.id);

/**
 * The feature sections with the phone beside them. On a wide screen the phone
 * is pinned in its own column and shows whichever section is being read; on a
 * narrow one it sits once above the sections. One phone either way -- two
 * copies in the DOM would repeat every id inside it.
 *
 * The sections stay server-rendered and arrive as children; only the pairing
 * with the phone needs the browser.
 */
export function FeatureStage({ children }: { children: React.ReactNode }) {
  const active = useActiveSection(SECTION_IDS);
  return (
    <div className="mx-auto grid max-w-[1320px] gap-x-16 px-4 sm:px-6 lg:grid-cols-[minmax(0,1fr)_320px] lg:px-8">
      <div className="py-12 lg:sticky lg:top-24 lg:col-start-2 lg:row-start-1 lg:self-start lg:py-16">
        <PhonePreview section={active} />
      </div>
      <div className="lg:col-start-1 lg:row-start-1">{children}</div>
    </div>
  );
}
