# Handoff: Projections Slice 5 (the API contract), 2026-11-23

Written at the end of the session that built sub-slices 5.2 to 5.7. It supersedes the
"Current state" and "next steps" rows of `docs/fantasy-platform-handoff.md` and
`docs/status-report.md` where those say the Slice 5 plan is "awaiting approval" or Slice 5 is
unbuilt. Those files are dated snapshots and were not edited.

## 1. State

| | |
|---|---|
| Branch | `feat/projections-slice5-contract`, **PR #101**. Its base on GitHub was the old `feat/projections-slice5-contract-tests` branch (PR #100, merged); it is retargeted to `main` when pushed |
| Built | 5.0 and 5.1 shipped as PR #100. **5.2 to 5.7 are PR #101**: resolver-derived week and states; the board envelope and `excluded`; one explanation line per bonus; the three-preset strip; `source.summary` and the measured error; docs |
| Not built (by design) | Slice 6 (the UI), Slice 7 (live accuracy), anything about switching the job on (section 4). The job is **off**; nothing was deployed; production was not touched |
| Backend gates at the last commit | `./mvnw -B verify`: 527 tests run, 0 failures (528 static `@Test`; `CrosswalkCoverageMeasurement` is one Surefire never runs). `session-check.sh`: 0 FAIL, 0 drift |
| Frontend gates | Run once on the whole slice (`npm ci`, lint, **113 tests**, build: 9 of 9 pages), all passing. The branch changes nothing under `frontend/` (`git diff --stat origin/main...HEAD -- frontend` is empty; use `origin/main`, the local `main` is 100+ commits stale) |
| Merge | Not merged. Merging to `main` and deploying are yours |

### What was built, in one place
`docs/projections-v1-architecture.md` section 9, "As built", is the contract. The plan
(`docs/projections-slice-5-plan.md`) says what each sub-slice was and where it departed from the plan.

### How it was checked
Every assertion in the plan's section 6.1 table was **broken on purpose and its test failed**: 80
deliberate mutations over the slice, **80 caught, 0 survived**, source files restored byte-for-byte
after each (a harness applied one break, ran the tests, restored, and compared hashes). The commit
bodies name them. Per run: 5.3 18, its review fix 7; 5.4 10, fix 3; 5.5 7, fix 6; 5.6 10, fix 5;
the plan's 6.1 rows 1, 2, 4, 5, 6 and 8 in one consolidated run 7; the whole-slice review fixes 7.
The harness lived in a scratch directory and is not in the repo; the list above is the durable record.

Reviews: every sub-slice had a fresh-context review on its commit, and each found something real
that was fixed in a following commit (the history rule for "no history"; `times()`; four presets vs
three; the population of the error figure). The whole slice then had three reviews (invariants,
security, docs drift). **Two of those three ran on Sonnet, not on the reviewer agent's Opus**: Opus hit
its usage cap mid-review (resets 15:30 CT). The security review (Opus) finished first.

## 2. Branches, worktrees and the stash

- **Work was done in a worktree: `/Users/prabhnoorbal/fantasy-kai-s5`.** Not in the main checkout,
  which is on `feat/projections-availability` with uncommitted `.claude/` changes and was never touched.
  Keep the worktree until the PR merges. Worktrees under a session's scratchpad are wiped by routine
  `/tmp` cleanup (that hollowed out the last session's two); this one is a sibling of the repo.
- **In a worktree, run `session-check.sh` with `COMPOSE_PROJECT_NAME=fantasy-kai`.** Compose derives its
  project name from the directory, so without it the database rows print `?` (not a false `ok`; the
  script is right to). The worktree also needed a copy of the gitignored `.env`.
- **The stash on `slice5-contract-tests` is redundant and safe to drop:** `stash@{0}`
  (`10aa1da5fd65b357770469841f63db4c43b63adc`, "WIP on feat/projections-slice5-contract-tests: 183fbf7").
  All four files in it are byte-identical to `origin/main` (checked with `git diff` per file). I did not
  drop it: `git stash drop 'stash@{0}'` when you agree.
- Pruned: the two dead worktrees (`wt-s5`, `wt-s5b`; their directories were empty shells, 0 files).
  **Not touched:** `fantasy-kai-ci`, `-dense`, `-dense-rebased`, `-ingest-v2`, `-new-ruleset`
  (live directories; their branches are not ancestors of `origin/main`, though some PRs merged by squash).
- `chore/agents` is merged (PR #99). I merged `origin/main` **into** the slice branch instead of
  rebasing, so no force-push was needed on a pushed, PR-backed branch.

## 3. Open decisions (the plan does not make these)

1. **D3, which population "about 5.5" describes.** The figure is over the research's P (a fixed
   top-224-by-opportunity group who played). Over every player the board ranks the same baseline reads
   about **4.5**; counting players who then sat as zeros, **5.8 to 5.9**; and it is a *PPR* figure (4.579
   under 0 PPR, 5.003 under Half in 2024). The API now says which P it is. Decide what the popover leads
   with before Slice 6 prints it, so Slice 7's live accuracy and the popover do not disagree. (Plan section
   3 "Follow-ups"; arch section 15.)
2. **D2 as built is three presets** (Standard, Half, Full PPR); TE Premium is not on the strip.
   Plan D2 says three; I first read "each seeded profile" as four and a review caught it. One name to change
   (`ProjectionsService.STRIP_PRESETS`) if you want four.
3. **A hole in "no unexplained disappearance".** `excluded` has no bucket for a rostered player who has
   history and no line (the job has not run since a roster change, or his game kicked off before its first
   run). He is in no count; his page says `UNAVAILABLE`. **In the first week after switch-on this is everyone
   on a game that kicked off before the first run.** Add a fourth count (`notProjected`?) or accept.
4. **`availabilityAsOf` publishes ingest timing** the health endpoint deliberately hides
   (`show-details: never`), and it is the time a pull ran, not the week it covered: on a Tuesday after a
   successful pull, every player can read `UNKNOWN` under a recent time. Designed (arch section 9), but yours to accept.
5. **A STALE board's `week` is the target's, not the rows'.** Each row says its own; an empty filtered
   page cannot. A `rowsWeek` field is one line if Slice 6 wants it.
6. **Slice 6 contract gaps vs arch section 11:** the stored volume (`pass_att`, `rush_att`, `targets`) is
   not served, and the "6.1 targets" line needs it. Bonus lines prune exact zeros only: a decayed average
   leaves a small rushing value on every receiver who ever carried once, so the client may hide trivial ones.

## 4. D4: the earliest date the projection job can be switched on

**Earliest realistic first run: Tue 2026-12-01 06:00 ET (week 13).** Last clean slot for week 13: Thu
12-03 06:00 ET (TNF kicks off 20:15 ET). The week-12 slot (last pull before its first game: **Wed
11-25 06:00 ET**, GB-LA kicks off Wed 20:00 ET, then Thanksgiving) is theoretically open but needs all of
the list below finished and verified in about 39 hours, with production writes and a deploy in between.

How the dates come out (from the local mirror's schedule, refreshed 2026-11-17; production reads the
same nflverse source, and a game can move): the job runs inside the 06:00 ET daily pull, writes rows
**only for games that have not kicked off**, and locks them at kickoff. The target week flips when
every game of the old week has stat rows (normally the Tuesday pull after Monday night). Week 11's
last game is Mon 11-23 20:15 ET; week 12 runs Wed 11-25 to Mon 11-30 20:15 ET; week 13 starts
Thu 12-03 20:15 ET. A run that starts after a game kicked off leaves that game's players with no
projection for the week (R5; and hole 3 above).

**What is blocking, by the plan's own row (section 7), with how I know:**

| | Status | Evidence |
|---|---|---|
| R2 production appearance backfill, 2020 to 2025 | **Owed.** Production holds 2026 only (3,219 rows) as of 2026-11-17; I did not re-check production | plan section 7; a production write is yours (D5(a): laptop, SSH tunnel, existing `BackfillRunner`, which never exits the JVM: stop it after "backfill finished") |
| R2a the +2 to +20 zero-stat gap against research | **Unexplained**, first task of R2 | plan section 7 |
| R3 rehearsal on a restored production copy | **Not done.** Also time the resolver (105 ms warm locally) and the history read (`withoutHistory`, one index probe per rowless candidate) on production-sized data | plan section 7; security review |
| R4 a way to set the flag in production | **Absent.** No `PROJECTIONS_JOB_ENABLED` in `deploy/compose.prod.yml` or `.env.example` (grepped today): needs the line, `deploy/.env`, and a backend redeploy per `deploy/README.md` section 7 | grep |
| R1 Slice 5 merged and deployed | PR #101 open, not merged, not deployed. D4(a) lets the plan relax it to "before Slice 6", which I read as not binding for switch-on; if you read it as binding, it is on the path | plan R1 |
| R8 your sign-off on the first board | after R3's top 20 | plan section 7 |
| Re-run the parity checks on `main` immediately before | `scripts/backtest.sh parity` and `ProviderParityTests`. **The command behind the "14,906-week" input parity of plan section 6.2 was never confirmed** (5.0's task, still open) | plan section 10 |

Nothing in the code blocks it: `DailyPull` runs the job when the flag is on and records it as
`ingest_runs.source = 'fantasykai.projections'`, so freshness is already watched (R6). The first stored
row fixes the meaning of `weighted-average-v1` for good (any change to weighting, window, eligibility
or availability semantics must be a new id).

## 5. Not fixed, and why

- **`CurrentTargetWeek` after a long database stall** can re-run the ~100 ms resolver once per queued
  request (a waiter whose clock was read before the lock looks "old"). Needs a choice between two
  behaviours with different failure modes (refuse to overwrite a newer entry vs trust a backward clock
  jump). Bounded, and only after a stall of a minute or more. Security review, Low.
- **Existing issues the 5.4 review surfaced, not in this change:** `RulesetJson` reads a bonus threshold
  with `asInt()` (a stored `gte` of 99.5 pays at 99, and the explanation now shows 99), and
  `ResolvedRuleset`'s hash sorts bonuses while the engine's sum follows the authored order (last-bit
  differences, and a cache that shares by hash). Neither touches this slice.
- **A broken system preset would 500 player pages** (`presets()` compiles three seeded rows per request).
  They are migration-seeded and covered by `ScoringProfileTests`' canonical-hash assertion; degrading it would hide a corrupt row.
- **`docs/map.md` per-package counts** are wrong on `main` (`ingest/` says 19, 24 on disk; `auth/`,
  `leagues/` likewise) and there is no `projections/` section. I fixed `scoring/` (my row made it wrong);
  the rest is a separate pass. `session-check.sh` checks totals only.
- **`CLAUDE.md` in the main checkout** is an older monolithic copy with stale counts; the worktree's is
  a short pointer. Not edited.

## 6. How the work was run, and what differed from the brief

- "Teammates": this session had no agent-team mode (that needs `scripts/team.sh`), so the three
  whole-slice reviewers were parallel read-only subagents.
- `/review-pass` diffs `ref...HEAD`, which means **committed** work: each sub-slice was gated, committed,
  then reviewed, and the findings went in a following commit (matching the repo's earlier history),
  not reviewed before the first commit.
- One test (`ProjectionSourceTests`) reads `../docs/perf/...`, so it assumes the working directory is
  `backend/` (Surefire and CI both do). A missing report fails the test, it is not skipped.

## 7. Next step

1. Review PR #101 (retargeted to `main`) and decide the open items above, D3 first if Slice 6 is next.
2. Merge, and deploy the backend when you want the endpoints live (they are members-only and nothing calls them).
3. Either Slice 6 (the UI), or the switch-on work in section 4 (R2a, R2, R3, R4, R8), in that order.
