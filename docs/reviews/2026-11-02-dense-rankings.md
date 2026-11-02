# Dense member rankings and league availability — review, 2026-11-02

Branch `codex/dense-rankings-2026-11-02`, created in a fresh worktree from main `72fbe50`.
This is separate from the S2/S3 PR so the layout can remain draft until visual acceptance.
No new dependency. No change to the public landing's source or rendering components.

## Findings

1. **P1 release gate, open: signed-in browser QA is not available in this session.** The browser
   skill initialized but reported `Browser is not available: iab`; its discovery list was empty.
   The Flock URL also failed through the read-only web tool. The supplied reference measurements
   and the old `7665721` rail/control components were inspected, but that is not a fresh visual
   comparison. Do not claim the density targets have passed, attach manufactured screenshots, or
   promote this PR from draft until the checklist below is executed.
2. **P1 deploy-order risk, fixed in code:** the running production jar lacks `BoardController`.
   Switching exclusively to `/rankings/board` would turn working rankings into an error before
   the API deploy. Only a 404 falls back to the existing `/rankings` points response. Additional
   metrics stay explicitly null, shown as dashes, with “Extended stats coming shortly”. A missing
   private profile remains 404 on the legacy route too. Auth, rate-limit, network and server errors
   are not reclassified. New tests preserve absent metrics, zeros and whole-population ranks.
3. **P2, fixed:** My Teams classified a missing collection route as an error. Only that collection's
   404 now reads “League import is coming to your account shortly” without a retry, and disables
   the unavailable discovery form. Other errors retain the existing alert and retry.

Reviewed every changed hunk for URL serialization, search debouncing, stale ranking movement,
player overlay links and saved scroll position; absence versus zero; mobile overflow; disabled
Coming items versus links; duplicate navigation; token storage, session restore and CSP changes;
shared landing dependencies; and text/background pairs. Auth code is untouched. No simulated
member session or hidden authentication bypass was used.

## Implemented contract, not yet browser measurements

- 88px icon rail from lg, top bar/menu below lg; held Trades absent.
- One control strip: scoring, Overview/Usage, season, window, position, search, Filters.
  Native selects and a two-group phone arrangement; all options remain usable.
- 38px row target with 36px face, 24px tier bands carrying column labels, tabular figures,
  internal horizontal table scroll with pinned rank. A closable Filters section controls tiers.
- Board API supplies positional/per-game ranks, recent points, form, last four weeks and usage.
  No snap percentage invented. View is URL state; a ruleset change retains movement and a
  collapsed biggest-moves disclosure. Existing intercepted player route and return logic remain.
- DESIGN.md records the 2026-11-02 owner decision. New contrast pairs were calculated using WCAG
  relative luminance and sRGB alpha compositing, and recorded in globals.css. The lowest new
  foreground pair is faint/Well at 4.99:1; green on its 10% tint is 8.37:1, 7.55:1 over hover Well.

## Gates — actual output

The appended transcript contains local output. `git diff --check` is clean. The public site,
landing components/data, RequireAccount, memberHint, csp and proxy paths have an empty diff
against main. `globals.css` adds only the contrast comment; shared landing quality tokens are unchanged.
An anonymous HTTP request to `http://localhost:3001/rankings` returned:

```text
HTTP/1.1 307 Temporary Redirect
location: /login?next=%2Frankings
```

This is an auth regression check, **not browser QA**. Backend verify is not repeated for this PR:
no Java, migration, dependency or backend configuration changes. The separate S2/S3 PR ran its
full clean verify, including the new empty-roster regression.

## Browser acceptance still required

Use an ordinary throwaway `@example.test` account created through **localhost:3001/register**;
keep its credentials private and delete the account after testing. The original app remains on
3000, and the local API is on 8080. Never seed or bypass auth for visual acceptance.

| Check | Evidence required | Current result |
|---|---|---|
| 1440×900 signed in | First row top ≤220px, ≥16 complete visible rows, each ≤44px, page scrollWidth ≤ innerWidth | NOT RUN |
| 1024 wide | Rail, control wrapping, table scrolling, no page overflow | NOT RUN |
| 390 wide | At most two control rows; rank stays pinned during horizontal table scroll; compact menu | NOT RUN |
| Scoring and URL | Switch profiles, observe movement; season/window/position/search/view survive reload | NOT RUN |
| Player | Open overlay after filter changes; close/back restores scroll and board; cold route works | NOT RUN |
| Filters | Open/close, keyboard focus, tier toggle, clear position/search | NOT RUN |
| Logged out | Landing screenshot unchanged, member route redirects | Source unchanged; HTTP redirect checked |
| My Teams | Actual missing API route shows coming shortly; other failure still shows retry | Classification unit test passed; browser pending |
| Production diagnosis | Owner-signed-in GET /api/v1/leagues: capture status/shape only | Pending owner browser |
| Screenshots | Attach 1440/1024/390 images to this PR | NONE; browser unavailable |
| Cleanup | Delete exactly the QA account and confirm removal | No account created |

## Invariants and failure classes

No persistence, stats indexes, SQL, stat/signal registry, rounding engine, migration, ddl-auto or
external signal dependency changed. Member ranks are copied from API fields, not recomputed from
loaded pages. The legacy compatibility path preserves absence rather than presenting zero or an
invented rank. API ownership checks still precede response delivery. No transactional write,
ResultSet access, Redis client or backend bean changed. Tool failures were reported as failures;
no time-based freshness inference is used as production-image evidence.

## What I did not fix, and why

- No production deploy or merge: explicitly excluded by the owner's request. Deploy preparation
  and read-only VM evidence are in `docs/API-DEPLOY-2026-11-02.md`. Missing classes and V6 are
  confirmed, but a member HTTP 404 has not yet been observed.
- No fresh Flock or local browser captures: the supported browser is unavailable. Source-level
  dimensions cannot prove rendered density, wrapping, interaction or visual quality.
- No new projection view: the View menu switches real Overview/Usage cells already computed by
  the board endpoint. The projection job stays disabled; no unsupported prediction UI is implied.
- No local ingest or landing recapture: the owner required the logged-out landing unchanged. The
  local data freshness warnings are accurately retained.
- No commit of the four original untracked handoff/plan files; they remain in the league worktree.

## The argument against this change

This is not ready to merge until the rendered board meets the requested measurements and the
phone controls and player-return flow have been exercised. The denser layout trades some touch
space and immediate scoring explanations for more visible players, and weekly columns require
horizontal scrolling on phones. The compatibility fallback adds one failed request per page
while the old API runs, and should be removed only after the production route is verified live.
A failure to fit 16 rows, an inaccessible mobile control, horizontal page overflow or a changed
logged-out landing is a reason to revise the implementation, regardless of passing builds.

## Gate transcript

`lint`

```text
> frontend@0.1.0 lint
> eslint
```

`test`

```text
✔ board display URL (0.757458ms)
ℹ tests 106
ℹ suites 34
ℹ pass 106
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 142.9735
```

`build`

```text
> frontend@0.1.0 build
> next build

▲ Next.js 16.4.3 (Turbopack)
✓ Running next.config.ts took 14ms

  Creating an optimized production build ...
✓ Compiled successfully in 427ms
  Running TypeScript ...
  Finished TypeScript in 1006ms ...
  Collecting page data using 7 workers ...
  Generating static pages using 7 workers (0/9) ...
  Generating static pages using 7 workers (2/9) 
  Generating static pages using 7 workers (4/9) 
  Generating static pages using 7 workers (6/9) 
✓ Generating static pages using 7 workers (9/9) in 156ms
  Finalizing page optimization ...

Route (app)
┌ ƒ /
├ ƒ /_not-found
├ ƒ /(.)players/[id]
├ ƒ /login
├ ƒ /players/[id]
├ ƒ /profiles
├ ƒ /rankings
├ ƒ /register
├ ƒ /teams
└ ƒ /teams/[id]


ƒ Proxy (Middleware)

ƒ  (Dynamic)  server-rendered on demand
```

`session`

```text
fantasy-kai — session check · 2026-11-02 12:11 CST

TOOLCHAIN
  ok    java        25.0.4.1 at ~/Library/Java/JavaVirtualMachines/jdk-25.0.4.1+1/Contents/Home
  ok    docker      postgres healthy · redis healthy (:5433, :6379)
  ok    node        v24.21.0 (.nvmrc wants 24.21.0)
  ok    env         .env present · JWT_SECRET 57 bytes

REPO
  warn  branch      codex/dense-rankings-2026-11-02, 21 uncommitted file(s)
  ok    sync        level with origin/codex/dense-rankings-2026-11-02
  warn  branches    merged into origin/main, safe to delete: feat/leagues-s0 feat/leagues-s1 feat/projections-backtest redesign/foundations redesign/landing redesign/player redesign/rankings
  ok    github      0 open PR(s) · last CI on main: success

SCHEMA
  ok    migrations  V1..V11 applied, all 11 checksums match the files on disk

DATA
  ok    rows        stats 117,820 · players 25,069 · games 1,965 (+3,341 since the docs)
  warn  ingest      local mirror last refreshed 2026-10-14 05:16, 19d 7h ago -- ./scripts/ingest-once.sh when you need current weeks
  warn  hero        landing hero shows 2026 through Week 6, but Week 7 finished 2026-10-26 -- the mirror is complete only through Week 5: ./scripts/ingest-once.sh, then re-capture it (docs/map.md §4)
  ok    launchd     not installed -- by design since 2026-09-29; production is the only scheduled ingest (DEPLOY health)
  ok    jar         source hash matches the jar (5462c2a33802)

DEPLOY
  ok    liveness    https://api.fantasykai.com is up
  ok    health      aggregate UP

CLAIMS  (a number here disagreeing with the baseline means the tree or the baseline moved)
  ok    tests       428
  ok    endpoints   24
  ok    backend     129
  ok    frontend    96
  ok    migrations  11

not checked here: does the suite pass · does the frontend build
  the gates for those are in CONTRIBUTING.md under "Review pass".

0 FAIL · 0 drift · 4 warn · 0 unknown
```

