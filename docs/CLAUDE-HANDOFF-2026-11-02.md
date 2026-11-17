# Claude Code handoff — 2026-11-02

Read this first, then `CLAUDE.md`, `docs/research/2026-10-28-leagues-trades-plan.md` (the league
plan, S0–S6), `docs/perf/trade-calibration.md` and `docs/research/2026-10-30-s4-espn-plan.md`.
Run `COMPOSE_PROJECT_NAME=fantasy-kai ./scripts/session-check.sh` in the league worktree; without
that variable it looks for the wrong Compose project.

## Where everything is

- **Worktrees:**
  - `~/fantasy-kai-leagues`: the league work, now on `feat/leagues-s3` at
    `ec59cc0`.
  - `~/fantasy-kai`: the owner's original checkout on
    `feat/projections-availability` (`7665721`). It holds **the owner's own uncommitted
    whitespace edits in `RulesetJson.java`** and an untracked `.claude/local/`. Never clean,
    stash, overwrite or commit them.
- **`main` is `72fbe50`** (PR #76 merged: S0 + S1, Sleeper import and My Teams).
- **PRs:** #75 (S0) and #76 (S1) are merged into `main`. **#77 (S2) and #78 (S3) were merged into
  their *stacked bases*, not into `main`.** #76 went into `main` 16 s before #77 was merged into
  `feat/leagues-s1`, so these commits are on `origin/feat/leagues-s3` and **not on `main`**:
  - `f621073` ExplainedScore and the player-filtered history read;
  - `52a1c70` the σ fit;
  - `619961d` S2: rest of season, LineupOptimizer, TeamNeeds;
  - `fb90be5`, `85f5021`, `7b8bacd`, `ec59cc0`: S3, win probability and three calibration
    attempts.

  To land them, open a PR `feat/leagues-s3 → main` (Codex prompt, Task 2).
- **Uncommitted in the league worktree:**
  - `docs/research/2026-10-30-s4-espn-plan.md`, the S4 plan (not started);
  - `docs/CODEX-PROMPT-2026-11-02.md`;
  - this file;
  - Codex's `docs/CLAUDE-HANDOFF-2026-10-30.md`, deliberately never committed.
- **Local services:**
  - Docker Postgres on :5433 and Redis on :6379, Compose project `fantasy-kai`. The local
    database is at **V11**.
  - The API on :8080 and `next dev` on :3000 were started from the league worktree on
    `feat/leagues-s2` code, with the ingest, backfill and projection jobs disabled.
  - Check `lsof -iTCP -sTCP:LISTEN` before starting anything. They may have stopped with the
    previous session.
- **The local mirror is stale:** last ingest 2026-10-14, complete only through week 5.
  `./scripts/ingest-once.sh` refreshes it.

## Production — the owner's report: "My Teams has an error under Your leagues"

- **Vercel production is `72fbe50`**, so `/teams` is live. The frontend deploys from `main` on
  merge.
- **The production API has not been redeployed since 2026-09-29**, the last recorded deploy, with
  no deploy since per the 10-30 handoff. It therefore lacks `/api/v1/leagues` and V7–V11.
- **So a member's `GET /api/v1/leagues` returns 404, and the page shows "Your leagues could not be
  loaded".** This is the deploy-order risk recorded on #76.
- **Not yet confirmed with a member token.** Anonymous calls are 401 on every path, so they cannot
  tell the two cases apart. Confirm before acting.
- **The fix is an API deploy of current `main`.** It runs Flyway V7–V11; the projection job stays
  disabled; acceptance checks follow (`deploy/README.md` §7, `deploy/acceptance.sh`). **This needs
  the owner's explicit go-ahead. No production deployment has been authorised in any session so
  far.**
- **Interim UI fix proposed:** treat a 404 from `/api/v1/leagues` as "not available yet", not an
  error (Codex prompt, Task 1).

## The owner's design ask — a Flock-dense board

The owner wants the signed-in rankings page "like Flock's … that doesn't waste a bunch of space".
- **Measured at 1460×812:** Flock's first player row is about 210 px from the top; ours is about
  430 px.
- **Flock's layout:** a slim left rail, one control strip, rows of about 42 px, tier bands that
  carry the column labels, and a closable right filter panel.
- **A Flock-style shell was already built on 2026-10-28** (`7665721`, on the old branch:
  `Sidebar.tsx`, a one-line `FilterBar`, a compact `PlayerRow`). `main` kept the October redesign
  instead.
- The full brief, with measurable acceptance, is **`docs/CODEX-PROMPT-2026-11-02.md`, Task 3**.
  `DESIGN.md` must change with it, recorded as an owner decision of 2026-11-02.

## League plan status

| Slice | State |
|---|---|
| S0 matcher | merged (#75) |
| S1 Sleeper import, My Teams | merged (#76); the **frontend is live, the API is not** (above) |
| S2 rest of season, optimizer, team needs | built and reviewed (`docs/reviews/2026-10-30-leagues-s2.md`); **stranded off `main`** |
| S3 trades | **held.** The calibration gate failed three pre-registered attempts: Brier 0.2332, 0.2265, 0.2257 against a bar of 0.225. Calibration is solved out of sample (slope 1.01); what remains is the projection's resolution. **No evaluator or `/trades` page, and Trades stays out of the nav.** |
| S4 ESPN | planned (`docs/research/2026-10-30-s4-espn-plan.md`), **not started**, by owner instruction |
| S5 Yahoo, S6 league-aware rankings | planned in the league plan |

## Waiting on the owner

1. **Deploy the API** (it fixes production My Teams). Go or no-go, and when.
2. **Merge the S2 + S3 PR** into `main`, once it is opened.
3. **What happens to Trades:** better projections (research), changing the bar (to be recorded
   as decided after three near misses, 9.4% and 9.7%), or parking it.
4. **For S4:** an ESPN league id; for S4b, `LEAGUE_CREDENTIAL_KEY` on the VM, and the ESPN budget
   (120 calls a minute proposed).
5. **A Sleeper username or league id**, for S1/S2's real-league checks, still open.

## Working rules that held all session

- **Pre-register before measuring.** The bar and every harness choice are committed before the
  first run, which is reported whatever it says. **No further calibration attempt without the
  owner.**
- **QA:** throwaway `@example.test` accounts through `/register` on localhost only, deleted after,
  with credentials kept in the scratchpad and never in chat or the repo. A league seeded by SQL is
  labelled as seeded in any report. Never bypass authentication.
- **In `next dev`,** the first submit on a freshly compiled `/register` sometimes loses its input,
  sending no request. Retry once the page is hydrated. Not seen on a built page; cause unknown.
- **Never print `.env`.** `.cache/` (the nflverse downloads) is now gitignored.
- **Commit only on request.** The exception was pre-registration commits, made locally so they
  predate their code, and pushed later on request.
