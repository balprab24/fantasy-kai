# Contributing

Fantasy Kai is a solo project, but it is written to be read and reviewed. This file covers how work
is checked before it merges; setup is in the [README](README.md#running-locally).

## Where the docs live

| Doc | Owns |
|---|---|
| [`README.md`](README.md) | What ships today, and local setup |
| [`docs/architecture.md`](docs/architecture.md) | The core design, the invariants, how scoring and the read API fit together, measured numbers |
| [`docs/development.md`](docs/development.md) | Commands, the session check, known platform risks, and the traps that have already cost time |
| [`docs/north-star.md`](docs/north-star.md) | Product scope, sequencing and the roadmap. The source of truth for product decisions |
| [`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md) | Engineering rationale: schema, scoring, security, performance. Its §1 and §11 are superseded by the north star |
| [`docs/map.md`](docs/map.md) | Every package's classes, the two pipelines, known gaps |
| [`docs/orientation.md`](docs/orientation.md) | A plain-English tour and glossary |
| [`docs/deployment.md`](docs/deployment.md) · [`deploy/README.md`](deploy/README.md) | The production write-up, and the runbook |

When two docs disagree, the one that owns the fact wins and the other gets fixed. When
`./scripts/session-check.sh` disagrees with a doc, the check wins.

## Working agreement

**Measure before asserting. Prove a constraint by trying to violate it.**

Every design choice here should be defensible line by line, so any type, constraint or number that was picked by default becomes something its owner has to justify. Before a migration, pull the real values rather than reasoning about what the type should be — that habit has already caught the fractional sacks and the NULL-defeated unique constraint. Before quoting a number, run the query. Fixing a 200-line schema is free; fixing it under 112K rows is not.

Report design gaps you are *not* fixing explicitly rather than staying quiet about them.

## Definition of done

Work is not finished when it works. It is finished when someone has tried to break it and
written down what they tried. Every change ends with the review pass below. In Claude Code,
`/review-pass` runs the same checklist.

## Review pass

Review `git diff main...HEAD` (or the branch you are merging into) as a senior engineer who **did not write it** and has no
stake in it shipping. The author is not in the room and does not need protecting. Your job is to
find what is wrong, and — where nothing is wrong — to say precisely what you looked for.

**Order matters: read the evidence before you read the intent.** Start with the diff, in full,
before re-reading any plan, commit message or doc that explains it. Reading the reasoning first
is how a reviewer ends up confirming it instead of testing it.

### 1. Read the diff

Every hunk, including tests, docs, scripts and config. Not a summary of the diff — the diff.

### 2. Run the gates, and paste what they actually printed

```bash
./scripts/session-check.sh                            # ~3s
(cd backend && ./mvnw -B verify)                    # JDK 25, Docker running
(cd frontend && npm run lint && npm test && npm run build)
```

A gate you did not run is reported as **not run**. Never as passing, never omitted. If a gate is
irrelevant to this diff, say which and why — "nothing here touches Java" is a defensible reason;
silence is not.

### 3. Walk the invariants as yes/no questions

Straight from the invariant table in [`docs/architecture.md`](docs/architecture.md). Answer each against the diff, not from memory:

- Does anything persist a computed point value (`fantasy_points`, a projected total, a blended
  rank)?
- Does anything add an index to `player_game_stats`?
- Is a scorable stat named anywhere but `StatKey`? A blendable signal anywhere but `SignalKey`?
- Does anything round before the API boundary, or `ScoringEngine` round at all?
- Does any SQL get built by concatenation — including a sort or filter?
- Does a ranking score summed stats instead of summing scored games?
- Did `ddl-auto` move off `validate`? Did an applied migration get edited?
- Does a ranked number travel as a bare `double` instead of carrying its parts?
- Does any external signal become mandatory to compute a ranking?

### 4. Hunt the failure classes this codebase has actually produced

Not generic code smells — the specific traps in [`docs/development.md`](docs/development.md), each of which cost real time here:

- **A default that hides an absence.** `CsvValues.shortValue` maps a missing column to 0. Does
  anything new treat "could not read it" and "read it, it was zero/fine" as the same value?
- **Trusting an exit code over output.** A command can exit 0 and still have done the wrong thing:
  `java_home -v 25` returns an older JDK, and exits 0, when 25 is not installed.
- **Trusting a timestamp over content.** `find -newer` is mtime; a git checkout rewrites mtimes.
- **NULL defeating a UNIQUE constraint.** Postgres treats NULLs as distinct.
- **A cache consulted before the ownership filter**, so a warm entry becomes a bypass.
- **`@Transactional` rolling back the write you made before you threw.**
- **Argument evaluation order vs `ResultSet.wasNull()`** — it reports on the last column *read*.
- **Binary rounding standing in for decimal rounding.**
- **A bean that only exists in a servlet context**, breaking the headless entrypoint while all
  tests — which all boot a web app — stay green.
- **A test that silently uses the dev Redis** because it declared no container.

For each: does this diff introduce one, or walk past one it should have caught?

### 5. Audit every claim the diff makes

Commit message, doc edits, code comments, and any number in any of them. For each: **how was
this derived?** A number that cannot name its source gets cut or gets measured. This project's
working agreement is *measure before asserting* — a review that lets an unmeasured number
through has failed at the thing the project is about.

Check too whether the diff makes an existing claim false somewhere it did not edit. Run
`./scripts/session-check.sh` and treat any `drift` row as a finding.

### 6. Report

Findings first, ranked by severity. Each one:

- **What is wrong**, in one sentence.
- **A concrete failure scenario** — specific inputs or state, leading to specific wrong
  behaviour. "This could be fragile" is not a finding; "a second profile named X by the same
  user returns 500 instead of 409" is.
- **Where** — `file:line`.

Then, always, two sections that do not get skipped:

- **What I did not fix, and why.** Every issue seen and deliberately left. Silence here reads as
  "nothing was left", and that is almost never true.
- **The argument against this change.** State the strongest case that it should not be merged as
  written, even if you conclude it should. Name what would falsify the approach.

If you found nothing: **list what you searched.** "Nothing found" with a search list is a
finding. "Looks good" is not a review.
