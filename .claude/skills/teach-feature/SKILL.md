---
name: teach-feature
description: Explain a finished feature to its owner — flow, files, decisions, alternatives, transferable concepts — then quiz them without revealing answers.
argument-hint: "[plan path or git range; default main...HEAD]"
disable-model-invocation: true
---

# Teach: $ARGUMENTS

**Target:** the plan or git range given above; if none was given, `main...HEAD`.

The owner must be able to explain this feature in an interview without notes. Work from the diff,
the plan and `docs/decisions.md`, not from memory of the build conversation — this may be a fresh
session.

## 1. Explain (concise — no tutorial on trivial functions)

1. **What we built** — two sentences.
2. **How it works** — trace one request or run end to end with `file:line`.
3. **Why this design** — each significant decision, the alternatives that were rejected and why, and
   what would make the other choice right. Link the `docs/decisions.md` entries.
4. **What could go wrong** — concrete failure scenarios, and which of them are still open.
5. **How we know it works** — the tests and executions that proved it, including what they cannot
   see.
6. **The transferable concepts** — the 2–4 ideas that apply to any full-stack project.

## 2. Quiz

Ask **4–6 questions**, mixing:
- tracing ("what happens when…")
- design ("why not…")
- failure ("how would you break…")
- transfer ("where else does this apply")

**Do not reveal or hint at the answers.** Stop and wait for the owner to reply.

## 3. After the owner answers

- Grade each answer honestly — correct, partly, or missed — and explain the missing piece.
- Append an entry to `docs/learning-log.md`:
  - the date and the feature
  - the six questions, answered briefly, with "What did I learn" in the owner's words
  - the quiz questions and their grades

  Then update the concept tracker at the top of that file.
- Name the one concept most worth revisiting, and where in this codebase to study it.
