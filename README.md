# fantasy-kai

NFL fantasy analytics platform — recomputes player rankings against any league scoring ruleset. Spring Boot, PostgreSQL, Next.js.

The core design decision: **store raw stat lines, never fantasy points.** Full PPR, half PPR, standard and TE premium stop being separate code paths and become rows in a table; points are computed on demand against whatever ruleset you ask for.

## Status

**Phases 0–4 shipped.** Infrastructure, versioned schema, the nflverse + Sleeper ingestion
pipeline, the scoring engine, a read API, a measured performance baseline, and the Vegas
columns. **112,319 weekly stat lines across the 2020–2025 seasons** — 1,243 distinct
QB/RB/WR/TE players across those six seasons, 578–633 in any single one. Five REST
endpoints serve rankings, players and game logs against any scoring ruleset. 80 tests.

No web UI yet — that ships with authentication in Phase 5.

The measured headline so far: the rankings endpoint is CPU-bound, and **88% of that CPU is
Postgres, not the Java scorer** — which disproved the hypothesis the design doc was built
on. See [`docs/perf/baseline.md`](docs/perf/baseline.md).

**Start here:** [`docs/map.md`](docs/map.md) — where everything is and what state it's in.
Then [`docs/north-star.md`](docs/north-star.md) for scope and the roadmap,
[`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md) for engineering
rationale (its §1 and §11 are superseded by the north star), and [`CLAUDE.md`](CLAUDE.md)
for operational notes.

## Stack

Spring Boot 3.5 (Java 21) · PostgreSQL 16 · Flyway · Redis 7 (provisioned, first used in Phase 5) · Next.js 15 (Phase 5)

## Local setup

Requires JDK 21 and Docker.

```bash
cp .env.example .env
docker compose up -d            # Postgres 16 on :5433, Redis 7 on :6379

cd backend
./mvnw spring-boot:run          # http://localhost:8080
```

Postgres is published on **5433**, not the usual 5432, so the project coexists with a
system or Homebrew Postgres. Change `POSTGRES_PORT` and `DB_URL` in `.env` if 5432 is
free on your machine.

Verify it came up:

```bash
curl -s localhost:8080/actuator/health
# {"status":"UP", ...}
```

**Then load the data** — the app starts against an empty database, so every ranking is
empty until you backfill. Six seasons take about 20 seconds:

```bash
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true
```

Now ask it something. `profileId=3` is full PPR; the presets are seeded by `V3`:

```bash
curl -s 'localhost:8080/api/v1/rankings?profileId=3&season=2025&position=WR&size=5'
```

Change `profileId` and every number changes, because none of them were stored.

Run the tests — these boot a throwaway PostgreSQL 16 via Testcontainers and apply the migrations for real, so Docker must be running:

```bash
cd backend && ./mvnw verify
```

## Layout

```
backend/    Spring Boot API — ingestion, scoring engine, REST layer
docs/       Design docs, the roadmap, and the measured performance baseline
perf/       k6 load script
scripts/    One-shot ingest, launchd plist, EXPLAIN harness
```

`frontend/` arrives in Phase 5 alongside authentication.

Schema lives in `backend/src/main/resources/db/migration/`. Flyway owns it; nothing is created by Hibernate.

## Data sources and attribution

Player and statistical data comes from **[nflverse](https://github.com/nflverse/nflverse-data)**, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Data is redistributed here under those terms with attribution to the nflverse project and its contributors.

Player identifiers and ownership signals come from the **[Sleeper API](https://docs.sleeper.com/)**, which is free for **non-commercial use only**. This project is a non-commercial portfolio project and uses the API within those terms.

This project is not affiliated with, endorsed by, or connected to the NFL, ESPN, Sleeper, or any fantasy football provider.

## License

MIT — see [LICENSE](LICENSE).
