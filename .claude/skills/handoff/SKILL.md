---
name: handoff
description: Write a concise handoff so a fresh session can resume this task without the conversation history.
disable-model-invocation: true
---

# Handoff

Write `.claude/local/handoff.md` (gitignored), replacing any previous one. A fresh session must be
able to resume from it alone. **Facts come from commands, not memory**, so run these first:

```bash
git branch --show-current && git status --short && git log --oneline main..HEAD && git diff --stat main
./scripts/session-check.sh --no-network
```

Use exactly this first line. `session-check.sh` parses it to warn when a handoff belongs to a
different branch.

```markdown
# Handoff — <YYYY-MM-DD HH:MM> · branch <name>

## Objective
One or two sentences. Link the plan file.

## Done
- … (with commit hashes)

## Decisions made
- … — durable ones must also be in docs/decisions.md; say whether they are

## Files changed
<from git diff --stat>

## Verification
The results, pasted. Any gate that was not run is written as **not run**, with the reason.

## Known problems / open questions
- …

## Next action
The single next step, concrete enough to start without re-reading anything else.
```

Then tell the owner to start a fresh session (`/clear`) and begin with: "Read
`.claude/local/handoff.md` and continue."
