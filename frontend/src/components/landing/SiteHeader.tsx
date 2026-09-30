"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Wordmark } from "@/components/shell/Wordmark";
import { BUTTON_SECONDARY } from "@/components/ui/buttons";
import { useAuth } from "@/lib/auth";
import { HOME_FOR_MEMBERS, JOIN_FIELD_ID, LANDING_SECTIONS } from "@/lib/landing";
import { useActiveSection } from "./useActiveSection";

const SECTION_IDS = LANDING_SECTIONS.map((s) => s.id);
/** Module-level so the observer's effect sees the same array every render. */
const NO_SECTIONS: string[] = [];

/**
 * The bar above the landing page and the sign-in pages -- the product's own
 * bar (`shell/TopBar.tsx`) in the same vocabulary, so the two read as one
 * product: the wordmark, plain links, one orange action.
 *
 * Its links do not open the features -- they scroll to the part of the page
 * that explains each one, and the one being read is underlined in blue.
 * Opening the feature itself takes an account. They are the landing page's
 * alone: on sign-in and register the page is the task. Below md they step
 * away: the page is one column, and scrolling is the navigation.
 *
 * In Daylight it rests on the page itself, with no edge; once the page scrolls
 * under it, one blue hairline says where the bar ends. Nothing else changes.
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
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const check = () => setScrolled(window.scrollY > 4);
    check();
    window.addEventListener("scroll", check, { passive: true });
    return () => window.removeEventListener("scroll", check);
  }, []);

  useEffect(() => {
    if (onLanding && status === "signed-in") router.replace(HOME_FOR_MEMBERS);
  }, [onLanding, status, router]);

  // Secondary, never orange: the orange is the page's own ask (the hero's
  // form, a sign-in form's button), and two orange buttons for one action is
  // one too many. On /register the page IS the ask, so the bar has none.
  const join = onLanding ? (
    <button
      type="button"
      onClick={() => {
        const field = document.getElementById(JOIN_FIELD_ID);
        field?.scrollIntoView({ block: "center" });
        field?.focus({ preventScroll: true });
      }}
      className={BUTTON_SECONDARY}
    >
      Join the Kai
    </button>
  ) : pathname === "/register" ? null : (
    <Link href="/register" className={BUTTON_SECONDARY}>
      Join the Kai
    </Link>
  );

  return (
    <header
      className={`sticky top-0 z-30 bg-veil backdrop-blur-md transition-shadow ${
        scrolled ? "shadow-[0_1px_0_var(--color-line)]" : ""
      }`}
    >
      <div className="mx-auto flex h-16 max-w-[1320px] items-center gap-4 px-4 sm:px-6 md:gap-10 lg:px-8">
        <Wordmark />
        {/* Only on the landing page: on sign-in the page is the task, and links into
            another page's sections read as places a visitor cannot go yet. */}
        {onLanding && (
          <nav aria-label="On this page" className="hidden md:block">
            <ul className="flex items-center">
              {LANDING_SECTIONS.map((section) => {
                const current = active === section.id;
                return (
                  <li key={section.id}>
                    <a
                      href={`#${section.id}`}
                      aria-current={current ? "location" : undefined}
                      className={`relative flex h-16 items-center px-3 text-[15px] whitespace-nowrap transition-colors ${
                        current
                          ? "text-ink after:absolute after:inset-x-3 after:bottom-0 after:h-0.5 after:bg-energy"
                          : "text-mute hover:text-ink"
                      }`}
                    >
                      {section.label}
                    </a>
                  </li>
                );
              })}
            </ul>
          </nav>
        )}
        <div className="ml-auto flex items-center gap-1 sm:gap-3">
          <Link
            href="/login"
            className="flex h-9 items-center px-2 text-[15px] whitespace-nowrap text-mute transition-colors hover:text-ink sm:px-3"
          >
            Sign in
          </Link>
          {join}
        </div>
      </div>
    </header>
  );
}
