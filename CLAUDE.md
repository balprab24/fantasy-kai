# fantasy-kai — instructions for coding agents

Humans: start with [`README.md`](README.md) and [`CONTRIBUTING.md`](CONTRIBUTING.md). This file
exists so AI coding assistants load the same project rules a human contributor reads; everything
it imports below is ordinary project documentation.

- Run `./scripts/session-check.sh` first. Where it disagrees with a doc, the check wins and the
  doc is what gets fixed.
- Product scope and sequencing: `docs/north-star.md`. Engineering rationale:
  `docs/fantasy-platform-handoff.md`.
- Work is done when it has passed the review pass in `CONTRIBUTING.md` (`/review-pass`).
- Personal, machine-specific notes belong in a gitignored `CLAUDE.local.md`, never here.

@docs/architecture.md
@docs/development.md
@CONTRIBUTING.md
