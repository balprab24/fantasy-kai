"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { AccountButton } from "./AccountButton";
import { ComingMenu } from "./ComingMenu";
import { PRIMARY_NAV, isActive } from "./nav";
import { Wordmark } from "./Wordmark";

/**
 * The product's navigation from md: the wordmark, the two sections that
 * exist, one entry for everything that does not, and the account at the foot.
 * The same items as the `TopBar` (`nav.ts`), so the two cannot drift.
 *
 * A rail of real links only. The 104px rail this replaced held six "coming
 * soon" tiles and was the loudest thing in the app (owner decision,
 * 2026-09-29); what is not built stays behind the one Coming entry.
 *
 * On the page's own black, with a hairline for its edge -- structure, not a
 * panel. The current section takes the "you are here" bar on its left edge.
 */
export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="fixed inset-y-0 left-0 z-30 hidden w-44 flex-col border-r border-line bg-canvas px-3 pt-5 pb-4 md:flex">
      <div className="px-2">
        <Wordmark href="/rankings" />
      </div>
      <nav aria-label="Primary" className="mt-10 flex flex-col gap-1">
        {PRIMARY_NAV.map((item) => {
          const active = isActive(item.href, pathname);
          return (
            <Link
              key={item.href}
              href={item.href}
              aria-current={active ? "page" : undefined}
              className={`relative flex h-10 items-center gap-3 rounded-control px-3 text-sm transition-colors ${
                active
                  ? "bg-surface font-semibold text-ink before:absolute before:inset-y-2 before:left-0 before:w-0.5 before:bg-energy"
                  : "text-mute hover:text-ink"
              }`}
            >
              <Icon name={item.icon} size={18} />
              {item.label}
            </Link>
          );
        })}
        <ComingMenu />
      </nav>
      <div className="mt-auto">
        <AccountButton />
      </div>
    </aside>
  );
}
