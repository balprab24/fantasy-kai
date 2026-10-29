# Workspace redesign and security review — 2026-10-28

## Changes

The member workspace now has desktop side navigation and a dense performance table. Player reports retain their identity/chart/game-log/career structure, with board context and a compact projection explanation added. Public marketing specimens retain their original columns and comparison bars.

The enriched board endpoint calculates positional and qualified PPG ranks before pagination. Recent results use the last four league regular-season weeks, missing appearances remain null, and usage is aggregated in one query. The browser loads bounded 200-player pages to complete search coverage; until completion the search notice explicitly describes the loaded subset. Existing rankings API remains intact.

Projection reads use the active provider's stored raw lines, score against the caller's rules, resolve request-time availability, exclude unavailable players from the board, and label stale data. No HTTP request runs generation. Missing stored projections report UNAVAILABLE, not a fabricated score. Where a stored target and roster evidence exist, detail responses distinguish bye, missing history, and absence from published rosters. Threshold bonuses are shown as an aggregate rather than individually. No production job or data was changed.

## Findings and fixes

| Finding | Evidence / consequence | Change |
|---|---|---|
| Vulnerable frontend dependency versions | npm audit initially reported five packages, one critical and four high. The critical Next advisory requires a typed-array `use cache` pattern not present in inspected source; runtime exploitability was not demonstrated. | Next and matching ESLint config upgraded to 16.4.3, transitive fixes applied; subsequent audit reports zero. |
| Unbounded authenticated request load | Each ranking request scores a season. Authentication alone does not bound an account's load. | Redis-backed per-account budgets: 120 reads/minute, 20 mutations/minute; 429 + Retry-After, fail closed on Redis failure. |
| Local infrastructure exposed beyond loopback | Development compose published Postgres/Redis on all interfaces. | Both ports now bind 127.0.0.1. Production bindings already did. |
| Unbounded compiled scoring cache | ConcurrentHashMap retained every accessed profile. | Synchronized 1,024-entry access-order cache; owner checks retained on hits. |
| Member access to operational health detail | `when-authorized` without an operator role also covers ordinary authenticated users. | Health details disabled; liveness still available. |
| Account switch cached data | Invalidation could retain prior-account data while fetching. | Clear query cache before exposing a newly authenticated account. |
| Refresh completion after logout | An in-flight refresh could overwrite cleared access-token memory. | Session generation guards reject refresh results from a replaced session. |

The new member limiter makes Redis a dependency for authenticated data reads as well as auth. Health checks and headless ingestion do not consume member buckets. Non-limiter integration suites explicitly disable member limits because they do not provision Redis; the Redis integration suite enables and tests them.

Existing protections retained: default-deny endpoints, per-profile ownership checks including warm caches, Argon2 password hashing, HttpOnly/Secure/Strict refresh cookies, replay detection, nonce CSP, redirect validation, and proxy header sanitization. This review does not certify that all vulnerabilities have been found.

## Validation and limits

Frontend tests, lint, production build, backend compilation, focused board tests, API integration tests, ownership/query safety tests, authentication and Redis rate-limit tests were run. Tests exercise page-independent ranks, flattened JSON, matching board/detail figures, explicit missing projections, stored projection scoring, and account-based read throttling despite forged forwarded addresses.

Browser visual verification and before/after screenshots could not be completed: the supplied in-app browser backend reported `Browser is not available: iab`. Responsive CSS is implemented but has not been visually certified. No production penetration testing, backend dependency advisory scan, or deployed proxy acceptance checks were performed.

Remaining differences from the full design plan: complete-population search is achieved by bounded client page loading, not a server search parameter; projection explanations aggregate bonuses; projection exclusion counts, preset comparison strip, dedicated no-upcoming-week metadata, and a persisted run-level freshness contract remain to be completed. Projection target selection currently reads the latest stored provider week and flags old completed weeks as stale. It must not be described as a fully implemented projection architecture slice.

## Operations

Keep `fantasykai.projections.job-enabled=false` in production until the documented appearance backfill and projection activation checks in `docs/projections-v1-architecture.md` and `DEPLOY-STEPS.md` are completed. Existing V1–V9 migrations were not modified. Member rate-limit settings are `fantasykai.auth.member-reads-per-minute`, `member-writes-per-minute`, and `member-limits-enabled` (default true). Preserve private deployment secrets; none were inspected or included here.

## Live local-server review

Connected to localhost:3000 and localhost:8080. The existing API process predated these changes (started September 29); replaced it with the current source on port 8080. For this review, scheduled ingestion, startup backfill, and projection generation are disabled. The running Docker containers still have their old all-interface port bindings: applying the loopback compose changes requires container recreation, which was not performed during this review.

Used a temporary local account through the normal registration endpoint, then deleted that exact account and its cascading refresh tokens. Current data: 496 season/per-game players, 481 last-four-week players, through week 5 of 2026. All three scopes returned HTTP 200; board and player-performance responses matched exactly. Season career points, PPG, games, and positional rank matched the board. Pagination started page two at rank 201. Projection reads returned UNAVAILABLE; liveness returned UP.

Fixed a disabled-query pagination edge case on projection navigation; reduced tablet column widths and deferred weekly-result columns until desktop; constrained projection-row identity text for mobile. Added the recorded data-through week and visible board-mode selection. These layout changes are source-reviewed, not visually verified: browser discovery returned an empty list, so screenshots and interactive navigation review remain blocked.
