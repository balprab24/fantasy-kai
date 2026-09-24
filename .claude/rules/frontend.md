---
paths:
  - "frontend/**"
---

# Frontend rules — Next.js, auth, the ruleset builder

Loaded when Claude reads a file under `frontend/`. The full stories are in `docs/traps.md` — grep the
quoted phrase. Node 24 (`.nvmrc`); the gates are `npm run lint && npm run build`. **There are no
frontend tests yet**, so verify behaviour in a browser.

- **The access token lives in memory only (`lib/api.ts`), never in `localStorage`.** The refresh
  token is the persistence mechanism, in an `HttpOnly` cookie, and a reload re-earns the access token.
- **A user-scoped query must not fire before the session is restored.** `useProfiles` is gated on
  `status !== "restoring"`; otherwise TanStack Query caches the logged-out answer for five minutes. → "before the session is restored"
- **`POST /api/v1/scoring-profiles` takes `rules` as a JSON *string*,** not a nested object. → "*string*"
- **A focused `<input type="number">` changes on the mouse wheel.** `RateInput` blurs on wheel, so
  scrolling the page cannot silently edit a rate. → "mouse wheel"
- **`SameSite` is decided by registrable domain, and `localhost` hides it.** The frontend and API must
  share one registrable domain; never weaken the cookie to make a cross-site setup work. → "SameSite"
- **`StatKey` and the validator's limits are hand-copied** into `lib/types.ts` and `lib/ruleset.ts`, and
  `npm run build` will not notice drift. Change them together with `StatKey.java` and
  `RulesetValidator.java`.
- **Refresh is de-duplicated per tab, not across tabs.** Two tabs restoring at once can present the
  same refresh token twice, and the backend revokes the whole family on the second use (found by
  reading, not yet reproduced).
- **Known, not yet fixed:** `RankingsBoard` re-rounds the server's 2-decimal points with
  `toFixed(1)`, which is binary rounding: `350.45` displays as `350.4`, where the backend's
  `HALF_UP` would give `350.5`.
