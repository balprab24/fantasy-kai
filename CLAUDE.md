# fantasy-kai — project brain

NFL fantasy analytics, live at `https://www.fantasykai.com` (API `https://api.fantasykai.com`).
Java 25 / Spring Boot 3.5.16 · PostgreSQL 16 · Redis 7 · Next.js 16 / React 19 / TypeScript 6.
**Spring Boot 3.5 has been OSS-EOL since 2026-06-30** — see north-star §10 row 11.5, which carries the clock.

This file holds only what every session needs. The rest loads when it is relevant:

| Where | Owns | Loads |
|---|---|---|
| [`docs/north-star.md`](docs/north-star.md) | **Scope, sequencing, product decisions** — the roadmap (§10), what we will not build (§9) | on demand |
| [`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md) | **Engineering rationale** — §5 schema, §6 scoring, §8 security, §9 perf. §1 and §11 are superseded | on demand |
| [`docs/map.md`](docs/map.md) | **The front door** — status board, class map, the two pipelines, where to look for anything | on demand |
| [`docs/orientation.md`](docs/orientation.md) | Plain English, real-vs-planned, and a glossary | on demand |
| [`docs/decisions.md`](docs/decisions.md) | The decision log — an index of past decisions, plus a full entry for each new one | on demand |
| [`docs/traps.md`](docs/traps.md) | Every trap this codebase hit, told in full, and the incidents behind them | on demand |
| [`docs/measurements.md`](docs/measurements.md) | Measured numbers and how each was taken — **quote them, do not re-derive or estimate them** | on demand |
| [`docs/learning-log.md`](docs/learning-log.md) | For each feature: what was built, why, what could go wrong, what was learned | on demand |
| `.claude/rules/*.md` | The traps as one-line rules, scoped by path | **when Claude reads a matching file** |

When this file disagrees with any of them, the doc wins — fix this file.

## Every session starts here

`./scripts/session-check.sh` (~3s, read-only) runs at session start from the SessionStart hook, and
by hand any time. It re-derives what the docs only claim: toolchain, containers, git/PR state,
Flyway checksums, row counts, ingest freshness, jar staleness, whether a handoff is waiting, and the
counts written in `docs/map.md` §1. **Where the check and a doc disagree, the check wins, and the doc
gets fixed before anything else.** A check that cannot run prints `?`, never `ok`. It does not run
tests or builds — those are the definition of done, below.

## The one idea

**Store raw stat lines, never fantasy points. Compute points on demand against a ruleset.**

Full PPR, half PPR, standard and TE premium stop being three code paths and become three rows in a table. Everything else in the design serves this. If a change would persist a computed point value, it is the wrong change.

## Invariants — do not break these

| Invariant | Why | Expires |
|---|---|---|
| `player_game_stats` carries **no index past its primary key** | §9's whole performance story is a measured before/after. An index added early destroys the baseline and there is no way to recover it without re-measuring from scratch. | Phase 11, as a numbered migration |
| Never store `fantasy_points` / `fantasy_points_ppr` | The source ships both. A stored point value is correct for exactly one ruleset. Persisting them reintroduces the thing the architecture exists to avoid. | never |
| Flyway owns the schema; `ddl-auto` stays `validate` | Versioned schema from commit 1. **It must never become `update`** — that is the whole guarantee today. The drift check this setting is famous for is *vacuous here*: there are zero `@Entity` classes, so it validates nothing, and Phase 5's decision to stay on `JdbcTemplate` keeps it that way. Say the real guarantee, not the advertised one. | never |
| `.env` is gitignored, `.env.example` is committed | No secret in `application.yml`. JWT secret comes from env (Phase 5). | never |
| No string concatenation into SQL — including dynamic sort/filter | Whitelist sortable columns by name. §8. | never |
| `StatKey` is the only place a scorable stat is named | It is the validation allowlist, the dot-product array index, and the `player_game_stats` column name at once. Adding a stat anywhere else breaks one of the three. | never |
| Round points once, at the API boundary | `ScoringEngine` never rounds. A season total must be the rounded sum of weeks, not the sum of rounded weeks. | never |
| A ranking scores **each game, then sums** — never one score over summed stats | Threshold bonuses make `ScoringEngine` non-linear, so a 100-yard bonus belongs to a game. It also keeps §9 Step 4's matview meaningful: if Phase 3 pre-aggregated, the perf pass would have nothing left to collapse. | never |
| Ingest every position, filter at query time | v1 scores QB/RB/WR/TE only, but storing only those rows would cut `player_game_stats` from 112K to 37K and gut the §9 baseline — and K/DST in v2 would then need a backfill after all. | never |
| **Project the stat line, never the points** | A projection row carries the same 13 `StatKey` columns and is scored by the unmodified `ScoringEngine`. A projected point value is right for exactly one ruleset — the `fantasy_points` mistake, one layer up. north-star §3.1 | never |
| **Store signal components, never a blended rank** | A rank is a function of (signals × recipe × league). Storing it freezes the recipe and turns a weight change into a backfill. north-star §3.2 | never |
| `SignalKey` is the only place a blendable signal is named | Same three-jobs trick as `StatKey`, one level up: recipe allowlist, dot-product index, signals-table column name. north-star §3.3 | never |
| **Every ranked number carries its parts** — `ExplainedScore`, not `double` | "Why is he ranked here" *is* the product. Explanation bolted on afterwards ends up absent or wrong. | never |
| Every external signal is optional; a ranking computes without it | $0 budget, unlicensed sources, and ESPN will break mid-season. Degrade the explanation, never 500 the endpoint. | never |
| A trade is valued in **expected wins**, never a per-player number | A context-free player value is exactly what every basic calculator gets wrong. north-star §7 | never |
| Third-party league data is fetched **as the user, with their own credentials**, never redistributed or aggregated | The line that separates legitimate league import from scraping. It is what makes ESPN import defensible. | never |
| **No expert rankings ingested, ever** — consensus means *market* consensus | Real drafts (FFC), real roster rates (Sleeper), real lines (nflverse). Handoff §0. | never |
| No paywall, no ads, no sportsbook links, no affiliate | The product promise — and it keeps Apple guideline 5.3 out of scope entirely. | never |

## Failure classes this codebase has already produced

Hunt for these in every change. The specific instances load with `.claude/rules/`; the stories are
in `docs/traps.md`.

- **A default that hides an absence** — "could not read it" and "read it, it was zero" look the same.
- **An exit code trusted over the output** — `java_home -v 25` exits 0 and returns 21.
- **An mtime trusted over the content** — a git checkout rewrites mtimes without changing a byte.
- **NULL defeating a UNIQUE constraint.**
- **A cache consulted before the ownership filter.**
- **`@Transactional` undoing the write you made before you threw.**
- **`wasNull()` and argument evaluation order.**
- **Binary rounding standing in for decimal rounding.**
- **A bean that only exists in a servlet context** — it breaks the headless entrypoint while every
  test, all of which boot a web app, stays green.
- **State shared between tests** — the dev Redis, or a bucket the previous test drained — which turns
  a failure into a green bar.
- **A trust boundary defined by the one thing you thought of** — Caddy overwrote one forwarded header
  of seven. List everything that crosses the boundary.
- **Configured is not the same as running** — a header that is configured but never sent, a
  Caddyfile changed but never reloaded, a CI check that passes without ever running.

## Where things are

```
backend/src/main/java/com/fantasykai/
  ingest/    nflverse + Sleeper pipeline (19 classes)      scoring/  ruleset model, validator, dot product
  query/     JdbcTemplate reads, StatKey-generated SQL, the §8 whitelists
  api/       controllers, DTOs, RFC 7807 advice, the write endpoints
  auth/      filter chain, JWT, rotating refresh, Argon2id, rate limit, forwarded-header allowlist
backend/src/main/resources/db/migration/   Flyway V1 schema · V2 ingestion · V3 presets · V4 Vegas · V5 refresh_tokens
backend/src/test/resources/nflverse/       real 2024 rows as fixtures — not invented
backend/Dockerfile       Temurin 25 JRE on Alpine, arm64 and amd64
deploy/                  compose.prod.yml + Caddyfile + README.md (the runbook, the firewall trap, the acceptance checks)
frontend/src/            Next.js 16 App Router; lib/api.ts holds the only access token, in memory
docs/perf/               baseline.md; projection-accuracy.md (Phase 6) and results.md (Phase 11) are owed
perf/rankings.js         k6 load script — pins season=2025 on purpose
scripts/                 session-check.sh · package.sh + lib/jar-state.sh · install-ingest.sh + launchd plist ·
                         ingest-once.sh · perf-explain.sh · db-restore.sh (writes to PRODUCTION — ask first)
.claude/                 settings.json (hook, permissions) · rules/ · skills/ · agents/ · commands/review-pass.md
                         local/ (gitignored: handoff.md, ai-workflow-log.md)
```

## Commands

```bash
./scripts/session-check.sh                # re-derive what the docs claim; exits 1 on any FAIL or drift
./scripts/session-check.sh --no-network   # skip gh and the deploy probe

docker compose up -d                      # Postgres on :5433 (not 5432), Redis on :6379

# Java 25 or nothing compiles. java_home exits 0 even when 25 is absent -- check what it PRINTS.
export JAVA_HOME=$(/usr/libexec/java_home -v 25) && java -version

JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run   # or `source .env` first
cd backend && ./mvnw -B verify            # needs Docker -- Testcontainers boots a real PG 16
cd backend && ./mvnw clean compile        # after a public signature moves

docker compose exec postgres psql -U fantasykai -d fantasykai

# one-shot historical backfill
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true

./scripts/ingest-once.sh                  # one-shot current-season pull (what launchd runs daily)
./scripts/package.sh                      # the ingest jar + its source hash; never a bare ./mvnw package
                                          # add --with-tests to run the suite too
./scripts/install-ingest.sh               # install the daily job (idempotent; needs a current jar)
launchctl list | grep fantasykai          # loaded?
launchctl start com.fantasykai.ingest     # run it now
tail -f logs/ingest.log

curl -s localhost:8080/actuator/health | jq .components.ingestFreshness   # anonymous sees status only

cd frontend && nvm use && npm ci          # Node 24 (.nvmrc); Node 20 went EOL 2026-04-30
npm run dev                               # :3000, expects the API on :8080
npm run lint && npm run build             # what CI runs

cd backend && docker build -t fantasykai-backend:local .   # the deploy image
unzip -l backend/target/backend-0.0.1-SNAPSHOT.jar | grep tomcat-embed-core   # must say 10.1.59
```

**Postgres is on 5433** because a Homebrew `postgresql@16` launchd service owns 5432 on the dev Mac and wins the connection. Symptom when this bites: `role "fantasykai" does not exist`.

## How we work

This project exists to be **understood and defended by its owner**, not just to run.

- **Claude** investigates, proposes, implements what is approved, writes and runs the tests, flags
  risks and its own mistakes, and teaches. **The owner** sets product goals, owns system boundaries,
  makes the decisions, and reviews what changed and what the verification actually showed.
- **Stop and lay out the options before any decision about architecture, security, database design,
  external dependencies, cost or operational complexity** — the options, the tradeoffs, a
  recommendation, and when it would be worth revisiting. Trivial implementation details need no
  approval.
- **Nothing is pushed, merged, deployed, or run against production without explicit approval**, each
  time. Local commits on a feature branch are fine.
- **The workflow:** `/explore-feature` (plan mode, no edits) → `/implement-feature <plan>` in a fresh
  session → `/review-pass` (a forked reviewer that never saw the implementation) → `/teach-feature` →
  `/log-success` or `/log-error` → `/handoff` when stopping mid-task. Scale it to the task: a typo
  does not need a plan.
- **One task per session.** Durable decisions go to `docs/decisions.md`, not the conversation. Start
  fresh (`/clear`) at task boundaries; use `/compact <what to keep>` when continuing the same task.
- The `Explore` and `Plan` subagents **skip this file** — give them the invariants that matter in
  their prompt.

## Working agreement

**Measure before asserting. Prove a constraint by trying to violate it.**

This project exists to be defended out loud in an interview, so any type, constraint or number that was picked by default becomes something its owner has to justify. Before a migration, pull the real values rather than reasoning about what the type should be — that habit has already caught the fractional sacks and the NULL-defeated unique constraint. Before quoting a number, run the query. Fixing a 200-line schema is free; fixing it under 112K rows is not.

Report design gaps you are *not* fixing explicitly rather than staying quiet about them.

## Definition of done — the review pass

Work is not finished when it works. It is finished when someone has tried to break it and written
down what they tried. `/review-pass` runs the checklist, which is defined once in
[`.claude/commands/review-pass.md`](.claude/commands/review-pass.md):

1. Read the diff before the intent.
2. Run the gates and paste what they printed — `./scripts/session-check.sh` · `./mvnw -B verify` ·
   `npm run lint && npm run build`. A gate that was not run is reported as **not run**, never as
   passing.
3. Walk the invariants as yes/no questions.
4. Hunt the failure classes above.
5. Audit every claim, the commit message included; a `drift` row is a finding.
6. Report findings ranked by severity, each with a concrete failure scenario. Then two sections that
   are never skipped: **what I did not fix, and why**, and **the argument against this change**.

If nothing was found, list what was searched.
