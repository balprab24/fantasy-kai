# Codex prompt — 2026-11-02: fix production My Teams, land S2/S3, make the board dense

Paste everything below the line into Codex.

---

You are working in the fantasy-kai repo (`balprab24/fantasy-kai`). Read `CLAUDE.md`,
`CONTRIBUTING.md`, `docs/architecture.md`, `docs/development.md` and `DESIGN.md` first. The
project's rules apply in full:
- measure before asserting;
- every change ends with the review pass in `CONTRIBUTING.md`;
- commit only on a branch, never straight to `main`;
- **no production deployment without the owner's explicit go-ahead in this session.**

Start from a fresh worktree on `origin/main` (`72fbe50` or later). The owner's checkout at
`~/fantasy-kai` has uncommitted edits of their own. Do not touch it.

## Task 1 — diagnose and fix "error under Your leagues" on production My Teams

**What was found (Claude Code, 2026-11-02; re-verify each line before acting on it):**
- **Vercel production is `72fbe50`**, the PR #76 merge, so `www.fantasykai.com/teams` is live
  (`gh api repos/balprab24/fantasy-kai/deployments`).
- **The production API has not been redeployed since 2026-09-29.** That is the last deploy the
  docs record, and the 2026-10-30 handoff states none happened after. So it has no
  `/api/v1/leagues` route and no V7–V11 migrations.
- **What a member sees:** `GET /api/v1/leagues` answers 404, and the page shows "Your leagues
  could not be loaded" (`frontend/src/app/(app)/teams/page.tsx`).
- **Why the check can't be made anonymously:** an anonymous request is 401 on every path,
  existing or not, so it cannot tell the two cases apart. Confirm with a member token, or by
  reading the API image's build on the VM.

**What to do:**
1. **Confirm the cause** by evidence, not inference: what the VM is running (`deploy/README.md`
   §7 says where to look) and what a member request to `/api/v1/leagues` returns. Record it.
2. **Prepare, but do not run, the API deploy.** Write the exact runbook steps for deploying
   current `main`:
   - Flyway V7 through V11 on the production database. Back up first, per `deploy/README.md`.
   - The projection job **stays disabled** (`fantasykai.projections.job-enabled=false`).
   - Scheduled ingest is unchanged.
   - Acceptance checks after (`deploy/acceptance.sh`), and a member-token check that
     `/api/v1/leagues` returns 200 `[]`.
   - **Ask the owner before executing any of it.**
3. **Make the page honest until the API catches up.** A 404 from `/api/v1/leagues`, meaning the
   route is missing, should read "League import is coming to your account shortly", not an error
   with a retry. Any other failure keeps today's message. Add a node test for the classification.
   This change ships with Task 2's PR or on its own: owner's choice.

## Task 2 — land S2 and S3, which are stranded off `main`

PRs #77 (S2) and #78 (S3) were merged into their stacked bases (`feat/leagues-s1`,
`feat/leagues-s2`) **after** #76 had already merged into `main`. So their commits are on
`origin/feat/leagues-s2` and `origin/feat/leagues-s3`, and not on `main`. Verify with
`git log origin/main..origin/feat/leagues-s3`.
- Open a PR **`feat/leagues-s3 → main`**, which carries S2 and S3, after rebasing or merging onto
  current `main`.
- **What S3 contains:** the win-probability model and a calibration gate that **failed three
  pre-registered attempts**. Trades is **not** built and must stay out of the nav. The record is
  `docs/perf/trade-calibration.md`.
- Run every gate: `./mvnw -B clean verify`; frontend lint, test and build;
  `./scripts/session-check.sh`. Paste their output.
- **Do not merge without the owner.**

## Task 3 — the signed-in board, as dense as Flock's (the owner's main ask)

**The owner's words:** the rankings page "still doesn't look the way I want … like Flock's … the
same or similar format … that doesn't waste a bunch of space."

**Reference:** `flockfantasy.com/rankings`, public. Study the *layout density*. **Do not copy**
its brand, mascot, artwork, colours or text; fantasy-kai keeps its own "Prime Time" tokens
(`DESIGN.md`, `globals.css`).

**What Flock does that we do not** (measured at 1460×812, 2026-11-02):
- **No display-size page title.** The page name is small, in the top bar. Content starts at once,
  and the first player row is about **210 px** from the top. Ours is about **430 px**, below the
  display "RANKINGS" title, a recipe sentence, a separate four-card scoring panel, a filter row
  and a note.
- **A slim left icon rail** (about 90 px, icon plus short label) holds the app's sections, so the
  board gets the full height and almost the full width.
- **One control strip** holds everything: the scope/format chips, search, the position chips
  (All QB RB WR TE) and a filters button. It is a single row of about 48 px.
- **Dense rows** of about 42 px: rank, a 40 px face, the name, a position chip with its rank
  ("RB 1"), then compact data cells (points, team, games, snap %, positional finish, overall
  finish). Cells carry a quiet heat tint for where the figure ranks.
- **Tier headers are compact bands** that also carry the column labels, so no separate sticky
  header block is needed per tier.
- **An optional right-hand panel** (filters and display options) on wide screens, closable.

**Already built once, on the old branch (port it, don't merge it):** commit `7665721` on
`origin/feat/projections-availability` built a Flock-style member shell on the pre-redesign
frontend:
- `components/shell/Sidebar.tsx`, the left rail;
- a one-line `components/rankings/FilterBar.tsx` with hairline seams from 2xl;
- a compact `components/rankings/PlayerRow.tsx` with recent form;
- a `TierHeader` that carries column labels;
- `Projections.tsx`.

That branch diverges from `main`'s redesign by about 8,800 deleted lines, so **port the
components and ideas onto `main`'s current code**. `docs/CLAUDE-HANDOFF-2026-10-28.md`, on that
branch, records what was measured and QA'd.

**Data:** `main` already serves `GET /api/v1/rankings/board`, from PR #75's `BoardController`:
- position ranks;
- per-game positional ranks;
- recent points per game and form;
- the last four weeks;
- usage (pass attempts, carries, targets);
- `dataThrough`.

Use it for the dense board's cells instead of deriving them in the browser. **Don't show a
metric the API does not compute** (a `DESIGN.md` rule). There is no snap % on the board endpoint
today: either add it to `BoardController` with a test, or leave the column out.

**Acceptance (measure each and paste the evidence):**
1. **At 1440×900 signed in:**
   - the first player row is within **220 px** of the viewport top;
   - **at least 16 player rows** are visible without scrolling;
   - no horizontal page scroll.
2. **One control strip** holds scoring (a compact segmented control or menu, not four cards),
   view, season, window, position, search and filters. It wraps cleanly at md/lg, and at 390 px
   wide it becomes two rows at most.
3. **Rows ≤ 44 px.** Columns are tabular figures, with the first column pinned when the table
   scrolls inside its panel on narrow screens.
4. **Navigation:** a left rail from lg (Rankings, My Teams, Scoring, then Coming items as
   disabled entries with a "Coming" note, never links), and the existing compact top bar plus
   menu below lg.
5. **The logged-out landing page (`app/(site)/page.tsx`) is unchanged.**
6. **`DESIGN.md` is updated in the same PR:**
   - the "Prime time" rules that mandated the display page title, the headline Format Bar and
     section panels on the board are replaced by the dense layout;
   - recorded as an owner decision dated 2026-11-02;
   - with every new text pair's contrast measured and recorded in `globals.css`, per `DESIGN.md`.
7. **Gates:**
   - frontend lint, `npm test` (add tests for any new pure logic) and `npm run build`;
   - `./scripts/session-check.sh` with 0 FAIL and 0 drift, with the baseline updated if counts
     move;
   - browser QA with an ordinary local account, created through `/register` and deleted after,
     at 1440, 1024 and 390 wide (if the window cannot reach 390, say so);
   - screenshots attached to the PR;
   - **never bypass authentication to fabricate a visual check.**
8. **A review write-up** in `docs/reviews/`, in the house format: findings, what was not fixed,
   and the argument against.

**Constraints:**
- No new dependencies without saying why.
- Keep `RequireAccount`, the member hint and the CSP nonce handling intact.
- The board's existing behaviour stays: tiers, ruleset switching with movement, the player
  overlay with scroll restore, and URL state (`lib/boardParams.ts`).

## Report back
For each task:
- what you verified;
- what you changed;
- the gate output;
- what you did not do and why;
- anything that needs the owner (the deploy, PR merges).
