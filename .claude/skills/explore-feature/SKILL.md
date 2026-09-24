---
name: explore-feature
description: Investigate a feature or bug against the existing code and produce a decision-ready plan. Read-only — no edits.
argument-hint: "<feature or problem, in a sentence>"
disable-model-invocation: true
---

# Explore: $ARGUMENTS

You are investigating, not implementing. **Make no edits** except to the plan file. If plan mode is
not on, say so and suggest the owner turn it on (shift+tab) before continuing.

## 1. Orient cheaply

- Read `docs/map.md` §1–§4 first; it says where things are. Do not read the whole repository.
- Check `docs/decisions.md` and `docs/north-star.md` §9 ("explicitly not building"). If the request
  collides with a recorded decision or a non-goal, say so before going further.

## 2. Find and trace the code

- For broad searches ("where is X handled"), use `Explore` subagents, in parallel only when the
  questions are genuinely independent. **Explore skips CLAUDE.md**, so paste the invariants that
  matter for the question into its prompt. Use a stronger model only where judgment is needed
  (security, architecture); inventory work does not need one.
- Read the files that matter yourself. Trace the path the feature touches end to end with
  `file:line`: UI → API → service → SQL or external source, and back.
- **Measure, don't assume.** If behaviour is in question, reproduce it — a request, a query, a test —
  and paste the output. A bug is not confirmed until it has been executed. Check subagent claims
  that you intend to rely on.

## 3. Report to the owner

1. **What exists today**, with `file:line`. Separate what is *implemented*, what is *planned* (in
   north-star), and what is *your suggestion*.
2. **Invariants this touches** — walk the CLAUDE.md table as yes/no questions.
3. **Decisions to make.** For each one: the options, the tradeoffs, your recommendation, and *when it
   would be worth revisiting*. Architecture, security, schema, new dependencies, external services,
   cost and operational complexity always get this treatment; trivial details do not.
4. **Risks and unknowns** — what you could not verify, and how it could be verified. Phrase untested
   properties as predictions, not facts.
5. **Targeted questions**, only where the requirements are genuinely unclear. Use AskUserQuestion,
   and don't ask what the code or a sensible default can answer.

## 4. Write the plan

After the owner decides, write a scoped plan covering:
- the context and the chosen approach
- the files to touch
- the increments, each with its own check
- the tests, including which ones must **fail before** the change
- the verification commands

Scale it to the task. End with ExitPlanMode. Implementation happens in a fresh session with
`/implement-feature <plan path>`.
