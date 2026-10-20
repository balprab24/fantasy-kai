import type { Viewport } from "next";
import { Attribution } from "@/components/Attribution";
import { SiteHeader } from "@/components/landing/SiteHeader";

/** A phone's browser chrome takes the page's night ground. */
export const viewport: Viewport = { themeColor: "#060a12" };

/**
 * The frame outside the product: the landing page and the sign-in pages. No
 * rail -- there is nothing to navigate to until you are a member -- but the
 * attribution footer all the same, because the landing page shows nflverse
 * figures and the licence asks for credit wherever they appear.
 *
 * The same night ground as the product (owner brief 2026-10-19): the public
 * pages are the product's front room, so a visitor sees the product's own
 * colours, type and parts before they join -- and signing in changes the page,
 * not the world.
 */
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col bg-canvas text-ink">
      <SiteHeader />
      <main className="flex-1">{children}</main>
      <Attribution variant="site" />
    </div>
  );
}
