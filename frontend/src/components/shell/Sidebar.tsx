"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Icon } from "@/components/ui/Icon";
import { COMING, PRIMARY_NAV, isActive } from "./nav";

/** Owner decision 2026-11-02: compact member rail, sharing the mobile menu's items. */
export function Sidebar() {
  const pathname = usePathname();
  return (
    <aside className="fixed inset-y-0 left-0 z-20 hidden w-[88px] flex-col border-r border-line bg-canvas pt-16 lg:flex">
      <nav aria-label="Primary" className="flex flex-col gap-1 px-2">
        {PRIMARY_NAV.map((item) => (
          <Link key={item.href} href={item.href} aria-current={isActive(item.href, pathname) ? "page" : undefined}
            className={`flex h-16 flex-col items-center justify-center gap-1 rounded-control text-[12px] ${isActive(item.href, pathname) ? "bg-lift font-semibold text-energy-text" : "text-mute hover:bg-well hover:text-ink"}`}>
            <Icon name={item.icon} size={22} />{item.label}
          </Link>
        ))}
        <div className="mt-3 border-t border-line pt-3">
          {COMING.map((item) => (
            <span key={item.label} aria-disabled="true" className="flex h-16 flex-col items-center justify-center gap-0.5 text-[11px] text-faint">
              <Icon name={item.icon} size={18} /><span>{item.label}</span><span>Coming</span>
            </span>
          ))}
        </div>
      </nav>
    </aside>
  );
}
