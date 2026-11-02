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
  { label: "My Teams", href: "/teams", icon: "teams" },
  { label: "Scoring", href: "/profiles", icon: "scoring" },
];

/** Disabled Coming entries, shared by rail and menu. Trades stays absent while held. */
export const COMING: { label: string; icon: IconName }[] = [
  { label: "Start/Sit", icon: "startSit" },
  { label: "Waivers", icon: "waivers" },
  { label: "Draft", icon: "draft" },
  { label: "Players", icon: "players" },
];

/** `/` would otherwise match every path. */
export function isActive(href: string, pathname: string) {
  return href === "/" ? pathname === "/" : pathname === href || pathname.startsWith(`${href}/`);
}
