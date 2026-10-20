# Fantasy Kai

**Fantasy football rankings scored by your league's exact rules.**

Fantasy Kai is a live, full-stack NFL analytics platform. It pulls real NFL data every morning and
stores raw player-game statistics instead of fantasy points, so every ranking and player page is
scored on demand under the ruleset you choose: 0 PPR, half PPR, PPR, or your own league's settings.

**Live: [www.fantasykai.com](https://www.fantasykai.com)** (a free account is required to view rankings)

[![CI](https://github.com/balprab24/fantasy-kai/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/balprab24/fantasy-kai/actions/workflows/ci.yml)

Java 25 · Spring Boot 3.5 · PostgreSQL 16 · Redis 7 · Next.js 16 · React 19 · TypeScript ·
Docker Compose on Oracle Cloud · Vercel

---

## What works today

| Feature | What it does |
|---|---|
| **League-specific rankings** | QB/RB/WR/TE boards for any season from 2020 to the current one, by season total, per-game average or the last four weeks, with tiers and positional ranks. Switching the ruleset re-scores the whole board and names the biggest moves it made. |
| **Player pages** | Opened over the board or on their own URL: a player card with the season in figures and the scoring beside it, then the game log and career as heat tables (career per game or in totals), his last 17 games charted across seasons, and the same season under every ruleset side by side -- all scored under the selected ruleset. |
| **Scoring-rule builder** | Build and save your own rulesets: per-stat rates, threshold bonuses and per-position overrides. They are private to your account. |
| **Accounts** | Email sign-up and sign-in. Every data read requires an account. |
| **Daily data** | Production pulls nflverse and Sleeper data at 06:00 ET every day, so in-season weeks fill in as stat corrections land. |
| **Historical data** | 110K+ weekly stat lines for every NFL player since the 2020 season. |

### Status

| Shipped | Experimental (built, intentionally not shipped) | Planned |
|---|---|---|
| Everything in the table above, live in production | A projection model and the leakage-controlled backtest that rejected it ([details](#projection-experiments)) | League import, explained projections, trade evaluator, waivers ([roadmap](#roadmap)) |

---

## The core idea: store statistics, not points

Most fantasy tools store one points value per player per week, which is only correct for one
scoring system. Fantasy Kai stores the **raw stat line** (passing yards, receptions, touchdowns,
fumbles lost and so on) and treats scoring rules as separate data:

```
nflverse + Sleeper ──daily ingest──▶ PostgreSQL: raw player-game stat lines (no points stored)
                                                 │
your ruleset (JSON) ──validate──▶ compile to weights + bonuses
                                                 │
                                                 ▼
                      ScoringEngine: score each game, then sum ──▶ rankings and player pages
```

- **One code path for every league.** PPR, half PPR, 0 PPR and TE premium are rows in a table,
  not separate implementations. A league with 6-point passing touchdowns is one more row.
- **History re-scores instantly.** The same 2020–present data produces a different board for every
  ruleset, with nothing to recompute or backfill when a rule changes.
- **Games are scored individually, then summed,** so threshold bonuses (for example, 100 rushing
  yards in a game) apply per game, as real leagues apply them. Points are rounded once, at the API
  boundary.

---

## Architecture

```
Browser ──▶ www.fantasykai.com       Next.js 16 / React 19 on Vercel
              │  HTTPS · short-lived access token in memory · refresh token in an HttpOnly cookie
              ▼
           api.fantasykai.com        Caddy (TLS) ─▶ Spring Boot API ─▶ PostgreSQL 16
                                                         │        └─▶ Redis 7 (auth rate limits)
                                     Docker Compose on an Oracle Cloud VM
                                                         ▲
                                     nflverse + Sleeper, pulled daily by an in-app scheduler
```

| Layer | What it does |
|---|---|
| **Frontend** (`frontend/`) | Next.js App Router with TypeScript and TanStack Query. Public landing and sign-in pages, plus the signed-in rankings, player and ruleset workspaces. Every page is served with a per-request nonce Content-Security-Policy. |
| **API** (`backend/`) | Spring Boot REST API: rankings, players, game logs, careers, scoring profiles and auth. Queries are plain SQL through `JdbcTemplate`; sort and filter inputs are whitelisted, never concatenated into SQL. |
| **Database** | PostgreSQL 16 with a Flyway-versioned schema. It holds players, games, Vegas lines, weekly stat lines, scoring profiles and users. |
| **Redis** | Backs the distributed rate limiter on the auth endpoints (Bucket4j). |
| **Ingest** | nflverse CSV releases plus a Sleeper ID crosswalk, upserted idempotently on a daily schedule. |
| **Hosting** | The frontend runs on Vercel. The API, PostgreSQL, Redis and Caddy run as one Docker Compose stack on an Oracle Cloud VM. |

---

## Engineering highlights

### Scoring engine, checked against an independent source

A ruleset is JSON. It is parsed and validated (unknown keys are rejected by name, rates are
bounded), then compiled into a weight vector plus threshold bonuses. Scoring a game is a dot product
plus those bonuses. Equivalent rulesets hash identically because the hash is computed over the
compiled weights, not over the JSON someone typed.

To check the arithmetic against something this project did not write: across all **19,422**
player-game rows of nflverse's 2025 file, the engine reproduces nflverse's own published fantasy
points on **19,383**. The other **39** all differ by exactly two points, and every one is a
returner who lost a muffed punt or kickoff. Fantasy Kai deliberately penalizes any lost fumble,
as real leagues do, while nflverse counts only offensive fumbles. A 53-row fixture of edge cases
(all three kinds of two-point conversion, return touchdowns, both kinds of lost fumble, 100- and
300-yard bonuses) pins this in the test suite.

### Data pipeline

- An in-app scheduler pulls the current season at 06:00 ET every day. nflverse revises weeks as
  stat corrections land, so the pull is daily and every write is an idempotent upsert.
- Each CSV's header is checked against the columns the ingestor reads, so an upstream rename fails
  the run loudly instead of silently zeroing a stat.
- A health indicator reports ingest freshness. Liveness is a separate check, so a stalled pull
  degrades the service instead of restarting it.

### Authentication and security

- Passwords are hashed with **Argon2id**.
- Short-lived JWT access tokens are paired with **rotating refresh tokens**. Replaying a refresh
  token that was already used revokes its entire token family.
- A **Redis-backed rate limiter** (Bucket4j) protects the auth endpoints. A security pass found
  **three ways to bypass it**: the RFC 7239 `Forwarded` header, `X-Forwarded-Prefix`, and a
  percent-encoded path. Each was reproduced, fixed at both the proxy and the application, and
  re-verified against production.
- A ruleset belongs to one account. Requesting another user's ruleset returns `404`, not `403`,
  so the response does not confirm that it exists.

### Projection experiments

Projections must earn their way into the product. The backtest harness controls for leakage:
every model choice is frozen on the 2024 season and committed *before* the 2025 test season is
read, and the test command refuses to run until that frozen config is committed.

The first model was 24 ridge regressions, one per (position, stat), solved directly in Java. It
beat every baseline at every position and under every ruleset, and improved PPR mean absolute
error by **2.7%** over the strongest baseline (95% confidence interval excludes zero). The
acceptance threshold, set in advance, was **3%**, so the model was **not shipped**. Method, frozen
choices and full results: [`docs/perf/projection-accuracy.md`](docs/perf/projection-accuracy.md).
Follow-up projection work is in development on feature branches.

### Performance baseline

A k6 load test of the rankings endpoint at 1, 5, 10 and 20 concurrent users measured p95 latency
from **21 ms to 125 ms**, with throughput levelling off near **256 requests/s**. Profiling both
processes showed **about 88% of CPU in PostgreSQL**, not in the Java scoring code the original
design assumed was the bottleneck. This is a measured baseline. The optimization pass it informs
(ranking cache, materialized view, indexes) has not been done yet. See
[`docs/perf/baseline.md`](docs/perf/baseline.md).

### Testing and CI

The backend suite uses JUnit 5 and Testcontainers against real PostgreSQL 16 and Redis. It covers
the scoring engine, ingestion, the REST API, authentication, rate limiting and per-account
isolation. The frontend has unit tests for its data and formatting logic. GitHub Actions runs the
backend build and tests, frontend lint, tests and build, and an OWASP dependency check.

---

## Production

- **Site:** [www.fantasykai.com](https://www.fantasykai.com), Next.js on Vercel.
- **API:** `api.fantasykai.com`. Spring Boot, PostgreSQL, Redis and Caddy run in Docker Compose
  on an Oracle Cloud VM, with TLS terminated by Caddy.
- **Data:** refreshed daily at 06:00 ET by the API's own scheduler.
- **Runbook:** [`deploy/README.md`](deploy/README.md) covers setup, redeploys with a tested
  rollback, and the acceptance checks in `deploy/acceptance.sh`. How the deployment was verified
  and what went wrong along the way: [`docs/deployment.md`](docs/deployment.md).

---

## Roadmap

Future work, not yet built. See [`docs/north-star.md`](docs/north-star.md) for the full plan.

- **Explained projections:** next-week projections whose numbers break down into their parts.
- **League import:** create a league's ruleset automatically from its Sleeper or ESPN settings.
- **Roster tools:** lineup optimizer, a trade evaluator that values a trade as the change in your
  expected wins, and a waiver board.
- **Market consensus board** built from real draft and roster data.
- **Performance pass:** ranking cache, materialized view and indexes, measured against the baseline above.

---

## Running locally

Prerequisites: **JDK 25** (the build enforces it), **Docker**, and **Node 24** (`frontend/.nvmrc`).

```bash
cp .env.example .env               # local-only placeholder values; no real secrets
docker compose up -d               # PostgreSQL 16 on :5433, Redis 7 on :6379
set -a && source .env && set +a    # the backend reads JWT_SECRET and DB settings from the env

cd backend
# First run: start the API and backfill historical seasons (takes well under a minute)
./mvnw spring-boot:run -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true
# Later runs
./mvnw spring-boot:run              # http://localhost:8080
```

```bash
cd frontend
nvm use && npm ci
npm run dev                        # http://localhost:3000; expects the API on :8080
```

Create an account at `http://localhost:3000/register`; every read requires one.

**Tests**

```bash
(cd backend && ./mvnw -B verify)                           # needs Docker (Testcontainers)
(cd frontend && npm run lint && npm test && npm run build)
```

**Projection backtest** (needs the local PostgreSQL loaded with data):

```bash
./scripts/backtest.sh validate     # tune on 2024 and write the frozen config
./scripts/backtest.sh test         # score 2025 once the frozen config is committed
```

## Repository layout

```
backend/    Spring Boot API: ingestion, scoring engine, auth, REST layer, Flyway migrations
frontend/   Next.js web app: landing page, rankings, player pages, ruleset builder
deploy/     Production Docker Compose stack, Caddyfile, runbook and acceptance checks
docs/       Product plan, engineering design, glossary, performance and projection results
perf/       k6 load-test script
scripts/    Packaging, one-shot ingest, backtest runner and repository health check
```

Further reading: [`docs/architecture.md`](docs/architecture.md) (design, invariants, measured
numbers) · [`docs/development.md`](docs/development.md) (commands and lessons learned) ·
[`docs/deployment.md`](docs/deployment.md) (production write-up) ·
[`docs/orientation.md`](docs/orientation.md) (plain-English tour and glossary) ·
[`docs/north-star.md`](docs/north-star.md) (product scope and roadmap) ·
[`CONTRIBUTING.md`](CONTRIBUTING.md) (how changes are reviewed).

## Data sources and attribution

Player and statistical data comes from **[nflverse](https://github.com/nflverse/nflverse-data)**, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Data is redistributed here under those terms with attribution to the nflverse project and its contributors.

Player identifiers and ownership signals come from the **[Sleeper API](https://docs.sleeper.com/)**, which is free for **non-commercial use only**. This project is a non-commercial portfolio project and uses the API within those terms.

This project is not affiliated with, endorsed by, or connected to the NFL, ESPN, Sleeper, or any fantasy football provider.

## License

MIT. See [LICENSE](LICENSE).
