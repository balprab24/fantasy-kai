"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";

const NAV = [
  { href: "/rankings", label: "Rankings" },
  { href: "/profiles", label: "Scoring" },
];

export function SiteHeader() {
  const { status, signOut } = useAuth();
  const pathname = usePathname();

  return (
    <header className="border-b border-line">
      <div className="mx-auto flex max-w-5xl items-center gap-6 px-4 py-3 sm:px-6">
        <Link href="/" className="font-display text-[17px] font-bold tracking-tight">
          fantasy-kai
        </Link>

        <nav className="flex items-center gap-5 text-sm">
          {NAV.map((item) => {
            const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={
                  active
                    ? "border-b-2 border-field pb-0.5 text-ink"
                    : "border-b-2 border-transparent pb-0.5 text-mute hover:text-ink"
                }
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div className="ml-auto text-sm">
          {status === "restoring" ? (
            // Deliberately blank rather than "Sign in": the refresh cookie is
            // still being traded for a token, and flashing the signed-out state
            // at a signed-in user on every reload is a worse lie than a gap.
            <span className="inline-block h-5 w-16" aria-hidden />
          ) : status === "signed-in" ? (
            <button
              type="button"
              onClick={() => void signOut()}
              className="text-mute hover:text-ink"
            >
              Sign out
            </button>
          ) : (
            <Link href="/login" className="text-mute hover:text-ink">
              Sign in
            </Link>
          )}
        </div>
      </div>
    </header>
  );
}
