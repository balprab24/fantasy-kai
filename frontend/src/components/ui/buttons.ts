/**
 * What a button, a panel and a link look like, as class names. The codebase
 * styles with utilities in place; these are the few looks every page shares,
 * so an action looks the same wherever it appears.
 *
 * One `BUTTON_PRIMARY` per view, at most: it is the orange, and orange means
 * "the one thing to do". Everything else is secondary, ghost or a quiet link.
 */
const BASE =
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-control font-semibold transition-[filter,background-color,box-shadow,color,transform] duration-150 disabled:cursor-not-allowed disabled:opacity-60";

const ORANGE = "bg-ki text-on-ki hover:brightness-110 active:brightness-95 motion-safe:active:scale-[0.98]";

export const BUTTON_PRIMARY = `${BASE} h-11 px-5 text-[15px] ${ORANGE}`;

/** The public pages' ask, "Join the Kai", at the size of a decision: 56px. */
export const BUTTON_JOIN = `${BASE} h-14 px-8 text-[17px] ${ORANGE}`;

/**
 * The primary at the site header's height, for the moment the page's own ask
 * has scrolled away and the header's join becomes the one orange in view.
 */
export const BUTTON_PRIMARY_COMPACT = `${BASE} h-9 px-4 text-sm ${ORANGE}`;

/**
 * Everything else you press: retry, show more, cancel -- and the header's
 * "Join the Kai" while the page's own ask is in view. Its edge is the 3:1 line.
 */
export const BUTTON_SECONDARY = `${BASE} h-10 bg-well px-4 text-sm text-ink shadow-[inset_0_0_0_1px_var(--color-line-strong)] hover:bg-lift hover:shadow-[inset_0_0_0_1px_var(--color-mute)]`;

/** A square icon button: close, previous, next, the menu. 40px, a real touch target. */
export const BUTTON_ICON =
  "inline-flex size-10 shrink-0 items-center justify-center rounded-control text-mute transition-colors hover:bg-well hover:text-ink disabled:pointer-events-none disabled:opacity-40";

/** A link that reads as one without shouting: underlined, blue only on hover. */
export const LINK_QUIET =
  "text-ink underline decoration-line-strong underline-offset-4 transition-colors hover:decoration-energy";

/**
 * A section of a page: rounded, one hairline, the surface step. The page's
 * pieces are told apart by these, and what is inside them -- rows, a chart --
 * sits on the panel directly, never in a second box.
 */
export const PANEL = "rounded-panel border border-line bg-surface";
