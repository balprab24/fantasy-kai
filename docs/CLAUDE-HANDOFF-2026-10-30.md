# Claude Code handoff — league build, 2026-10-30

## Start here

Continue in **`~/fantasy-kai-leagues`**, branch **`feat/leagues-s0`**.
Do not implement in the older `~/fantasy-kai` checkout.

- Current implementation commit: `eaae565`.
- Draft PR: https://github.com/balprab24/fantasy-kai/pull/75
- PR is OPEN and DRAFT. It is **CONFLICTING** as of this handoff.
- Latest fetched main: `ea6745f`, which merged dependency PR #73 after this branch
  started from `ac2aa8c`.
- A non-mutating `git merge-tree --write-tree HEAD origin/main` confirmed conflicts
  only in `frontend/package.json` and `frontend/package-lock.json`.
- GitHub checks for the current PR head all succeeded: backend build/test/migrate,
  frontend lint/build, vulnerability scan, Vercel, preview comments. These are not
  validation of a future conflict resolution.
- Worktree was clean before this handoff file was added. This handoff is deliberately
  uncommitted and is copied into both worktrees for discoverability.

The user requested the full researched league/trade plan, then approved fixing its
missing prerequisites while preserving main's redesigned frontend. S0 code is built;
review and browser QA are still incomplete. **S1–S6 are not built.** Do not call the
whole plan complete, and do not silently merge or deploy the draft PR.

## Authoritative instructions and decisions

Read `CLAUDE.md`, `CONTRIBUTING.md`, `docs/architecture.md`, `docs/development.md`,
`docs/north-star.md`, `DESIGN.md`, and
`docs/research/2026-10-28-leagues-trades-plan.md` in the league worktree.

The user explicitly confirmed all six decisions:
1. Sleeper first, then ESPN public/private, then Yahoo.
2. Private ESPN cookies stored encrypted only with opt-in; use-once/discard remains.
3. Separate league copy per member; no sharing across members.
4. Expected-win change is the headline, playoff-probability change beside it.
5. **If calibration fails, hold Trades. No public beta fallback.**
6. Availability is our players minus league rosters; waiver status is not distinguished.

The research plan records these confirmations, the prerequisite integration, and
S0 status. S0 consumes V10; **S1 must use V11**, not the original V10 numbering.
Some older north-star text still says ESPN first: reconcile that when S1 lands.
No budgeted/automatic goal was created. No subagents were used.

## Why this PR is larger than S0

The old `feat/projections-availability` branch had continued after PR #61 merged.
Main lacked the later projection implementation and V7–V9 but had a newer frontend
redesign. The owner chose to preserve that redesign and integrate the backend first.

Applied the backend delta from common ancestor `e2c3b05` through `7665721` using a
three-way patch. Kept main's newer backend dependency versions and documentation
refactor. Imported the corresponding backtest script, projection architecture and
accuracy report. Did not replace frontend source with the old member-board UI.

Integrated prerequisites include weighted-average projections, raw persistence and
kickoff locks, schedule reconciliation, weekly roster/injury availability, shared
daily ingest, board/performance and projection read APIs, member Redis limits and
bounded scoring-profile cache. The research harness includes v2/parity/availability.

Frontend source still equals the main baseline `ac2aa8c`. Only dependency manifests
changed: Next/eslint-config-next patched to 16.4.3 and npm audit fixes applied. Main's
subsequent dependency update is the reason for the current merge conflict.

Projection generation remains **disabled**. The redesigned frontend does not expose
all of the old branch's projection-board UI. Do not claim frontend feature parity.

## S0 implementation

- `ingest/PlayerMatcher`: stored platform ID → GSIS → ESPN → normalized name and
  position (birth date breaks ties) → name and birth date across positions → explicit
  override. Indexed lookup maps avoid repeated whole-player scans. Ambiguous stored
  IDs and ambiguous names are refused.
- `SleeperCrosswalk`: one full player snapshot per daily ingest, 32 MiB body cap;
  transactional attachment of Sleeper/Yahoo IDs. Preserves other IDs, refuses to
  overwrite conflicting Sleeper IDs, avoids Yahoo IDs owned by another player,
  and skips multiple provider records resolving to the same internal player.
- Four portable GSIS-based overrides seed `player_id_overrides` without replacing
  existing entries: Mitchell/Mitch Tinsley, Bam/Zonovan Knight, Andrew/Drew Ogletree,
  Matthew/Matt Hibner. Their positions and birth dates agree in the inspected data.
- V10 creates the audited override table (including non-null keys and reason) and
  an expression index on `players.external_ids->>'espn'`. Existing migrations and
  `player_game_stats` indexes are untouched.
- `ProjectionController`: raw total used for sorting before paging; rounded public
  points unchanged. Internal `rawPoints` is excluded from JSON with `@JsonIgnore`.
- Regression tests include stored-ID fallback/ambiguity, normalization, overrides,
  crosswalk persistence/idempotence/conflicts, and two projections 0.004 apart that
  display the same points but must sort correctly across page boundaries.

Birth date intentionally does not veto an otherwise unique name+position match.
Sources disagree on some birth dates; the approved plan uses dates to break ties.
An initial stricter implementation failed coverage (481/496); it was corrected to
match that policy. Do not describe unique name matching as proof of identity.

## Evidence and limitations

Read `docs/reviews/2026-10-29-leagues-s0.md` and
`docs/reviews/2026-10-29-league-prerequisites.md`. The latter is an earlier integration
checkpoint; its statement that matcher wiring/sort remain unfinished is superseded
by the S0 review and this handoff.

Final evidence:
- `./mvnw -B verify`: **373 tests**, zero failures/errors/skips, BUILD SUCCESS.
- Frontend integration checks: lint, **90 tests**, and production build passed.
  No frontend source changed during S0, so these were not repeatedly rerun.
- npm audit after dependency fixes: zero vulnerabilities.
- Actual crosswalk coverage in disposable Postgres: **496/496 (100%)** for local
  2026 QB/RB/WR/TE players with a stat row, through week 5. All 25,069 exported player
  records were loaded so matching saw the complete local master, not just targets.
- Final attachment measurement: 550 ms in that test environment (not a production SLA).
- Snapshot hashes and reproduction method are in the S0 review. Initial 98% gate
  was not weakened. The last missing player was the verified Hibner name variant.
- Mutation: changing raw sort back to rounded fails ProjectionOrderingTests.
- Mutation: accepting first duplicate stored ID initially survived; added the
  missing regression, then confirmed the mutation fails. Both mutations restored.
- Local V10 migration, application startup and liveness passed with jobs disabled.
- Final session check: 0 FAIL, 0 drift, 0 unknown; warnings for stale mirror/hero
  and repository state. Counter baseline is 374 test methods because it includes
  the explicitly selected coverage measurement, which normal surefire does not run.
- **Browser QA has not been run for this integration/S0.** No comprehensive review
  of every imported backend line was completed. Draft status is intentional.

## Local state and artifacts

The original checkout remains on `feat/projections-availability` with preexisting
RulesetJson.java whitespace edits, `.claude/local/`, and untracked research docs.
Do not overwrite, clean, stash or attribute those edits to S0.

League worktree `.env` is a gitignored symlink to the original checkout's `.env`.
Never print or commit it. The shared local database on localhost:5433 now has **V10**
applied; V1–V10 checksums matched. No production schema/data was changed. No full
live ingest was run and the local master was not populated with newly matched IDs;
coverage writes happened in disposable test Postgres only.

Use `COMPOSE_PROJECT_NAME=fantasy-kai ./scripts/session-check.sh` in this worktree to
query the existing containers. Without that setting, the worktree's default Compose
project differs and the check reports DB unknowns even though containers are up.
Do not start a second Compose project or reset shared volumes.

A temporary API on port 18081 was started for migration/liveness and then stopped.
The original user's background API/frontend processes were not stopped or replaced.
Check listeners before starting anything. Generation, scheduled ingest and startup
backfill must stay disabled for QA.

Local scratch artifacts (not committed):
- `/tmp/fk-s0-sleeper.json`, `/tmp/fk-s0-players.json` — measured snapshots.
- `/tmp/fk-s0-coverage.log`, `/tmp/fk-s0-final-verify.log`.
- `/tmp/fk-s0-mutation-ProjectionOrderingTests.log` and
  `/tmp/fk-s0-mutation-PlayerMatcherTests.log` — expected mutation failures.
- `/tmp/fk-s0-session.log`, `/tmp/fk-s0-local-startup.log`.
- `/tmp/fantasy-kai-integration-{lint,frontend-tests,build,upgrade}.log`.
Scratch files may disappear; durable results/hashes are in the review docs.

## Next actions

1. Resolve PR #75's two dependency-file conflicts against fetched main. Preserve
   main's intended dependency updates and S0's security fixes; regenerate the lock
   file through npm rather than hand-editing integrity fields. Rerun audit, frontend
   tests/lint/build and the applicable backend/session gates after resolution.
2. Review the full PR against invariants and perform outstanding browser QA, including
   member access with the imported Redis limiter. Resolve findings; keep actual
   checks distinct from checks not run. This is a large prerequisite integration.
3. Keep PR #75 draft until its remaining gates/review are complete. Follow the user's
   merge/release policy; no production deployment was authorized or performed here.
4. Continue S1 from the accepted baseline: V11 league storage, ownership-filtered
   APIs, Sleeper provider/scoring mapper, bounded outbound client/shared rate budget,
   and My Teams using main's existing design system. Follow all detailed contracts
   and tests in Part B of the research plan. Do not invent a replacement architecture.
5. **User input still missing:** an asynchronous question requested the user's Sleeper
   username or league ID for real-league verification and a redacted fixture. No answer
   arrived. Username/ID is sufficient; never ask for a Sleeper password. Future-week
   matchups must be verified rather than assumed. Synthetic/unit work can continue
   before this input, but do not claim the real-league acceptance gate passed.
6. S2–S6 remain: ROS/optimizer/team needs; calibrated trades/playoffs; ESPN; Yahoo;
   league rankings/waivers. Yahoo needs the owner's developer app later. Preserve
   the fixed calibration gate and hold Trades on failure.
