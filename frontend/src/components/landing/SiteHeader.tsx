"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect } from "react";
import { Wordmark } from "@/components/shell/Sidebar";
import { useAuth } from "@/lib/auth";
import { HOME_FOR_MEMBERS, JOIN_FIELD_ID, LANDING_SECTIONS } from "@/lib/landing";
import { useActiveSection } from "./useActiveSection";

const SECTION_IDS = LANDING_SECTIONS.map((s) => s.id);
/** Module-level so the observer's effect sees the same array every render. */
const NO_SECTIONS: string[] = [];


/**
 * The bar above the landing page and the sign-in pages.
 *
 * Its links do not open the features -- they scroll to the part of the page
 * that explains each one, and the one being read is underlined. Opening the
 * feature itself takes an account. Off the landing page the same links go to
 * `/#section`, so the bar means the same thing everywhere it appears.
 *
 * A member who arrives at `/` is sent on to the board: the landing page is
 * for people deciding whether to join. While the session is still being
 * restored the bar shows the signed-out buttons, not a gap -- nearly everyone
 * here is signed out, and a member is about to leave the page anyway.
 */
export function SiteHeader() {
  const pathname = usePathname();
  const router = useRouter();
  const { status } = useAuth();
  const onLanding = pathname === "/";
  const active = useActiveSection(onLanding ? SECTION_IDS : NO_SECTIONS);

  useEffect(() => {
    if (onLanding && status === "signed-in") router.replace(HOME_FOR_MEMBERS);
  }, [onLanding, status, router]);

  const join = onLanding ? (
    <button
      type="button"
      onClick={() => {
        const field = document.getElementById(JOIN_FIELD_ID);
        field?.scrollIntoView({ block: "center" });
        field?.focus({ preventScroll: true });
      }}
      className={JOIN_CLASS}
    >
      Join the Kai
    </button>
  ) : (
    <Link href="/register" className={JOIN_CLASS}>
      Join the Kai
    </Link>
  );

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-paper/85 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-[1320px] items-center gap-4 px-4 sm:px-6 md:gap-8 lg:px-8">
        {/* Never squeezed: the wordmark's ı carries its orb, and a wrap drops it onto a line of its own. */}
        <div className="shrink-0">
          <Wordmark />
        </div>
        <nav aria-label="On this page" className="hidden md:block">
          <SectionLinks onLanding={onLanding} active={active} />
        </nav>
        <div className="ml-auto flex items-center gap-2 sm:gap-3">
          <Link
            href="/login"
            className="rounded-md px-2 py-2 text-sm whitespace-nowrap text-mute transition-colors hover:text-ink sm:px-3"
          >
            Sign in
          </Link>
          {join}
        </div>
      </div>
      {/* Below md the links move to their own strip, scrolled sideways if a
          narrow phone cannot fit them, rather than into a drawer: four links
          do not need a menu. */}
      <nav aria-label="On this page" className="overflow-x-auto border-t border-line md:hidden">
        <SectionLinks onLanding={onLanding} active={active} compact />
      </nav>
    </header>
  );
}

const JOIN_CLASS =
  "rounded-md bg-ki px-3.5 py-2 text-sm font-semibold whitespace-nowrap text-on-ki transition-[filter] hover:brightness-110";

function SectionLinks({
  onLanding,
  active,
  compact = false,
}: {
  onLanding: boolean;
  active: string | null;
  compact?: boolean;
}) {
  return (
    <ul className={`flex items-center ${compact ? "gap-1 px-2" : "gap-1"}`}>
      {LANDING_SECTIONS.map((section) => {
        const current = active === section.id;
        const href = onLanding ? `#${section.id}` : `/#${section.id}`;
        return (
          <li key={section.id}>
            <a
              href={href}
              aria-current={current ? "location" : undefined}
              className={`relative block rounded-md px-3 whitespace-nowrap transition-colors ${
                compact ? "py-2.5 text-[13px]" : "py-2 text-sm"
              } ${current ? "text-ink" : "text-mute hover:text-ink"}`}
            >
              {section.label}
              <span
                aria-hidden
                className={`absolute inset-x-3 -bottom-px h-0.5 rounded-full bg-energy transition-opacity ${
                  current ? "opacity-100" : "opacity-0"
                }`}
              />
            </a>
          </li>
        );
      })}
    </ul>
  );
}
