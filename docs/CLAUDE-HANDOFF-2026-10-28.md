# Claude Code handoff — Fantasy Kai, 2026-10-28

## User request and current state

The user requested a codebase/security review and a redesign of the signed-in homepage inspired by the attached Flock Fantasy screenshot, preserving the player-detail layout. They approved a compact sidebar, dark dense board, actual performance plus projections, and security fixes. Default view is season performance; projections belong in a separate board mode and a compact additional player-detail panel.

The reference image is `/Users/prabhnoorbal/Downloads/homeflock.png`. Follow its density, tier sections and information hierarchy, not an exact clone. The public marketing homepage is not the redesign target.

Implementation is **partial**, all changes are uncommitted. Do not describe this as the entire approved plan completed. The user now requested this handoff and that all project processes be stopped. Do not resume servers until beginning the next authorized work session.

## Preserve existing work

- `backend/src/main/java/com/fantasykai/scoring/RulesetJson.java` had user edits before Codex started. Preserve them. Its trailing whitespace/new EOF line were already present; do not attribute them to this implementation.
- `.claude/local/` was already untracked. Do not delete or overwrite it.
- No production deployment, production data changes, or projection activation occurred.
- No migrations were edited or added. Secrets were not included in this document.
- Read repository guidance and inspect the complete git diff before proceeding.

## Implemented

### Member UI

- Desktop 176px sidebar (`Sidebar.tsx` / `AppShell.tsx`); existing top bar remains on mobile.
- Dense charcoal rankings rows, tier bands, recent four-week scores, recent PPG/form, position-specific usage, phone disclosure, visible mode selection, and data-through-week label.
- Existing player identity, performance chart, game log and career structure retained. `BoardContext.tsx` explicitly shows the source scope's figures above the full-season report; `Projections.tsx` adds a projection panel.
- Historical board URLs preserve profile/season/scope; return-scroll handling waits for sufficient pages to reload.
- Marketing specimens retain their eight-column contract and comparison bars; the member board uses ten columns. Review shared `COL`/row components carefully for accidental marketing regressions.
- Responsive widths were adjusted by source inspection, **not screenshots**.

### API/data

- New `BoardController`: `/api/v1/rankings/board` and `/api/v1/players/{id}/performance`.
- Ranks and qualified PPG ranks computed over the full eligible population before paging. Existing `/rankings` remains unchanged.
- Last-four results use league regular-season weeks; no recorded appearance is null, not zero. Usage is a bulk aggregate query in `PlayerQueryRepository.boardUsage`.
- Frontend automatically fetches bounded 200-player pages to complete historical search coverage. This differs from the approved server-search design.
- Fixed automatic historical pagination continuing when switching into projections.
- New `ProjectionController`: `/api/v1/projections` and `/api/v1/players/{id}/projection`. Reads stored raw lines, uses caller scoring, resolves current known availability, excludes unavailable players from projected rankings, supports server name search, and labels stale stored weeks.
- Explicit UNAVAILABLE for absent stored projections; BYE/NO_HISTORY/NOT_ROSTERED only when a stored target and supporting roster evidence exist.
- Never runs projection generation in an HTTP request; job remains disabled.

### Security

- Next.js and eslint-config-next updated to 16.4.3-compatible ranges; transitive fixes applied. Last npm audit found zero vulnerabilities.
- Redis-backed per-account member limits: 120 GETs/minute, 20 other requests/minute, separate from existing auth buckets; 429/Retry-After, fail closed on Redis outage. Health and auth paths excluded from member budget.
- `member-limits-enabled` defaults true. Most API integration suites disable it because they lack Redis; `AuthRateLimitTests` explicitly enables it and checks 120 requests plus rejection and account isolation.
- Compiled rules cache capped at 1,024 entries, synchronized access-order eviction; ownership check retained on every hit.
- Health details set to never (ordinary members previously qualified for operational details).
- Query cache cleared on account authentication; session generation guard prevents an older refresh from replacing token memory after logout/account replacement. Browser race behavior is not yet regression-tested.
- Development compose now binds Postgres/Redis to loopback. The previously running containers still used old published bindings until stopped; use `docker compose up -d` to recreate with updated configuration, not `docker start`.

## Verified

- 89 frontend tests pass; ESLint passes; Next production build passes.
- 89 backend tests passed across BoardTests, RankingsTests, ReadApiTests, ScoringProfileIsolationTests, QuerySafetyTests, AuthTests and AuthRateLimitTests. This is **not the full backend suite**.
- Additional focused scoring/tally tests passed earlier.
- Tests cover flat board JSON, population ranks independent of loaded page, board/detail parity, absent projection data, stored projection scoring without rewriting raw stats, authorization/query safety, and Redis account limits despite forged forwarded IP headers.
- The old API process on 8080 was from September 29. Restarted current source and verified both localhost:3000 and API liveness returned HTTP 200.
- Used a temporary normal registered account for live HTTP checks, then deleted that exact account and its refresh tokens. No test credentials need retaining.
- Live local data: season/per-game boards 496 players; last-four board 481; latest recorded week 5 of 2026. Season leader Josh Allen 142.46 points / 28.49 PPG; last-four leader Jahmyr Gibbs 109.1 / 27.28. Board and player performance JSON matched, career totals/PPG/games/positional rank matched, page two began at rank 201. Projections returned UNAVAILABLE.

## Outstanding work / review priorities

1. **Actual visual and interactive QA is missing.** In-app browser setup failed with `Browser is not available: iab`; discovery returned `[]`. No screenshot review, measured contrast, keyboard interaction, or end-to-end navigation was completed. Reconnect a supported browser and inspect desktop (1440/1920), tablet (768/1024), and mobile (390), including sign-in, search, profile switches, long names, details/back, no-data/errors, and projections. Do not claim source-derived layout fixes are visually proven.
2. **Projection API is not complete against `docs/projections-v1-architecture.md` slices 5/6.** Current target selection is latest stored provider week, not a persisted run-level target contract. Missing: run metadata/no-upcoming-week semantics, exclusion counts, per-bonus explanations (currently aggregate bonus points), and preset comparison strip. Check raw-vs-rounded sort semantics, missing-player states, freshness, partial runs, and availability correctness. Never silently enable generation or rewrite kickoff-locked rows.
3. **Historical search loads all pages client-side.** Implement server-side complete-population search if retaining the exact approved plan, preserving unfiltered rank/tier boundaries. Review request cost: each enriched page currently calls the existing full ranking calculation and a second season scoring scan; do not introduce N+1 queries or pretend this is optimized.
4. **Navigation:** projection links currently do not use the historical board-return mechanism. Verify/fix projection search/filter/page/scroll restoration. Board mode is in `view=projections`; historical scope remains separate.
5. **Security follow-through:** backend dependency advisory scan and deployed proxy acceptance checks were not performed. Review refresh/logout concurrency end to end, Redis outage behavior for reads (a new operational dependency), cache contention, rate-limit request coverage and malformed input, and operator health requirements. The security report is not a certification of absence of vulnerabilities.
6. Code style: several new components/controllers are compact and need readability cleanup after behavior is verified. Keep changes scoped and retain the user's RulesetJson edits.
7. Review shared styling and the sidebar logo/menu fit. Ensure public landing specimens are unchanged in behavior and appearance.

## Supporting files

- `docs/reviews/2026-10-28-workspace-security.md`: findings, implementation gaps, live-review notes.
- `docs/projections-v1-architecture.md`: established projection contract and activation constraints.
- Existing README, PRODUCT, DESIGN, CLAUDE and deployment docs: read critically; some prose predates current implementation.

## Process state and restarting

At handoff, the user requested shutdown. Frontend `npm run dev`/Next on 3000, Maven/Spring on 8080, and the two Fantasy Kai Docker containers are being stopped without deleting data volumes. Unrelated applications and system database services are outside this project's shutdown scope.

For a future authorized local review:

1. Start Docker if necessary, then `docker compose up -d` from repository root (applies loopback bindings; retains volumes).
2. Use JDK 25. Load root `.env` locally without printing secrets.
3. From backend, run `./mvnw spring-boot:run -Dspring-boot.run.arguments='--fantasykai.ingest.scheduled-enabled=false --fantasykai.ingest.backfill-on-startup=false --fantasykai.projections.job-enabled=false'` for review without background data generation.
4. From frontend, run `npm run dev`; default API URL is localhost:8080.
5. Use an ordinary account to test the UI. Never bypass authentication to fabricate a visual verification.

Temporary logs from this session may still exist in `/tmp/fantasy-kai-review-server.log`, `/tmp/fk-review-build.log`, `/tmp/fk-review-tests.log`, and `/tmp/fk-verified.log`. Treat logs as local artifacts, not durable evidence or files to commit.

Shutdown verified: no listeners on 3000 or 8080; fantasykai-postgres and fantasykai-redis are stopped. Data volumes retained.

## Claude review and cleanup — 2026-10-28 (later the same day)

Reviewed Codex's uncommitted diff against `DESIGN.md` and the codebase's own patterns, then
reworked the member UI. Still uncommitted, on `feat/projections-availability`.

**Fixed**

- **Design system restored.** Removed the `.product-shell` override that re-themed the app in
  charcoal grey (`#141414`/`#1e1e1e`/`#303030`, none of it contrast-measured), the rounded card
  rows, the brown tier gradients, both tracked-caps eyebrows and the sidebar tagline. The Flock
  *layout* stays — sidebar, dense rows, tier bands, recent-weeks columns — in Prime time tokens.
  `DESIGN.md` Product pages now describes it.
- **Mobile top bar sticky again.** It was wrapped in a `md:hidden` div, which confined `sticky`
  to the wrapper's height; the bar now hides itself.
- **Column layout back in one place.** Every `.performance-board … nth-child` width rule is gone;
  the member board's columns are `MEMBER_COL` beside the landing's untouched `COL`, counts derived
  from the maps, and `PlayerRow` takes an explicit `variant` instead of switching on whether the
  data happens to carry `recentWeeks`. A test reads both headers from source against the maps.
- **Types say what the API sends.** `RankingRow` lost its optional board fields; `/rankings/board`
  rows are `BoardRankingRow` with them required. Projection types moved to `lib/types.ts`, with
  the real state and availability values; a status is now worded per value (none for
  `AVAILABLE`), where the raw enum was lower-cased onto every row ("available", "not on roster").
- **Projections restructured.** Queries in `lib/queries.ts` (`useProjectionBoard` is paged like
  the board; `useAllPages` is the one auto-pagination loop for both views); the board view is
  `view` in `BoardState`, round-tripped by `parseBoardParams`/`boardSearch`; the switch is a
  `SegmentedControl` in the console, and season/window (with their seams) are not rendered in the
  projections view instead of being hidden by CSS. Projection rows save the board-return entry,
  so back from a player restores the view and scroll. The projections search follows the
  debounced URL search, not every keystroke. Loading, empty and error states use
  `BoardSkeleton`/`StatusMessage`.
- **Player page.** `Section` moved to `player/Section.tsx`; the projection is a `Section` after
  Performance and says nothing when nothing is stored (it never shifts the page above the fold);
  the board-context line shows only for the last-four board, where its figures differ from the
  season's, and says "no game in those four weeks" for a 404.
- **Backend formatting, no behaviour change except one.** `BoardController`,
  `ProjectionController`, `MemberRateLimitFilter`, `SecurityConfig`'s signature, `boardUsage` and
  the rules cache are formatted to house style with explicit imports. `ppgPosRank` is one sort per
  position instead of an O(n²) scan, pinned by a tie/qualification test (mutating the tie rule
  fails it). The one behaviour change: the usage window keys on the resolved `RankingScope`, so
  `scope=LAST4` no longer gets the last-four ranking with a whole-season usage line.

**Verified:** `npm test` 96/96, `npm run lint`, `npx tsc --noEmit`, `npm run build`; backend
`./mvnw -B verify -Dsurefire.exitTimeout=5`, the full suite: 360 run, 0 failures, BUILD SUCCESS.

**Still open** (beyond items 1–7 above, which stand):

- The Suspense fallback's console heights were measured before the view switch joined the
  console, and were not re-measured.
- On the `last4` board the Last 4 figure repeats Per G (both are the window's per-game); only
  its "vs season" line adds anything there.
- 1920px was not captured: the test display is 1710px wide. Phone was captured at 500px,
  Chrome's minimum window width, not 390.

**Browser QA, 2026-10-28 (Claude in Chrome, jobs off, a throwaway local account registered
through `/register` and deleted after with its refresh tokens).** At 500, 768, 1024, 1440 and
1710: no sideways scroll at 500; the phone top bar and the board's header both stay pinned while
scrolled; the sidebar, tier bands, week chips and the xl Last 4 / Recent weeks columns render in
Prime time tokens; projections view drops season and window and shows its empty state; a player
opened from the last-four board shows the board line, and back lands on the same row (scroll
restored once all 481 rows loaded); the Coming popover opens beside the sidebar and Escape
returns focus to its button; the signed-out `/` hero keeps the eight-column rows and plain tier
headers. One fix came of it: the console's seams were `md:block`, but beside the sidebar the
console wraps below ~1,485px and stranded a seam at the end of a line, so seams now show from
`2xl` only. The console needs about 1,250px of strip, and more profiles widen it.
- The projection board sorts by the **rounded** total (`Row.points`), so two players a hundredth
  apart can tie and order by id. Ranking sorts unrounded; projections should too.
- `ScoringProfiles`' bounded cache compiles a miss under the map's lock, serialising misses.
- `/players/{id}/performance` scores the whole board to return one row.
