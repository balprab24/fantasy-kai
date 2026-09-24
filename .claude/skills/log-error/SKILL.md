---
name: log-error
description: Record an AI-assisted development failure — what went wrong, the evidence, the real root cause, and how to prevent it. About two minutes.
argument-hint: "[one-line summary of what went wrong]"
disable-model-invocation: true
---

# Log an error: $ARGUMENTS

Append one entry to `.claude/local/ai-workflow-log.md`. If the file is missing, create it with a
`# AI workflow log` header. The directory is gitignored: this log is private, so it can be candid.

Keep the entry short. Fill it from the conversation, and ask the owner only for what you cannot see.

```markdown
## YYYY-MM-DD · ERROR · <one-line summary>
- **Prompt / request:** what was asked, quoted or closely paraphrased
- **Context given:** the files, docs, plan and skills in play
- **Expected:** …
- **Actual:** …
- **Evidence:** command output, a test result, a diff, file:line — not an impression
- **Root cause:** the honest one. Category: prompting · missing context · model behaviour ·
  tooling · unclear requirements · engineering design
- **Correction:** what fixed it
- **Prevention:** a reusable change — a rule, a test, a hook, a skill edit, a doc line
```

Do not assume the owner's prompt was the cause. Model errors, tooling and design are just as likely.

If the prevention is a rule for future sessions, propose the exact line and where it goes (a
`.claude/rules/` file, a skill, or CLAUDE.md). Do not edit those files without approval.
