# League prerequisite integration

Main `ac2aa8c` had the redesigned frontend but lacked the projection backend through
`7665721`. Applied the backend delta from the common ancestor with a three-way patch;
kept main's Redis test dependency and documentation refactor. Included the matching
research harness, architecture, accuracy report and backtest script.

## Validation

- Backend `./mvnw -B verify`: 366 tests, zero failures/errors/skips, BUILD SUCCESS.
- Frontend: lint passes; 90 tests pass; production build passes.
- The first build rejected Next 16.4.0. Updated Next and eslint-config-next to 16.4.3
  and applied npm audit fixes; final audit reports zero vulnerabilities.
- No frontend source changes relative to main; only dependency manifests differ.
- No changes to migrations V1–V6 or main's backend dependency versions.
- Projection generation stays disabled. No production migration or deployment ran.
- Packaging generated a matching source-hash sidecar.

## Review findings and remaining work

The imported projection API still sorts rounded values; this is the existing S0
item, not resolved by the prerequisite integration. The six matcher unit tests pass,
but the matcher is not connected to ingest and no coverage measurement is claimed.
The new league domain, REST-of-season evaluator and Trades remain unimplemented.

Browser QA was not run in this integration: frontend source was preserved, but
runtime behavior with the imported member rate limiter still needs browser coverage.
The limiter makes Redis a dependency for member reads and fails closed on outage.

## Argument against merging

This brings a substantial backend delta onto the redesigned UI baseline. Passing
unit/integration tests does not establish live projection accuracy or full browser
compatibility. Review those boundaries before merging; keep projection generation
disabled until its separate activation requirements are met.

Original checkout and its RulesetJson edits are untouched. Work continues in
`/Users/prabhnoorbal/fantasy-kai-leagues`. For session checks against the existing
local database, set `COMPOSE_PROJECT_NAME=fantasy-kai` (the worktree has a different
default Compose project name).
