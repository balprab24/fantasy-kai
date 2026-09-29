import { Attribution } from "@/components/Attribution";
import { SiteHeader } from "@/components/landing/SiteHeader";

/**
 * The frame outside the product: the landing page and the sign-in pages. No
 * rail -- there is nothing to navigate to until you are a member -- but the
 * attribution footer all the same, because the landing page shows nflverse
 * figures and the licence asks for credit wherever they appear.
 */
export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col">
      <SiteHeader />
      <main className="flex-1">{children}</main>
      <Attribution />
    </div>
  );
}
