# fantasy-kai

NFL fantasy analytics platform — recomputes player rankings against any league scoring ruleset. Spring Boot, PostgreSQL, Next.js.

The core design decision: **store raw stat lines, never fantasy points.** Full PPR, half PPR, standard and TE premium stop being separate code paths and become rows in a table; points are computed on demand against whatever ruleset you ask for.

## Status

**Phases 0–4 shipped.** Infrastructure, versioned schema, the nflverse + Sleeper ingestion
pipeline, the scoring engine, a read API, a measured performance baseline, and the Vegas
columns. **112,319 weekly stat lines across the 2020–2025 seasons** — 1,243 distinct
QB/RB/WR/TE players across those six seasons, 578–633 in any single one. Five REST
endpoints serve rankings, players and game logs against any scoring ruleset, and an
account gets you scoring profiles of your own. 130 tests.

No web UI yet — the Next.js shell is the next slice of Phase 5.

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
empty until you backfill. Six seasons take 22.8 seconds:

```bash
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true
```

Now ask it something. `profileId=3` is full PPR; the presets are seeded by `V3`:

```bash
curl -s 'localhost:8080/api/v1/rankings?profileId=3&season=2025&position=WR&size=5'
```

Change `profileId` and every number changes, because none of them were stored.

**Make it yours.** Reads are public; a profile of your own needs an account:

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"a-long-enough-password"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s -X POST localhost:8080/api/v1/scoring-profiles \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"name":"My League","rules":"{\"version\":1,\"base\":{\"rec\":1.5,\"rec_td\":6}}"}'
```

Your profile is yours: the `user_id` filter is in the query rather than in a service check, so
another account asking for its id gets a 404 — not a 403, which would confirm it exists.

Run the tests — these boot a throwaway PostgreSQL 16 via Testcontainers and apply the migrations for real, so Docker must be running:

```bash
cd backend && ./mvnw verify
```

**Keep it current.** nflverse revises the current week mid-week as corrections land, so the pull is
daily rather than weekly and the upsert is idempotent. One command installs it:

```bash
./scripts/install-ingest.sh        # substitutes paths, loads the launchd agent, proves it landed
launchctl start com.fantasykai.ingest   # run it now rather than waiting for 06:00
```

It needs a current jar (`cd backend && ./mvnw -B package`) and refuses to run a stale one. launchd
fires on wake rather than at 06:00 on a laptop that sleeps, and on local time rather than ET — so
gaps happen, and `/actuator/health` names them:

```bash
curl -s localhost:8080/actuator/health | jq .components.ingestFreshness
```

That component goes DOWN when the pull has stopped or a source failed. A missed pull is degraded,
not down, so a platform health check belongs on `/actuator/health/liveness` instead.

## Layout

```
backend/    Spring Boot API — ingestion, scoring engine, REST layer
docs/       Design docs, the roadmap, and the measured performance baseline
perf/       k6 load script
scripts/    Daily ingest — installer, launchd plist, one-shot runner — and the EXPLAIN harness
```

`frontend/` arrives in Phase 5 alongside authentication.

Schema lives in `backend/src/main/resources/db/migration/`. Flyway owns it; nothing is created by Hibernate.

## Data sources and attribution

Player and statistical data comes from **[nflverse](https://github.com/nflverse/nflverse-data)**, licensed under [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Data is redistributed here under those terms with attribution to the nflverse project and its contributors.

Player identifiers and ownership signals come from the **[Sleeper API](https://docs.sleeper.com/)**, which is free for **non-commercial use only**. This project is a non-commercial portfolio project and uses the API within those terms.

This project is not affiliated with, endorsed by, or connected to the NFL, ESPN, Sleeper, or any fantasy football provider.

## License

MIT — see [LICENSE](LICENSE).
