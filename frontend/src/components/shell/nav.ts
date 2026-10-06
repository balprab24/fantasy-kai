import type { IconName } from "@/components/ui/Icon";

/**
 * One definition of the navigation, read by the top bar and the mobile menu
 * alike, so the two cannot drift.
 */
export interface NavItem {
  label: string;
  href: string;
  icon: IconName;
}

/**
 * What exists. No "Home": `/` is the landing page, which a member is sent
 * straight past, so a Home item would be a link that bounces. The board is
 * where the product starts, and the wordmark goes there too.
 */
export const PRIMARY_NAV: NavItem[] = [
  { label: "Rankings", href: "/rankings", icon: "rankings" },
  { label: "Scoring", href: "/profiles", icon: "scoring" },
];

/**
 * What does not exist yet, behind one "Coming" entry (owner decision #7,
 * 2026-09-29) instead of six dead tiles that were the loudest thing in the
 * app. Listed so the shape of the product is visible; never a link, because a
 * link to an empty page is a promise the app cannot keep.
 */
export const COMING: { label: string; icon: IconName }[] = [
  { label: "My Teams", icon: "teams" },
  { label: "Start/Sit", icon: "startSit" },
  { label: "Waivers", icon: "waivers" },
  { label: "Trades", icon: "trades" },
  { label: "Draft", icon: "draft" },
  { label: "Players", icon: "players" },
];

/** `/` would otherwise match every path. */
export function isActive(href: string, pathname: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}
