/**
 * What a button looks like, as class names. The codebase styles with utilities
 * in place; these are the few looks every page shares, so an action looks the
 * same wherever it appears.
 *
 * One `BUTTON_PRIMARY` per view, at most: it is the orange, and orange means
 * "the one thing to do". Everything else is secondary or a quiet link.
 */
const BASE =
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-control font-semibold transition-[filter,background-color,box-shadow,color] disabled:cursor-not-allowed disabled:opacity-60";

export const BUTTON_PRIMARY = `${BASE} h-11 bg-ki px-5 text-[15px] text-on-ki hover:brightness-110 active:brightness-95`;

/**
 * Everything else you press: retry, show more, cancel -- and the header's
 * "Join the Kai", because the orange belongs to the page's own ask. Its edge
 * is the 3:1 line.
 */
export const BUTTON_SECONDARY = `${BASE} h-9 bg-well px-4 text-sm text-ink shadow-[inset_0_0_0_1px_var(--color-line-strong)] hover:shadow-[inset_0_0_0_1px_var(--color-mute)]`;

/** A link that reads as one without shouting: underlined, blue only on hover. */
export const LINK_QUIET =
  "text-ink underline decoration-line-strong underline-offset-4 transition-colors hover:decoration-energy";
