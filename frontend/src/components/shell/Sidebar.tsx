"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth";
import { Icon } from "@/components/ui/Icon";
import { PRIMARY_NAV, SECONDARY_NAV, isActive, type NavItem } from "./nav";

/**
 * The desktop rail. Expanded with labels at xl, an icon rail from md to xl,
 * and absent below md, where MobileNav's drawer carries the same lists.
 */
export function Sidebar() {
  return (
    <aside className="sticky top-0 hidden h-dvh w-[72px] shrink-0 flex-col border-r border-line bg-raised md:flex xl:w-[232px]">
      <div className="flex h-16 items-center justify-center px-4 xl:justify-start">
        <Wordmark collapsible />
      </div>
      <NavLists collapsible />
      <div className="mt-auto border-t border-line p-2">
        <AccountBlock collapsible />
      </div>
    </aside>
  );
}

/**
 * `collapsible` hides every label between md and xl. Hidden with `sr-only`
 * rather than removed, so the icon rail still reads as "Rankings, current
 * page" to a screen reader and not as an unlabelled image.
 */
function labelClass(collapsible: boolean) {
  return collapsible ? "sr-only xl:not-sr-only" : "";
}

export function Wordmark({ collapsible = false }: { collapsible?: boolean }) {
  return (
    <Link href="/" className="flex items-center gap-2.5 rounded-md">
      <LogoMark />
      <span
        className={`font-display text-[19px] font-bold tracking-tight text-ink ${labelClass(collapsible)}`}
      >
        fantasy<span className="text-ki">·</span>kai
      </span>
    </Link>
  );
}

/** Two slanted strokes -- a rising bar and its after-image. Ours, not borrowed. */
function LogoMark() {
  return (
    <svg aria-hidden width="26" height="26" viewBox="0 0 26 26" className="shrink-0">
      <path d="M9 3h6L9 23H3z" fill="var(--color-ki)" />
      <path d="M18 3h5l-6 20h-5z" fill="var(--color-energy)" opacity="0.85" />
    </svg>
  );
}

export function NavLists({
  collapsible = false,
  onNavigate,
}: {
  collapsible?: boolean;
  onNavigate?: () => void;
}) {
  return (
    <nav aria-label="Primary" className="flex flex-col gap-4 px-2 py-2">
      <NavGroup items={PRIMARY_NAV} collapsible={collapsible} onNavigate={onNavigate} />
      <div className="mx-2 border-t border-line" />
      <NavGroup items={SECONDARY_NAV} collapsible={collapsible} onNavigate={onNavigate} />
    </nav>
  );
}

function NavGroup({
  items,
  collapsible,
  onNavigate,
}: {
  items: NavItem[];
  collapsible: boolean;
  onNavigate?: () => void;
}) {
  const pathname = usePathname();
  const layout = collapsible
    ? "justify-center px-0 xl:justify-start xl:px-3"
    : "justify-start px-3";

  return (
    <ul className="flex flex-col gap-0.5">
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
                className={`flex h-10 cursor-default items-center gap-3 rounded-md text-sm text-faint ${layout}`}
              >
                <span className="relative">
                  <Icon name={item.icon} className="opacity-60" />
                  {/* On the icon rail the "Soon" tag is hidden, so a hollow
                      ring marks the item -- a shape, not only a dimmer colour. */}
                  {collapsible && (
                    <span
                      aria-hidden
                      className="absolute -top-0.5 -right-1 size-1.5 rounded-full border border-mute xl:hidden"
                    />
                  )}
                </span>
                <span className={labelClass(collapsible)}>
                  {item.label}
                  <span className="sr-only">, coming soon</span>
                </span>
                <span
                  aria-hidden
                  className={`ml-auto rounded-sm border border-line px-1 text-[10px] font-semibold tracking-wider uppercase ${collapsible ? "hidden xl:inline" : ""}`}
                >
                  Soon
                </span>
              </span>
            </li>
          );
        }

        const active = isActive(item.href, pathname);
        return (
          <li key={item.label}>
            <Link
              href={item.href}
              title={collapsible ? item.label : undefined}
              aria-current={active ? "page" : undefined}
              onClick={onNavigate}
              className={`relative flex h-10 items-center gap-3 rounded-md text-sm transition-colors ${layout} ${
                active
                  ? "bg-surface-2 font-medium text-ink before:absolute before:inset-y-2 before:left-0 before:w-[3px] before:rounded-full before:bg-energy before:shadow-[0_0_10px_rgb(77_163_255/0.6)]"
                  : "text-mute hover:bg-surface-2 hover:text-ink"
              }`}
            >
              <Icon name={item.icon} className={active ? "text-energy" : ""} />
              <span className={labelClass(collapsible)}>{item.label}</span>
            </Link>
          </li>
        );
      })}
    </ul>
  );
}

export function AccountBlock({
  collapsible = false,
  onNavigate,
}: {
  collapsible?: boolean;
  onNavigate?: () => void;
}) {
  const { status, signOut } = useAuth();
  const layout = collapsible
    ? "justify-center px-0 xl:justify-start xl:px-3"
    : "justify-start px-3";
  const row = `flex h-10 w-full items-center gap-3 rounded-md text-sm text-mute transition-colors hover:bg-surface-2 hover:text-ink ${layout}`;

  if (status === "restoring") {
    // Deliberately blank rather than "Sign in": the refresh cookie is still
    // being traded for a token, and flashing the signed-out state at a
    // signed-in user on every reload is a worse lie than a gap.
    return <div className="h-10" aria-hidden />;
  }

  if (status === "signed-in") {
    return (
      <button
        type="button"
        onClick={() => void signOut()}
        title={collapsible ? "Sign out" : undefined}
        className={row}
      >
        <Icon name="account" className="text-energy" />
        <span className={labelClass(collapsible)}>Sign out</span>
      </button>
    );
  }

  return (
    <Link
      href="/login"
      title={collapsible ? "Sign in" : undefined}
      onClick={onNavigate}
      className={row}
    >
      <Icon name="account" />
      <span className={labelClass(collapsible)}>Sign in</span>
    </Link>
  );
}
