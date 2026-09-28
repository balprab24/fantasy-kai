"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";
import { PRIMARY_NAV, SECONDARY_NAV, isActive, type NavItem } from "./nav";

/**
 * The desktop rail: from md up, one narrow column of big icons, each with its
 * label underneath -- the shape of an app's tab bar stood on its end, not a
 * list of rows. It replaces a 72px icon-only rail and a 232px list, both of
 * which asked the eye to read across; this one is read straight down. Below
 * md it is absent and MobileNav's drawer carries the same lists as rows.
 *
 * Sized to fit a 1440x780 laptop without the list scrolling: nine 60px tiles
 * plus the brand and account block come to ~760px. Taller tiles made the main
 * nav scroll at exactly the height most people use.
 */
export function Sidebar() {
  return (
    <aside className="rail-edge sticky top-0 hidden h-dvh w-[104px] shrink-0 flex-col bg-void md:flex">
      <div className="flex justify-center px-1 pt-4 pb-2">
        <Wordmark stacked />
      </div>
      {/* The list scrolls on its own if a short window cannot fit it, so the
          brand and the account block never leave the screen. */}
      <div className="min-h-0 flex-1 overflow-y-auto">
        <NavLists stacked />
      </div>
      <div className="border-t border-line p-1.5">
        <AccountBlock stacked />
      </div>
    </aside>
  );
}

/** The logo and the word. `stacked` sets the mark large with the word beneath it, for the rail. */
export function Wordmark({ stacked = false }: { stacked?: boolean }) {
  return (
    <Link
      href="/"
      className={`flex items-center rounded-md ${stacked ? "flex-col gap-2" : "gap-2.5"}`}
    >
      <LogoMark size={stacked ? 40 : 30} />
      <span
        className={`font-display font-bold tracking-tight text-ink ${stacked ? "text-[15px]" : "text-[21px]"}`}
      >
        {/* Drawn as a dotless ı with the orb for its dot, so the visible word
            is not the real word: screen readers get the real one. */}
        <span aria-hidden>
          fantasy<span className="text-ki">·</span>ka
          <span className="dotted-i">
            ı
            <DragonBall />
          </span>
        </span>
        <span className="sr-only">fantasy·kai</span>
      </span>
    </Link>
  );
}

/**
 * The dot of the i in kai, as an orb of ki: amber glass lit from the upper
 * left, one red star. Drawn here, not taken from anywhere. Bigger than a real
 * tittle on purpose -- it is the one flourish in the wordmark -- but it sits
 * where the tittle sits, so the word still reads as a word. No SVG gradient
 * ids, because the wordmark renders in the rail and the mobile bar at once and
 * two identical ids on one page are invalid.
 */
function DragonBall() {
  return (
    <span aria-hidden className="dragon-ball">
      <svg viewBox="0 0 20 20" className="size-full">
        <path
          d="M10 5.6l1.2 2.7 2.9.3-2.2 1.9.7 2.9L10 11.9l-2.6 1.5.7-2.9-2.2-1.9 2.9-.3z"
          fill="#d42a1c"
        />
      </svg>
    </span>
  );
}

/** Two slanted strokes -- a rising bar and its after-image. Ours, not borrowed. */
function LogoMark({ size }: { size: number }) {
  return (
    <svg aria-hidden width={size} height={size} viewBox="0 0 26 26" className="shrink-0">
      <path d="M9 3h6L9 23H3z" fill="var(--color-ki)" />
      <path d="M18 3h5l-6 20h-5z" fill="var(--color-energy)" opacity="0.85" />
    </svg>
  );
}

export function NavLists({
  stacked = false,
  onNavigate,
}: {
  stacked?: boolean;
  onNavigate?: () => void;
}) {
  return (
    <nav aria-label="Primary" className={`flex flex-col ${stacked ? "gap-2 px-1.5 py-1" : "gap-3 px-2 py-2"}`}>
      <NavGroup items={PRIMARY_NAV} stacked={stacked} onNavigate={onNavigate} />
      <div className={`border-t border-line ${stacked ? "mx-4" : "mx-2"}`} />
      <NavGroup items={SECONDARY_NAV} stacked={stacked} onNavigate={onNavigate} />
    </nav>
  );
}

/**
 * One item's shape. `stacked` is the rail's tile: icon over label, centred.
 * Otherwise a row, for the mobile drawer, where a wide tile would waste the
 * width it has.
 */
function itemShape(stacked: boolean) {
  return stacked
    ? "flex-col justify-center gap-1 py-1.5 text-[13px] leading-tight"
    : "h-12 justify-start gap-3 px-3 text-base";
}

function NavGroup({
  items,
  stacked,
  onNavigate,
}: {
  items: NavItem[];
  stacked: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const shape = itemShape(stacked);

  return (
    <ul className="flex flex-col gap-1">
      {items.map((item) => {
        if (item.href === null) {
          return (
            <li key={item.label}>
              {/* role="link" so aria-disabled means something: assistive tech
                  ignores it on a role-less span. It announces as a dimmed
                  link, which is exactly what it is. */}
              <span
                role="link"
                aria-disabled="true"
                title={`${item.label} — coming soon`}
                className={`flex cursor-default items-center rounded-lg text-faint ${shape}`}
              >
                <span className="relative">
                  <Icon name={item.icon} size={stacked ? 28 : 24} className="opacity-60" />
                  {/* A tile has no room for a "Soon" tag, so a hollow ring
                      marks the item -- a shape, not only a dimmer colour. */}
                  {stacked && (
                    <span
                      aria-hidden
                      className="absolute -top-0.5 -right-1.5 size-2 rounded-full border border-mute"
                    />
                  )}
                </span>
                <span>
                  {item.label}
                  <span className="sr-only">, coming soon</span>
                </span>
                {!stacked && (
                  <span
                    aria-hidden
                    className="ml-auto rounded-sm border border-line px-1 text-[10px] font-semibold tracking-wider uppercase"
                  >
                    Soon
                  </span>
                )}
              </span>
            </li>
          );
        }

        const active = isActive(item.href, pathname);
        return (
          <li key={item.label}>
            <Link
              href={item.href}
              aria-current={active ? "page" : undefined}
              onClick={onNavigate}
              className={`relative flex items-center rounded-lg transition-colors ${shape} ${
                active
                  ? "bg-surface-2 font-medium text-ink before:absolute before:inset-y-2.5 before:left-0 before:w-[3px] before:rounded-full before:bg-energy before:shadow-[0_0_10px_rgb(77_163_255/0.6)]"
                  : "text-mute hover:bg-surface-2 hover:text-ink"
              }`}
            >
              <Icon name={item.icon} size={stacked ? 28 : 24} className={active ? "text-energy" : ""} />
              <span>{item.label}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

export function AccountBlock({
  stacked = false,
  onNavigate,
}: {
  stacked?: boolean;
  onNavigate?: () => void;
}) {
  const { status, signOut } = useAuth();
  const row = `flex w-full items-center rounded-lg text-mute transition-colors hover:bg-surface-2 hover:text-ink ${itemShape(stacked)}`;
  const size = stacked ? 28 : 24;

  if (status === "restoring") {
    // Deliberately blank rather than "Sign in": the refresh cookie is still
    // being traded for a token, and flashing the signed-out state at a
    // signed-in user on every reload is a worse lie than a gap.
    return <div className={stacked ? "h-[3.75rem]" : "h-12"} aria-hidden />;
  }

  if (status === "signed-in") {
    return (
      <button type="button" onClick={() => void signOut()} className={row}>
        <Icon name="account" size={size} className="text-energy" />
        <span>Sign out</span>
      </button>
    );
  }

  return (
    <Link href="/login" onClick={onNavigate} className={row}>
      <Icon name="account" size={size} />
      <span>Sign in</span>
    </Link>
  );
}
