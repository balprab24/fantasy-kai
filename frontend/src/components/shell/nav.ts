import type { IconName } from "@/components/ui/Icon";

/**
 * One definition of the navigation, read by the desktop rail and the mobile
 * drawer alike, so the two cannot drift.
 *
 * `href: null` is a section that does not exist yet. It is listed so the shape
 * of the product is visible, and rendered as plainly unavailable -- never as a
 * link to an empty page, which would be a promise the app cannot keep.
 */
export interface NavItem {
  label: string;
  href: string | null;
  icon: IconName;
}

export const PRIMARY_NAV: NavItem[] = [
  { label: "Home", href: "/", icon: "home" },
  { label: "My Teams", href: null, icon: "teams" },
  { label: "Rankings", href: "/rankings", icon: "rankings" },
  { label: "Start/Sit", href: null, icon: "startSit" },
  { label: "Waivers", href: null, icon: "waivers" },
  { label: "Trades", href: null, icon: "trades" },
  { label: "Draft", href: null, icon: "draft" },
  { label: "Players", href: null, icon: "players" },
];

export const SECONDARY_NAV: NavItem[] = [{ label: "Scoring", href: "/profiles", icon: "scoring" }];

/** `/` would otherwise match every path. */
export function isActive(href: string, pathname: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}
