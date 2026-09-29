/**
 * A non-secret "this browser has signed in" hint, set on www.
 *
 * The session itself is the refresh cookie: HttpOnly, on `api.`, with
 * `Path=/api/v1/auth`. Neither this page's script nor its server (`proxy.ts`)
 * can see it, so without a hint every page load had to ask the API. That spent
 * a rate-limited `/auth/refresh` on visitors who had never signed in, and showed
 * members the landing page for one round trip before sending them on.
 *
 * **A hint, never a gate.** It carries no identity and grants nothing. Forging
 * it buys a refresh that fails and a trip to sign in; deleting it costs a member
 * one extra sign-in. `RequireAccount` and the API's own auth stay the gates.
 */

export const MEMBER_HINT = "fk_member";

/** The refresh token's lifetime (`refresh-token-ttl: 30d`), re-set on every restore so the two expire together. */
export const MEMBER_HINT_MAX_AGE = 30 * 24 * 60 * 60;

/** Parses a `Cookie` header or `document.cookie` -- one parser for the proxy and the page. */
export function hasMemberHint(cookies: string): boolean {
  return cookies.split(";").some((pair) => pair.trim() === `${MEMBER_HINT}=1`);
}

/** `Secure` only over https: `next start` on localhost is http, and a hint that never sets is a hint that never works. */
export function memberHintCookie(secure: boolean): string {
  return `${MEMBER_HINT}=1; Path=/; Max-Age=${MEMBER_HINT_MAX_AGE}; SameSite=Lax${secure ? "; Secure" : ""}`;
}

export function clearedMemberHintCookie(): string {
  return `${MEMBER_HINT}=; Path=/; Max-Age=0; SameSite=Lax`;
}

/** What one refresh attempt says about the session. */
export type RefreshOutcome = "restored" | "rejected" | "unavailable";

/**
 * Only a 401 means the session is gone: no token, an expired one, or a revoked
 * or replayed one. A 429, a 5xx, or a request that never finished says nothing
 * about the session -- and the common case of the last is a member reloading
 * while the refresh is in flight, which aborts it. Treating that as "gone"
 * cleared the hint of a member who had merely reloaded quickly, and every page
 * after sent them to sign in with a live session behind it (found in a browser,
 * 2026-09-29: eight fast reloads, a healthy token family, a signed-out member).
 */
export function refreshOutcome(status: number | null): RefreshOutcome {
  if (status !== null && status >= 200 && status < 300) return "restored";
  if (status === 401) return "rejected";
  return "unavailable";
}

/** The routes behind `RequireAccount` -- `app/(app)`. */
const MEMBER_ROUTES = ["/rankings", "/players", "/profiles"];

export function isMemberRoute(pathname: string): boolean {
  return MEMBER_ROUTES.some((route) => pathname === route || pathname.startsWith(`${route}/`));
}

/**
 * Where `proxy.ts` sends a request before anything renders, or null to let it
 * through. A member skips the landing page; a visitor who has never signed in
 * goes straight to sign-in instead of watching the product's skeleton load
 * first. Both were already happening -- in the browser, one round trip late.
 */
export function routeFor(pathname: string, search: string, hinted: boolean): string | null {
  if (pathname === "/" && hinted) return "/rankings";
  if (!hinted && isMemberRoute(pathname)) return `/login?next=${encodeURIComponent(pathname + search)}`;
  return null;
}
