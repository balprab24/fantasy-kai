"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AccountButton } from "./AccountButton";
import { MobileNav } from "./MobileNav";
import { PRIMARY_NAV, isActive } from "./nav";
import { Wordmark } from "./Wordmark";

/** Compact member bar; desktop section navigation lives in the icon rail. */
export function TopBar() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-veil backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[1440px] items-center gap-3 px-4 sm:px-6 md:gap-8 lg:px-8">
        <Wordmark href="/rankings" />
        {/* On phones the menu holds every section; the bar stays one line. */}
        <span className="hidden text-sm font-semibold text-ink lg:block">{PRIMARY_NAV.find((item) => isActive(item.href, pathname))?.label ?? "Player"}</span>
        <nav aria-label="Primary" className="hidden min-w-0 items-center sm:flex lg:hidden">
          {PRIMARY_NAV.map((item) => {
            const active = isActive(item.href, pathname);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`relative flex h-14 items-center px-2.5 text-[15px] transition-colors sm:px-3.5 ${
                  active
                    ? "font-semibold text-ink after:slash after:absolute after:inset-x-2 after:-bottom-px after:h-[3px] after:bg-energy sm:after:inset-x-3"
                    : "text-mute hover:text-ink"
                }`}
              >
                {item.label}
              </Link>
            );
          })}
        </nav>
        <div className="ml-auto flex items-center">
          <div className="hidden lg:block">
            <AccountButton />
          </div>
          <div className="lg:hidden">
            <MobileNav />
          </div>
        </div>
      </div>
    </header>
  );
}
