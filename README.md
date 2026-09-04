# fantasy-kai

NFL fantasy analytics platform — recomputes player rankings against any league scoring ruleset. Spring Boot, PostgreSQL, Next.js.

The core design decision: **store raw stat lines, never fantasy points.** Full PPR, half PPR, standard and TE premium stop being separate code paths and become rows in a table; points are computed on demand against whatever ruleset you ask for.

## Status

Phases 0–1 done: infrastructure, versioned schema, and the nflverse + Sleeper ingestion pipeline. **112,319 weekly stat lines across the 2020–2025 seasons**, covering 1,243 distinct QB/RB/WR/TE players. Phase 2 — the scoring engine — is in progress. Nothing user-facing yet.

See [`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md) for the full design and build plan, and [`CLAUDE.md`](CLAUDE.md) for the operational notes.

## Stack

Spring Boot 3.5 (Java 21) · PostgreSQL 16 · Redis 7 · Flyway · Next.js 15 (Phase 4)

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

Run the tests — these boot a throwaway PostgreSQL 16 via Testcontainers and apply the migrations for real, so Docker must be running:

```bash
cd backend && ./mvnw verify
```

## Layout

```
backend/    Spring Boot API — scoring engine, ingestion, REST layer
docs/       Design docs and (from Phase 6) performance measurements
frontend/   Next.js app — Phase 4
```

Schema lives in `backend/src/main/resources/db/migration/`. Flyway owns it; nothing is created by Hibernate.

## Data sources and attribution

Player and statistical data comes from **[nflverse](https://github.com/nflverse/nflverse-data)**, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Data is redistributed here under those terms with attribution to the nflverse project and its contributors.

Player identifiers and ownership signals come from the **[Sleeper API](https://docs.sleeper.com/)**, which is free for **non-commercial use only**. This project is a non-commercial portfolio project and uses the API within those terms.

This project is not affiliated with, endorsed by, or connected to the NFL, ESPN, Sleeper, or any fantasy football provider.

## License

MIT — see [LICENSE](LICENSE).
