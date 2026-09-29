/**
 * The landing page's sections, and where signing in sends you afterwards.
 * Pure: no React and no browser, so `tests/lib.test.ts` can load it.
 */

export interface LandingSection {
  /** The element id, and so the `#fragment` the header links to. */
  id: string;
  /** What the header calls it. */
  label: string;
}

/**
 * One list, read by the header's links and by the page's sections alike, so a
 * link cannot point at a section that is not there. The order is the page's.
 */
export const LANDING_SECTIONS: readonly LandingSection[] = [
  { id: "rankings", label: "Rankings" },
  { id: "scoring", label: "Scoring" },
  { id: "players", label: "Players" },
  { id: "next", label: "Coming next" },
];

/**
 * The id of the hero's email field, which the header's "Join the Kai" focuses.
 * Here and not in the header: a constant exported from a "use client" module
 * reaches a server component as a client reference, not as the string.
 */
export const JOIN_FIELD_ID = "join-email";

/** Where a member lands when there is nowhere better to send them. */
export const HOME_FOR_MEMBERS = "/rankings";

/** A page that makes no sense to be sent back to once signed in. */
const AUTH_PAGES = new Set(["/login", "/register"]);

/**
 * The `next` a sign-in page was handed, if it is safe to follow; otherwise null.
 *
 * `next` arrives in a URL anyone can write, so following it blindly is an open
 * redirect: `/login?next=//evil.example` signs you in and hands you to a
 * stranger who can then ask for your password again. Checking the string's
 * shape is the trap -- `/\evil.example` and `/<tab>/evil.example` both look
 * like paths and both leave the site, because the URL parser treats a
 * backslash as a slash and drops tabs and newlines. So the check asks the same
 * parser the browser will use: resolve it against a stand-in origin and
 * accept it only if it is still on that origin.
 *
 * And then asks again, of what it is about to return. Resolving is not
 * idempotent: `/..//evil.example` resolves *on this origin* to the path
 * `//evil.example` -- and that path, handed to the router, is a host. The
 * first version of this function stopped after one resolution and let it
 * through; `tests/lib.test.ts` caught it by resolving the output.
 */
export function safeNext(raw: string | null | undefined): string | null {
  if (!raw || !raw.startsWith("/")) return null;
  const origin = "https://fantasykai.invalid";
  let url: URL;
  try {
    url = new URL(raw, origin);
  } catch {
    return null;
  }
  if (url.origin !== origin) return null;
  if (AUTH_PAGES.has(url.pathname)) return null;
  const out = url.pathname + url.search + url.hash;
  if (out.startsWith("//") || new URL(out, origin).origin !== origin) return null;
  return out;
}
