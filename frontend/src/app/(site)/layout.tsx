import type { Viewport } from "next";
import { Attribution } from "@/components/Attribution";
import { SiteHeader } from "@/components/landing/SiteHeader";

/** A phone's browser chrome takes the page's ground, not the product's black. */
export const viewport: Viewport = { themeColor: "#f1f4f9" };

/**
 * The frame outside the product: the landing page and the sign-in pages. No
 * rail -- there is nothing to navigate to until you are a member -- but the
 * attribution footer all the same, because the landing page shows nflverse
 * figures and the licence asks for credit wherever they appear.
 *
 * In Daylight (globals.css, "Two modes"): the public pages are where a visitor
 * decides, in daylight, whether to trust the tool. Signing in is the moment the
 * product turns to Prime time, because the product is where they then work.
 */
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="daylight flex min-h-dvh flex-col bg-canvas text-ink">
      <SiteHeader />
      <main className="flex-1">{children}</main>
      <Attribution variant="site" />
    </div>
  );
}
