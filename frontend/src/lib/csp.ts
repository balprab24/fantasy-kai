/**
 * The site's Content-Security-Policy, as one pure function: `proxy.ts` calls it
 * once per request with a fresh nonce, and `tests/lib.test.ts` reads it without
 * a request.
 *
 * Why a nonce rather than `'unsafe-inline'` (owner decision, 2026-09-29): the
 * access token lives in this page's memory by design (`lib/api.ts`), so any
 * script that runs here can act as the member. `'unsafe-inline'` would let an
 * injected inline script run; a nonce it cannot guess stops it. The price is
 * that every page renders per request (`app/layout.tsx`), because a nonce baked
 * into static HTML at build time is one every visitor shares.
 *
 * Next.js reads the nonce back out of this header on the request and puts it on
 * its own scripts. Nothing else in the app renders a `<script>`.
 */

export interface CspInput {
  nonce: string;
  /** Origin of the API the browser fetches from, or null for same-origin. */
  apiOrigin: string | null;
  dev: boolean;
}

export function contentSecurityPolicy({ nonce, apiOrigin, dev }: CspInput): string {
  const directives: string[][] = [
    ["default-src", "'self'"],
    // 'strict-dynamic' trusts what a nonced script loads (Next's chunks) and
    // makes browsers ignore host allowlists here -- no host, not even this one,
    // is trusted to serve script unless a nonced script asked for it. Dev needs
    // 'unsafe-eval' for React's error stacks; production does not.
    ["script-src", "'self'", `'nonce-${nonce}'`, "'strict-dynamic'", ...(dev ? ["'unsafe-eval'"] : [])],
    // No nonce here, deliberately, and this is where the Next.js example differs:
    // a nonce in style-src makes browsers IGNORE 'unsafe-inline', and React's
    // style={} attributes (PlayerRow, WeeklyChart) cannot carry one. Injected
    // CSS is the accepted residue; injected script is not.
    ["style-src", "'self'", "'unsafe-inline'"],
    // Headshots and team logos -- both a.espncdn.com, measured 2026-09-29.
    ["img-src", "'self'", "data:", "blob:", "https://a.espncdn.com"],
    ["font-src", "'self'"],
    // Where an injected script would send a stolen token, if it ran anyway.
    ["connect-src", "'self'", ...(apiOrigin ? [apiOrigin] : []), ...(dev ? ["ws:"] : [])],
    // The sign-in form must not be frameable; X-Frame-Options (next.config.ts)
    // says the same to browsers that predate this directive.
    ["frame-ancestors", "'none'"],
    ["form-action", "'self'"],
    ["base-uri", "'none'"],
    ["object-src", "'none'"],
  ];
  // Only when the API is https, which is to say only when this is a real
  // deployment: `next start` on localhost talks to http://localhost:8080, and
  // upgrading that request to https would break the local check of this policy.
  if (apiOrigin?.startsWith("https:")) directives.push(["upgrade-insecure-requests"]);
  return directives.map((d) => d.join(" ")).join("; ");
}

/**
 * The API's origin from the configured base URL -- the same value `lib/api.ts`
 * fetches with. Empty means api.ts fetches relative to this site, so there is no
 * other origin to allow; that is F11's failure shape (DEPLOY-STEPS.md), and the
 * policy mirrors it rather than papering over it.
 */
export function apiOriginOf(base: string): string | null {
  return base === "" ? null : new URL(base).origin;
}
