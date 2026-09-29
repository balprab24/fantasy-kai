/**
 * Where the browser sends API calls. One definition, because two readers need
 * it to agree: `lib/api.ts` fetches from it and `proxy.ts` puts its origin in
 * the Content-Security-Policy's connect-src. If they drifted, the policy would
 * block the app's own requests.
 *
 * `??`, not `||`: an empty string is kept as it is, which makes every call
 * same-origin -- the F11 failure (DEPLOY-STEPS.md), still owed a build-time
 * refusal.
 */
export const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
