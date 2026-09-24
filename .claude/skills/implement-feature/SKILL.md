---
name: implement-feature
description: Implement an approved plan in small, verified increments. Stops and asks on any unplanned decision.
argument-hint: "<path to the approved plan file>"
disable-model-invocation: true
---

# Implement: $ARGUMENTS

Read the plan at the path above, in full. If no plan was given or it does not exist, stop and suggest
`/explore-feature`.

## Before writing code

1. **Restate the scope in three lines**, and what is explicitly out of scope. Do not add to it.
2. Branch from an up-to-date `main`:
   `git switch main && git pull --ff-only && git switch -c <type>/<name>`.
3. **Reproduce first.** For a bug, execute it and paste the output before touching anything. For a
   feature, capture the current behaviour the change will alter.

## The loop — one increment at a time

- **Write the test first and run it. It must fail, for the reason the plan says.** A test that passes
  before the fix is testing something else — fix the test before touching the code.
- Watch for state shared between tests (a Redis bucket, a database row, a cache) that can make a test
  pass for the wrong reason.
- Make the smallest change that turns it green. Re-run it. Paste the result.
- When a harness has state (containers, a mounted config, a built image), **print a guard line
  showing what is actually running** before trusting a result. Wrap commands in shell functions,
  never string variables — zsh does not word-split them.
- When there are layers of defence, prove each one alone: remove it and watch its test fail.
- When you parse a tool's output, print the tool's own count beside what you parsed. A mismatch
  means the parser is wrong, not the code.

## Stop and ask — do not decide alone

Stop and lay out the options, the tradeoffs and a recommendation before any decision the plan did
not cover about:
- schema or migrations
- authentication or security
- a new dependency
- an external service
- cost
- operational complexity

Also stop if the work turns out materially larger than planned.

## Finish

1. Run the gates and paste what they printed:
   - `./scripts/session-check.sh` — fix any `drift` row, usually a count in `docs/map.md` §1 and the
     matching `EXPECT_*` in the script
   - `cd backend && ./mvnw -B verify`
   - `cd frontend && npm run lint && npm run build`, if the frontend changed

   A gate that was not run is reported as **not run**, with the reason.
2. Update every doc the change made false: acceptance checks, `docs/traps.md` and the matching
   `.claude/rules/` line, code comments. If a durable decision was made, add it to
   `docs/decisions.md`.
3. Commit locally on the branch. **Never push, merge or deploy without asking.**
4. Report:
   - what changed (`git diff --stat`)
   - what was verified, and how
   - what was **not** done

   Then suggest `/review-pass`.
