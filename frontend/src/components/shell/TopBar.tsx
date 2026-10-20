"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AccountButton } from "./AccountButton";
import { ComingMenu } from "./ComingMenu";
import { MobileNav } from "./MobileNav";
import { PRIMARY_NAV, isActive } from "./nav";
import { Wordmark } from "./Wordmark";

/**
 * The product's bar: the wordmark, the two sections that exist, one entry for
 * everything that does not, and the account. It replaced a 104px rail whose
 * seven tiles were six "coming soon"s -- the loudest thing in the app was a
 * list of features it did not have (owner decision, 2026-09-29).
 *
 * One hairline under it and a translucent ground with a blur, so content
 * scrolling beneath stays faintly visible. The active section is marked by
 * the slash -- an energy bar on the logo's slant -- as a chosen tab is.
 */
export function TopBar() {
  const pathname = usePathname();

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-veil backdrop-blur-md">
      <div className="mx-auto flex h-14 max-w-[1440px] items-center gap-3 px-4 sm:px-6 md:gap-8 lg:px-8">
        <Wordmark href="/rankings" />
        <nav aria-label="Primary" className="flex min-w-0 items-center">
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
          <div className="hidden md:block">
            <ComingMenu />
          </div>
        </nav>
        <div className="ml-auto flex items-center">
          <div className="hidden md:block">
            <AccountButton />
          </div>
          <div className="md:hidden">
            <MobileNav />
          </div>
        </div>
      </div>
    </header>
  );
}
