"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { MobileNav } from "./MobileNav";
import { PRIMARY_NAV, isActive } from "./nav";
import { Wordmark } from "./Wordmark";

/**
 * The product's bar below md: the wordmark, the two sections that exist, and a
 * menu for everything else (`MobileNav`). From md the `Sidebar` carries the
 * same navigation and this bar is not drawn.
 *
 * No border: it sits on the same black as the page, and only content
 * scrolling beneath it meets the translucent ground and the blur. The same
 * vocabulary as the landing page's bar, so the two read as one product.
 */
export function TopBar() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-30 bg-canvas/80 backdrop-blur-md md:hidden">
      <div className="mx-auto flex h-14 max-w-[1320px] items-center gap-3 px-4 sm:px-6">
        <Wordmark href="/rankings" />
        <nav aria-label="Primary" className="flex min-w-0 items-center">
          {PRIMARY_NAV.map((item) => {
            const active = isActive(item.href, pathname);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`relative flex h-14 items-center px-2.5 text-sm transition-colors sm:px-3 ${
                  active
                    ? "font-semibold text-ink after:absolute after:inset-x-2.5 after:bottom-0 after:h-0.5 after:bg-energy sm:after:inset-x-3"
                    : "text-mute hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="ml-auto flex items-center">
          <MobileNav />
        </div>
      </div>
    </header>
  );
}
