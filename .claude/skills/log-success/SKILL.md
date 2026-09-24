---
name: log-success
description: Record an unusually effective AI-assisted workflow so it can be repeated. About two minutes.
argument-hint: "[one-line summary of what worked]"
disable-model-invocation: true
---

# Log a success: $ARGUMENTS

Append one entry to `.claude/local/ai-workflow-log.md`. If the file is missing, create it with a
`# AI workflow log` header; the directory is gitignored. Fill the entry from the conversation and
keep it short.

```markdown
## YYYY-MM-DD · SUCCESS · <one-line summary>
- **What worked:** the specific move, not "it went well"
- **Context provided:** the plan, docs, skills or prompts that mattered
- **Tools used:** subagents (which ones, on which model), browser, local stack, tests
- **What was verified, and how:** the evidence
- **Cost:** rough session length, tokens or wall time, if known
- **Repeat?** yes / only when … / no — and what would make it a skill or a rule
```

If it should become standard practice, propose the exact change — a skill edit or a rule line — but
do not make it without approval.
