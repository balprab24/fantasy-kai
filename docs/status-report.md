# Status report — fantasy-kai, 2026-11-10

Diagnosis only. No app code was changed, nothing was merged, rebased or pushed. Measured on a clean
worktree of `origin/main` at **`7721c2b`** (PR #83, fetched 2026-11-10) on branch
`chore/status-report`. Every number below came from a command run that day unless it is marked
*(doc)* — taken from a document and not re-derived — or *not run*.

## TL;DR

1. **`main` is healthy.** Backend `./mvnw -B verify`: **456 tests, 0 failures/errors/skips**. Frontend: lint clean, **104/104** tests, `next build` passes. Flyway V1–V11 applies to a fresh database and the jar boots with `ddl-auto: validate`. The last 8 CI runs on `main` are green.
2. **The redesign is not stranded.** All four `redesign/*` branches carry 0 unique patches; PR #72 rolled the stack up to `main`. What *is* stranded is smaller and different: the projections UI and three backtest docs on `feat/projections-availability`, one unmerged ingest fix, the draft dense-rankings PR #80, and `chore/ai-workflow`.
3. **Wrong-base merging happened three times** (redesign, projections, leagues), not once. Root cause is process: `ci.yml` only runs for PRs into `main`, and `main` is not branch-protected, so stacked slices (e.g. `redesign/rankings`, `feat/projections-*`, `feat/leagues-s2`) had **zero CI runs**.
4. **Top risks:** production API is behind the live frontend (My Teams 404s) *(doc)*; the "Vulnerability scan" check reports green while its scan is skipped; Spring Boot 3.5 has been out of OSS support since 2026-06-30; several handoff docs, a stash and an edit exist only on this laptop.
5. **Start with** the CODEX START HERE task at the bottom: make CI run on PRs against any base and add an `npm audit` gate. It is one file and removes the cause of the recurring loss.

---

## What's done (with evidence)

Verified at `7721c2b`. "Tests" are from the runs described in *Verification run* below.

| Feature | Status | Evidence |
|---|---|---|
| **Scoring rulesets** — PPR, half PPR, standard, TE premium, custom | **Done** (one UI gap, see Broken/risky) | Engine `backend/src/main/java/com/fantasykai/scoring/ScoringEngine.java`; presets seeded by `backend/src/main/resources/db/migration/V3__seed_scoring_presets.sql`; CRUD in `api/ScoringProfileController.java`; points are computed on read, never stored (`docs/architecture.md`, "The one idea"). Tests `ScoringEngineTests`, `NflverseOracleTests` (53 real stat lines vs nflverse), `ScoringProfileIsolationTests`, `RankingsTests` — all inside the 456 passing. UI: `frontend/src/components/RulesetBuilder.tsx`, `app/(app)/profiles/page.tsx`. |
| **Rankings board** | **Done** | `frontend/src/app/(app)/rankings/page.tsx`, `components/rankings/RankingsWorkspace.tsx`; backend `api/RankingsController.java` (`GET /api/v1/rankings`) used by `frontend/src/lib/queries.ts`. Switching the ruleset re-sorts the board (`RankingsTests` preset spread). |
| **Player view — overlay and full page** | **Done** | Overlay `frontend/src/app/(app)/@modal/(.)players/[id]/page.tsx`; full page `app/(app)/players/[id]/page.tsx`. `next build` lists both routes (`/(.)players/[id]`, `/players/[id]`). The "second copy opens as overlay" trap is fixed and pinned (`docs/development.md`, "An intercepting route…"). |
| **Heat-band grading + generator** | **Done**, regeneration manual | Generator `frontend/scripts/heat-bands.sql` → `heat-bands.mjs` → committed `frontend/src/lib/heatBands.ts` (header: "do not edit by hand"; 2023–2025 window; top 24 QB / 48 RB / 72 WR / 24 TE by volume). Consumer `components/player/PlayerTables.tsx`. Tests in `frontend/tests/lib.test.ts`. The generator was **not run** (needs a loaded DB and would rewrite a tracked file). |
| **Landing "rule mixer"** | **Done**, data is 3 weeks old | `frontend/src/app/(site)/page.tsx:73` renders `components/landing/RuleMixer.tsx`; logic `lib/mixer.ts`; six pre-captured boards in `components/landing/heroData.ts`. Four "the rule mixer" tests pass. **`HERO_CAPTURED = "2026-10-20"`** (`heroData.ts:19`). |
| **`HEADSHOTS_ENABLED`** | **Done — ON** | `frontend/src/lib/headshot.ts:26`: `export const HEADSHOTS_ENABLED: boolean = true;`, pinned by `frontend/tests/lib.test.ts` (imports and asserts it). It is a hard-coded constant, **not an env var** — flipping it is a source edit plus a frontend deploy. CSP admits `a.espncdn.com`. Owner decision 2026-10-20 (`PRODUCT.md`, "Evidence on Hand"). |
| **Snap-% grading** | **Done — graded** | `components/player/PlayerTables.tsx:228` calls `leagueStep(HEAT_WEEK, position, "snap", …)`; `frontend/scripts/heat-bands.mjs:19` ("graded at every position (owner decision, 2026-10-20)"). Per-game only — there is no career snap column. |
| **Data ingestion** | **Partial** (pipeline Done; hardening and refresh Missing) | Pipeline: `backend/src/main/java/com/fantasykai/ingest/` — one `*Ingestor` per source, `IngestService`, `SleeperCrosswalk` last (`IngestService.java:92`). Production scheduler `application.yml`: cron `0 0 6 * * *`, `scheduled-enabled: true`. Ingest tests all pass. Gaps listed under Broken/risky. |
| **Migrations** | **Done** | `db/migration/` V1–V11, contiguous, no duplicates. Fresh-DB boot: `flyway_schema_history` shows versions 1–11 all `success=true`. |
| **Auth, security, deploy** | **Done** *(doc)* | Phases 5a–5d shipped (`docs/north-star.md` §10). Tomcat pin verified in the built jar: `tomcat-embed-core-10.1.59.jar`. |
| **Leagues S0 (matcher) and S1 (Sleeper import, My Teams)** | **Done on main** | `backend/.../leagues/`, `V10`, `V11`, `frontend/src/app/(app)/teams/`. |
| **Leagues S2 (rest-of-season, optimizer, team needs)** | **Done on main** | Re-landed by PR #79 (merged 2026-11-04). `feat/leagues-s3` is an ancestor of `main`. |
| **Projections backend (Slices 2–4)** | **Done on main, switched off** | `backend/.../projections/` (provider, job, availability), `V7`, `V9`; re-landed by #75. `application.yml:119` `job-enabled: false`. |

### Verification run (this is what "real counts" means)

| Gate | Result |
|---|---|
| `cd backend && ./mvnw -B verify` (JDK 25.0.4.1, Docker) | **BUILD SUCCESS**, 1:25 min. Summed from 57 Surefire report files: **456 tests, 0 failures, 0 errors, 0 skipped.** Matches PR #79's claim. |
| `cd frontend && npm ci` | OK (reports 7 moderate vulns, see Debt). |
| `npm run lint` | exit **0**, no findings. |
| `npm test` (`node --test`) | exit **0**: **104 tests, 32 suites, 104 pass, 0 fail, 0 skipped, 0 todo**. |
| `npm run build` (Next 16.4.3) | exit **0**; routes `/`, `/login`, `/register`, `/rankings`, `/profiles`, `/players/[id]`, `/(.)players/[id]`, `/teams`, `/teams/[id]`. |
| Flyway against a **throwaway** Postgres 16 | Built jar booted in 3.6 s; 11 migrations applied; `ddl-auto: validate` passed. `/actuator/health/liveness` = `UP`. Overall `/actuator/health` = `DOWN` — expected on an empty DB (the ingest-freshness indicator goes DOWN when no pull has run, `docs/development.md`); I did not confirm which component, because anonymous callers cannot see details. The dev database on :5433 was never touched. Throwaway containers removed. |
| CI on `main` (`gh run list --workflow ci.yml --branch main`) | Last 8 runs of the CI workflow, all `success`; latest 2026-11-09 @ `7721c2b` (weekly schedule). In the latest run the "Vulnerability scan" job is `success` but its `OWASP dependency-check` and `Upload report` steps are `skipped`. |
| `./scripts/session-check.sh --no-network` | **0 drift**, 2 FAIL, 3 warn, 2 unknown. Both FAILs are artifacts of a fresh worktree (no `.env`; the jar-vs-mtime rule trips on any new checkout — the trap documented in `docs/development.md`). Its test count is 457 because it counts `@Test` annotations, not executed tests. |

**Not run:** the heat-band generator; OWASP dependency-check (needs `NVD_API_KEY`); browser QA of any page; `./scripts/ingest-once.sh`; the k6 perf script; any probe of production (its freshness and deployed schema are unverified here).

---

## In progress

| Branch / PR | What it is | What's left | Risk |
|---|---|---|---|
| **PR #80** `codex/dense-rankings-2026-11-02` (draft, 5 commits, 16 behind `main`) | Compact member rankings board (Flock-style: slim rail, 38 px rows, tier bands), My Teams treats a 404 as "coming shortly". Includes `docs/API-DEPLOY-2026-11-02.md` and a `DESIGN.md` change. | (1) **Rebase onto current `main`** — a dry-run merge conflicts in `frontend/src/lib/leagues.ts`, `frontend/tests/leagues.test.ts`, `scripts/session-check.sh`. (2) Narrow-width acceptance is **contradictory**: the review doc's QA table (`docs/reviews/2026-11-02-dense-rankings.md` lines 168–170) records passes at 1440/1024/390 on 2026-11-03, but the same doc (lines 131, 151–154) and the PR body still say "unmeasured", the 1024 screenshot predates a later change, and there is **no owner waiver**. (3) My Teams browser check and Filters details are listed as pending. | **Medium.** Touches the main workspace UI and `DESIGN.md`; its CI checks are green. |
| **`fix/ingest-fetch-deadline`** (1 commit `040a57e`, 118 behind) | Bounds the *whole* nflverse fetch (body included) with `fetch-timeout`, logs monotonic + wall clocks, adds `NflverseClientHttpTests` (8 tests). | Re-branch from `main`; conflicts only in `docs/map.md` and `scripts/session-check.sh`; update the test-count baseline; run `verify`. Its own message says it is **not** the root cause of the 2026-09-22..24 failures (suspected Mac sleep) and that retries are a separate task. | **Low.** Isolated to `ingest/`. |
| **`feat/projections-availability`** (11 ahead, 63 behind) — the checkout you left | Phase 6 Slices 5–7: the *old-style* projections UI and member board. The backend part already reached `main` via #75. | Stranded and absent from `main`: `frontend/src/components/player/ProjectionPanel.tsx`, `BoardContext.tsx`, `Section.tsx`, `components/rankings/Projections.tsx`, `components/shell/Sidebar.tsx`, `docs/perf/projection-backtest/v2-{diagnostic-2025,prospective-2026,validate-2024}.md`, `docs/reviews/2026-10-28-workspace-security.md`, `docs/CLAUDE-HANDOFF-2026-10-28.md`. It is built on the *pre-redesign* frontend, so **do not merge it** (a dry-run touches 30+ files incl. `DESIGN.md`, `CLAUDE.md`, `ProjectionController.java`). Rescue the 3 backtest docs and re-implement the UI on the redesigned workspace. | **High if merged, low if mined.** |
| **`chore/ai-workflow`** (2 ahead, 119 behind) + `stash@{0}` | Claude workflow skills, a forked reviewer, rules, a slimmed `CLAUDE.md`. | Dry-run merge conflicts in `.claude/commands/review-pass.md`, `.gitignore`, `CLAUDE.md`, `docs/map.md`, `scripts/session-check.sh`. The stash holds an unfinished attribution block in `.claude/settings.json`. | **Low**, but the stash is easy to lose. |
| **Leagues S3 (Trades)** | **Held** *(doc)*. Three pre-registered calibration attempts missed the bar (Brier 0.2332, 0.2265, 0.2257 vs 0.225). No evaluator or page, Trades is out of the nav. | Owner decision: better projections, change the bar, or park it. No further attempt without the owner. | Product, not technical. |
| **Leagues S4 (ESPN)** | Planned, not started. The plan `docs/research/2026-10-30-s4-espn-plan.md` is **untracked**, only in `~/fantasy-kai-leagues`. | Needs an ESPN league id from the owner. | — |
| **Phase 6 projections, overall** | Backend on `main`, job disabled. | Slice 5 (run metadata + explanations), Slice 6 (UI), Slice 7 (QA + monitoring). | Medium |

---

## Missing (vs PRODUCT.md / north-star)

- **Projections in the product.** `PRODUCT.md` says "Not built": projections. Backend exists and is off; no UI on `main` (the frontend has zero callers of `/rankings/board` or `/players/{id}/performance`; only #80 would add them).
- **Trades / evaluator** (held), **ESPN import** (S4), **Yahoo** (S5), **league-aware rankings** (S6).
- **Roster tools** (Phase 8: start/sit, waivers), **market consensus board** (Phase 9), **iOS app** (Phase 10), **perf pass** (Phase 11) — all planned, none started.
- **Spring Boot 3.5 → 4** (Phase 11.5) — now a dated security obligation (see below).
- **Edit a custom ruleset in the UI.** `PUT /api/v1/scoring-profiles/{id}` exists, but no frontend code calls it (the only `PUT` in `frontend/src` is My Teams' `my-team`). Users can create and delete a ruleset, not change one.
- **A refresh trigger.** There is no HTTP ingest/refresh endpoint (the only "refresh" route is `/auth/refresh`). Data refreshes only via the 06:00 ET job or `scripts/ingest-once.sh`.
- **Tests for the player overlay and full page.** Only the pure derivations in `frontend/tests/lib.test.ts` are covered; there is no component or e2e runner at all.
- **Docs the checklist expects but that are not on `main`:** the Phase 6 `v2-*` backtest result docs (stranded, above).

---

## Broken or risky (ranked by severity)

1. **Production API is behind the live frontend** *(doc; not probed)*. Vercel serves `main` (so `/teams` is live), but the production API has not been redeployed since 2026-09-29 or 2026-10-08 (the docs disagree) and lacks `/api/v1/leagues` and V7–V11. A member's My Teams shows "Your leagues could not be loaded". The fix is an API deploy (`docs/API-DEPLOY-2026-11-02.md` on the #80 branch; `deploy/README.md` §7) and **needs the owner's explicit go-ahead** — no session has been authorised to deploy. The deploy order (frontend outruns backend) is a documented, unsolved risk (`docs/map.md` §5).
2. **Stacked-PR process has no guard, and has already cost work three times.** `.github/workflows/ci.yml:3-9` filters `pull_request` to `branches: [main]`; `main` is **not protected** (`gh api …/branches/main/protection` → 404). Measured CI run counts per branch: `redesign/rankings` 0, `redesign/landing` 0, `feat/projections-{provider,job,availability}` 0, `feat/leagues-s2` 0. Recoveries so far: #72 (redesign), #75 (projections), #79 (leagues).
3. **The "Vulnerability scan" check is green but scans nothing.** `ci.yml:79` `continue-on-error: true`; with no `NVD_API_KEY` secret the OWASP step and the upload are skipped (`ci.yml:109-124`), emitting only a `::notice::` (line 104). Confirmed in the latest run: `OWASP dependency-check: skipped`, job `success`. PR #79 and #80 both show it as passing. There is no other scanner: no CodeQL, no `npm audit` step, no gitleaks. Dependabot security updates are off.
4. **Spring Boot 3.5.16 is past OSS end-of-life (2026-06-30)** (`backend/pom.xml`; `docs/development.md`). Only a one-line Tomcat pin (10.1.59, verified in the jar) covers the known advisories. Every month adds CVEs with no free upgrade path. Phase 11.5 is the fix and is not scheduled.
5. **Work that exists only on this laptop.** Untracked, on no remote: `docs/CLAUDE-HANDOFF-2026-11-02.md`, `docs/CODEX-PROMPT-2026-11-02.md`, `docs/research/2026-10-30-s4-espn-plan.md` (all in `~/fantasy-kai-leagues`); `docs/CLAUDE-HANDOFF-2026-10-30.md` (deliberately never committed); `.claude/local/` and `stash@{0}` in `~/fantasy-kai`. A one-line **uncommitted edit** in `backend/src/main/java/com/fantasykai/scoring/RulesetJson.java` is whitespace only (one added blank line mid-method, one at end of file) — harmless, but `CLAUDE.md` rules say never to discard it without asking.
6. **Ingest is thinner than it looks.** `NflverseClient.java:60` bounds only the response *headers* (`HttpRequest.timeout(3 min)`); the body read is unbounded until `fix/ingest-fetch-deadline` lands. No retries. The local mirror is **27 days old** (last pull 2026-10-14, complete only through Week 5; Week 9 finished 2026-11-09) because the laptop launchd job was uninstalled on purpose; production freshness was **not checked**.
7. **Landing hero is stale.** `heroData.ts:19` `HERO_CAPTURED = "2026-10-20"`; `session-check.sh` warns. Re-capturing needs `./scripts/ingest-once.sh` first, then `frontend/scripts/capture-landing.mjs`.
8. **Heat bands are a manual artefact.** `heatBands.ts` is generated from a loaded Postgres (window 2023–2025); no npm script or CI step regenerates or checks it, so it will not notice a new season.
9. **Stale and contradictory docs.** On `main`, `PRODUCT.md` still lists "league import (ESPN and Sleeper)" as not built while the README says Sleeper is built; the prod-deploy date conflicts (09-29 vs 10-08); `docs/CLAUDE-HANDOFF-2026-10-30.md` calls #75 a draft (merged 2026-10-30); the 2026-11-02 handoff says `main` is `72fbe50` (now `7721c2b`). The old checkout's `CLAUDE.md` (87 KB) differs entirely from `main`'s 772-byte stub.
10. **Public-history infrastructure leak.** `DEPLOY-STEPS.md` (deleted from `main` in `ac5c697`) still sits in git history and on `feat/projections-availability`, containing `ssh -i ~/.ssh/fantasykai_oracle ubuntu@<server IP>` (line 231 at `ac5c697^`). Not a credential, but a real host and key filename in a public repo. Rewriting history is the owner's call.
11. **Tooling nits:** `CrosswalkCoverageMeasurement` has an `@Test` but its name does not match Surefire's patterns, so `verify` never runs it; `session-check.sh` pins `EXPECT_TESTS` (stale vs 456/457).

### Debt inventory

| Item | Finding |
|---|---|
| TODO / FIXME / HACK / XXX | **0** on `main`. |
| Hardcoded secrets | None. The only matches are a test-only signing key (`backend/src/test/resources/application.properties:7`, self-labelled "not-a-secret"), a placeholder password in `deploy/acceptance.sh:37` for an `@fantasykai.invalid` account, and a form `autoComplete` attribute. Pattern scans for private keys / `AKIA` / `ghp_` / `sk-` / `xox*` found nothing. |
| Committed `.env` | **No.** `git ls-files` shows only `.env.example` and `deploy/.env.example`; no real `.env` was ever added on any ref. `.env` is gitignored. |
| Failing / skipped tests | **None**: no `@Disabled`, `@Ignore`, `assume*`, `.skip`, `.todo`, `.only` anywhere in `backend/src`, `frontend/tests`, `frontend/src`. |
| `npm audit` | **7 moderate, 0 high, 0 critical**, all in the dev toolchain (`minimatch` DoS via `eslint-config-next` → `typescript-eslint`). `npm audit --omit=dev` exits 0, so production dependencies are clean. `--audit-level=high` exits 0 today. |
| Backend CVE scan | **Not run** (needs `NVD_API_KEY`); see Broken #3. |
| Unused dependencies | `@tanstack/react-table` and `@tanstack/react-virtual` (`frontend/package.json:14-15`) are imported nowhere in `frontend/src`; Dependabot keeps bumping them. |
| Dead code (name-based, verify before deleting) | `components/landing/heroImage.ts` (documented placeholder, imported nowhere; its comment cites a `HeroDemo` that does not exist); exports `hasAccessToken` (`lib/api.ts`), `readBoardSequence` (`lib/boardSequence.ts`), `HERO_TOTAL` (`heroData.ts`). `BoardController` is backend-only on `main`. |
| Orphans | Fixtures `nflverse/roster_weekly_2024.csv`, `sleeper/matchups_{8,9,10}.json`; docs `docs/reviews/2026-10-29-league-prerequisites.md`, `2026-10-30-pr75-review.md` linked from nowhere. |
| Test-gap | No component/e2e coverage of the player overlay or full page; no `typecheck` script (the build type-checks). |

---

## Branch cleanup plan

**Nothing below has been run.** Run from `~/fantasy-kai-status` unless a path says otherwise, in this
order. Review every destructive line. Counts are from the classification run on 2026-11-10 against
`origin/main` = `7721c2b`.

### Classification (basis for the commands)

| Class | Branches |
|---|---|
| **On `main`** (ancestor, 0 ahead) | local `feat/leagues-s0`, `feat/leagues-s1`, `feat/leagues-s2`, `feat/leagues-s3`, `feat/projections-backtest`, `redesign/foundations`, `redesign/rankings`, `redesign/player`, `redesign/landing`; and 30 remote branches (`origin/redesign/player`, `origin/redesign/landing`, `origin/feat/leagues-s0`, `origin/feat/leagues-s3`, `origin/fix/*`, `origin/docs/{deploy-*,public-*,phase-5d-*}`, `origin/feat/{landing*,ki-visual-identity,members-only-api,player-workspace,security-headers,…}`, merged `origin/dependabot/*`). |
| **Merge-commit only** (ahead, but 0 unique patches and 0 file differences) | `origin/redesign/foundations`, `origin/redesign/rankings`, `origin/feat/leagues-s1`, `origin/feat/leagues-s2`. Safe to delete. |
| **Superseded** (0 unique patches; old studio/identity designs) | `feat/kai-identity`, `landing-studio-pre-rebase`. |
| **Superseded by a merged PR** | local `dependabot/npm_and_yarn/frontend/tooling-420a19dcf4` (remote gone; replaced by merged #83). |
| **Unmerged, has value — keep** | `fix/ingest-fetch-deadline`, `chore/ai-workflow` (+ `stash@{0}`), `codex/dense-rankings-2026-11-02` (PR #80), `feat/projections-availability`, `feat/projections-job`, `feat/projections-provider`, `feat/projections-v2`, `docs/projections-v1-architecture` (and their `origin/*`). Note `origin/feat/projections-backtest` is **not** fully merged even though the local branch is: it has 3 unique patches (the `v2-*` backtest docs), so do not delete it before Step 3 rescues them. |

### Step 1 — Preserve before touching anything

```bash
# 1a. Back up laptop-only files (nothing is deleted; copies only)
mkdir -p ~/fantasy-kai-backups/2026-11-10
cp ~/fantasy-kai-leagues/docs/CLAUDE-HANDOFF-2026-11-02.md \
   ~/fantasy-kai-leagues/docs/CODEX-PROMPT-2026-11-02.md \
   ~/fantasy-kai-leagues/docs/CLAUDE-HANDOFF-2026-10-30.md \
   ~/fantasy-kai-leagues/docs/research/2026-10-30-s4-espn-plan.md \
   ~/fantasy-kai-backups/2026-11-10/
cp -R ~/fantasy-kai/.claude/local ~/fantasy-kai-backups/2026-11-10/claude-local

# 1b. Turn the stash into a branch so it can't be dropped by accident (read-only on the stash)
git branch backup/stash-ai-workflow-attribution 'stash@{0}'

# 1c. Local archive tags on every unmerged tip you are about to stop tracking (no push)
for b in feat/projections-availability feat/projections-job feat/projections-provider \
         feat/projections-v2 docs/projections-v1-architecture feat/kai-identity \
         landing-studio-pre-rebase fix/ingest-fetch-deadline chore/ai-workflow; do
  git tag "archive/2026-11-10/${b//\//-}" "$b"
done
```

### Step 2 — Move local `main` (safe: 0 unique commits, checked out nowhere)

```bash
git rev-list --count origin/main..main     # must print 0
git branch -f main origin/main
```

### Step 3 — Land the keepers (separate PRs, each from `origin/main`; see CODEX START HERE)

Do not merge the stack branches themselves. In order: `chore/ci-all-bases`, then
`fix/ingest-fetch-deadline` (re-cut from `main`), then `codex/dense-rankings-2026-11-02` (rebased).
Rescue from `feat/projections-availability` with a path-limited checkout, not a merge:

```bash
git switch -c docs/projection-backtest-v2 origin/main
git checkout origin/feat/projections-availability -- \
  docs/perf/projection-backtest/v2-diagnostic-2025.md \
  docs/perf/projection-backtest/v2-prospective-2026.md \
  docs/perf/projection-backtest/v2-validate-2024.md \
  docs/reviews/2026-10-28-workspace-security.md
```

### Step 4 — Remove stale worktrees (after Step 1a; `git worktree remove` refuses if there are untracked files, so do not add `--force`)

```bash
git worktree remove ~/fantasy-kai-leagues     # fails until the 4 untracked docs are committed or backed up (1a)
git worktree list                              # ~/fantasy-kai-dense is clean; keep until #80 is rebased
# leave ~/fantasy-kai alone until the RulesetJson whitespace edit and .claude/local are dealt with
```

### Step 5 — Delete merged **local** branches (safe `-d`; it refuses anything unmerged)

```bash
for b in feat/leagues-s0 feat/leagues-s1 feat/leagues-s2 feat/leagues-s3 feat/projections-backtest \
         redesign/foundations redesign/rankings redesign/player redesign/landing; do
  git merge-base --is-ancestor "$b" origin/main && git branch -d "$b"
done
# superseded (archive tags from 1c keep them recoverable):
git branch -D feat/kai-identity landing-studio-pre-rebase dependabot/npm_and_yarn/frontend/tooling-420a19dcf4
```

### Step 6 — Delete merged **remote** branches (DESTRUCTIVE, outward-facing — owner approval first)

```bash
# Dry run: print what would go, review it, then remove the 'echo'
git fetch origin --prune
git branch -r --merged origin/main | grep -vE 'origin/(main|HEAD)$' | sed 's#^ *origin/##' \
  | while read -r b; do echo git push origin --delete "$b"; done
# Merge-commit-only branches (not caught by --merged):
echo git push origin --delete redesign/foundations redesign/rankings feat/leagues-s1 feat/leagues-s2
```

### Step 7 — Make the problem not recur (needs the owner; repo settings, not code)

```bash
# Auto-delete head branches on merge: GitHub then retargets stacked PRs to the default branch
gh api -X PATCH repos/balprab24/fantasy-kai -f delete_branch_on_merge=true
# Require the CI checks on main (check names must match ci.yml exactly)
gh api -X PUT repos/balprab24/fantasy-kai/branches/main/protection --input - <<'EOF'
{"required_status_checks":{"strict":true,"contexts":["Build, test, migrate","Lint and build the web shell"]},
 "enforce_admins":false,"required_pull_request_reviews":null,"restrictions":null}
EOF
# Optional: add the NVD key so the OWASP scan actually runs
gh secret set NVD_API_KEY
```

---

## CODEX START HERE

**You have no prior context. Read this section, then `CLAUDE.md`, `CONTRIBUTING.md` and `docs/development.md`.** Rules that apply to all three tasks (from `CONTRIBUTING.md` and `docs/architecture.md`): one branch per slice cut from `origin/main`; Conventional Commits (`type(scope): subject`); open **draft** PRs only; **never merge, never push to `main`**, never deploy; never print `.env`; never edit an applied migration (next free version is **V12**); backend needs **JDK 25 and Docker**; frontend needs Node **24.21.0** (`nvm use` in `frontend/`). Do not touch `~/fantasy-kai`'s uncommitted `RulesetJson.java` edit or `.claude/local/`. A gate you did not run must be reported as "not run".

### Task 1 (do this first) — `chore/ci-all-bases`: run CI on every PR, and stop the scan being silently green

**Why.** Stacked PRs (`feat/x → feat/y → main`) have been merged into their non-`main` bases three times; the work was then stranded. Those branches never got CI because `ci.yml` only triggers on PRs into `main`. Separately, `npm audit` is never run in CI.

**Branch:** `git fetch origin && git switch -c chore/ci-all-bases origin/main`

**Files to touch:** `.github/workflows/ci.yml` only (plus, optionally, one line in `docs/development.md`).

**Changes:**
1. Lines 3–9: under `pull_request:` delete `branches: [main]` so it runs for PRs against **any** base. Keep `push: branches: [main]` and the weekly `schedule`.
2. In the `frontend` job, after the `Build` step (line 68–70), add:
   ```yaml
         - name: Dependency audit
           working-directory: frontend
           run: npm audit --audit-level=high
   ```
   It exits 0 today (7 moderate, 0 high; all in the dev toolchain).
3. Line 104: change `::notice::` to `::warning::` so the skipped scan shows as a warning on the run summary. Do **not** remove `continue-on-error` and do not rename any job (`Build, test, migrate`, `Lint and build the web shell`, `Vulnerability scan`) — branch protection will reference those names.

**Acceptance criteria:**
- `git diff --stat origin/main` lists only `.github/workflows/ci.yml` (and optionally `docs/development.md`).
- A draft PR opened against a **non-`main` base** triggers all three jobs and they pass.
- The frontend job runs `npm audit --audit-level=high` and passes on the current tree.
- A `::warning::` annotation is visible on the Vulnerability scan job when `NVD_API_KEY` is absent.
- Job names are unchanged.

**Verify:**
```bash
git diff --stat origin/main
python3 -c 'import yaml; yaml.safe_load(open(".github/workflows/ci.yml")); print("yaml ok")'   # or: ruby -ryaml -e 'YAML.load_file(".github/workflows/ci.yml")'
(cd frontend && nvm use && npm ci && npm audit --audit-level=high; echo "audit exit=$?")        # expect 0
# prove it triggers on a non-main base:
git push -u origin chore/ci-all-bases
git push origin origin/main:refs/heads/tmp/ci-base-probe
gh pr create --draft --base tmp/ci-base-probe --head chore/ci-all-bases --title "chore(ci): run on PRs against any base" --body "Probe PR; do not merge."
gh pr checks --watch        # expect the 3 checks to appear and Build/Lint to pass
gh pr close                 # close the probe. Do NOT pass --delete-branch: that would delete chore/ci-all-bases (the PR's head)
git push origin --delete tmp/ci-base-probe
gh pr create --draft --base main --head chore/ci-all-bases --title "chore(ci): run on PRs against any base" --body "<what changed, gates run, probe evidence>"
```
`actionlint` is not installed on this machine; if you can install it, run `actionlint .github/workflows/ci.yml`. Backend/frontend gates: "not run — nothing here touches Java or the app".

### Task 2 — `fix/ingest-fetch-deadline-v2`: land the ingest fetch deadline

**Why.** `backend/src/main/java/com/fantasykai/ingest/NflverseClient.java:60` only bounds response headers (`HttpRequest.timeout(3 min)`); a stalled body read can hang the daily pull. Commit **`040a57e`** (on `fix/ingest-fetch-deadline`, 118 commits behind) already bounds the whole fetch and logs both clocks.

**Branch:** `git switch -c fix/ingest-fetch-deadline-v2 origin/main && git cherry-pick 040a57e`

**Files:** `backend/src/main/java/com/fantasykai/ingest/NflverseClient.java`, `IngestProperties.java`, `backend/src/main/resources/application.yml` (new `fetch-timeout`, default 3 min), new `backend/src/test/java/com/fantasykai/ingest/NflverseClientHttpTests.java` (8 tests), `docs/map.md`, `scripts/session-check.sh`. A dry-run shows **conflicts only in `docs/map.md` and `scripts/session-check.sh`** — keep `main`'s content and re-apply the commit's one-line intent in each.

**Acceptance:** the whole fetch (headers + body) is bounded by `fantasykai.ingest.fetch-timeout`; a log line carries both `monotonic_ms` and `wall_ms`; the 8 new tests pass; the full suite passes with no skips; `session-check.sh` shows no drift (update its pinned test-count baseline to the new real count — expect ≈ 464 executed, 465 `@Test` annotations; measure, don't trust this). Do **not** add retries — that is a separate task per the original commit message.

**Verify:**
```bash
cd backend && ./mvnw -B verify        # expect BUILD SUCCESS; report the real "Tests run: N, Failures: 0, Errors: 0, Skipped: 0"
cd .. && ./scripts/session-check.sh --no-network   # 0 drift (FAILs about .env / jar-by-mtime are normal on a fresh worktree)
```

### Task 3 — rebase and finish PR #80 (`codex/dense-rankings-2026-11-02`)

**Why.** The compact member board (draft PR #80) is the owner's design ask (a Flock-style dense board, ~181 px to first row) and is the only open PR. It is 16 commits behind `main` and conflicts.

**Branch:** work on a copy: `git switch -c codex/dense-rankings-rebased origin/codex/dense-rankings-2026-11-02 && git rebase origin/main`. **Ask the owner before force-pushing the original PR branch.**

**Files:** conflicts in `frontend/src/lib/leagues.ts`, `frontend/tests/leagues.test.ts`, `scripts/session-check.sh`; the PR also changes `DESIGN.md`, `frontend/src/components/RankingsBoard.tsx`, `components/rankings/{FilterBar,PlayerRow,RankingsWorkspace,TierHeader,BoardColumns}.tsx`, `components/shell/{AppShell,TopBar,Sidebar,nav}.ts*`, `lib/{board,boardParams,queries,types}.ts`, new `frontend/tests/member-board.test.ts`, `docs/API-DEPLOY-2026-11-02.md`, `docs/reviews/2026-11-02-dense-rankings.md`.

**Acceptance:** rebases cleanly onto `main`; `npm run lint && npm test && npm run build` pass (baseline today: lint 0, **104 tests** + the PR's new ones); the board still re-sorts when the ruleset changes. **Measure** the board at 1440×900, 1024×900 and 390×844 (first row ≤ 220 px, ≥ 16 complete rows, each ≤ 44 px, `scrollWidth ≤ innerWidth`) and fix the PR's contradictory review doc (`docs/reviews/2026-11-02-dense-rankings.md` lines 131, 151–154 vs 168–170). Keep the PR **draft** until the owner accepts narrow widths or waives them in writing — none is recorded.

**Verify:**
```bash
cd frontend && nvm use && npm ci && npm run lint && npm test && npm run build
./scripts/session-check.sh --no-network
```
Browser QA needs a throwaway `@example.test` account on localhost, deleted afterwards; never bypass authentication.

### Tasks to queue after these (owner decisions needed first)
- **Deploy the API** so My Teams works in production — owner go/no-go only (`deploy/README.md` §7, then `deploy/acceptance.sh`).
- **Rescue the stranded projections work** (Step 3 above) and rebuild the projection UI on the redesigned workspace (Phase 6 Slice 6).
- **Phase 11.5**, Spring Boot 3.5 → 4 — security obligation with a date on it.
