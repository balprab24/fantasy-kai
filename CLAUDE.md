# fantasy-kai — project brain

NFL fantasy analytics platform. Java 21 / Spring Boot 3.5.16 · PostgreSQL 16 · Redis 7 · Next.js 15 (Phase 4).

**Full spec: [`docs/fantasy-platform-handoff.md`](docs/fantasy-platform-handoff.md).** It is the source of truth for scope, sequencing and rationale; this file is the operational memory that sits alongside it. When the two disagree, the doc wins — and fix this file.

## The one idea

**Store raw stat lines, never fantasy points. Compute points on demand against a ruleset.**

Full PPR, half PPR, standard and TE premium stop being three code paths and become three rows in a table. Everything else in the design serves this. If a change would persist a computed point value, it is the wrong change.

## Invariants — do not break these

| Invariant | Why | Expires |
|---|---|---|
| `player_game_stats` carries **no index past its primary key** | §9's whole performance story is a measured before/after. An index added early destroys the baseline and there is no way to recover it without re-measuring from scratch. | Phase 6, as a numbered migration |
| Never store `fantasy_points` / `fantasy_points_ppr` | The source ships both. A stored point value is correct for exactly one ruleset. Persisting them reintroduces the thing the architecture exists to avoid. | never |
| Flyway owns the schema; `ddl-auto` stays `validate` | Versioned schema from commit 1. `validate` fails fast the moment a JPA entity drifts from a migration. It must never become `update`. | never |
| `.env` is gitignored, `.env.example` is committed | No secret in `application.yml`. JWT secret comes from env (Phase 5). | never |
| No string concatenation into SQL — including dynamic sort/filter | Whitelist sortable columns by name. §8. | never |
| Ingest every position, filter at query time | v1 scores QB/RB/WR/TE only, but storing only those rows would cut `player_game_stats` from 112K to 37K and gut the §9 baseline — and K/DST in v2 would then need a backfill after all. | never |

## Where things are

```
backend/src/main/java/com/fantasykai/
  ingest/          Phase 1 — nflverse + Sleeper pipeline (17 classes)
  scoring/         Phase 2 — ruleset model, validator, dot-product evaluator
backend/src/main/resources/db/migration/   Flyway. V1 schema, V2 ingestion support
backend/src/test/resources/nflverse/       Real 2024 rows as fixtures — not invented
docs/fantasy-platform-handoff.md           The spec
docs/perf/                                 Phase 3 baseline, Phase 6 results
scripts/                                   One-shot ingest + launchd plist
```

## Current state

| Phase | Status |
|---|---|
| 0 — Foundation | ✅ `921e21a`, `8fdad10`, `d06f133` |
| 1 — Ingestion | ✅ `6c591e5` — six-season backfill in 22.8s |
| 2 — Scoring engine | ⬅ **in progress** |
| 3 — Read API + k6 baseline | next |
| 4–7 | frontend · auth · perf pass · polish |

2026 season opens **Sept 10**. The 2026 schedule is loaded (272 games); nflverse has not published 2026 stat lines yet, so those runs correctly record `SKIPPED`.

## Measured numbers — do not re-derive or estimate these

From the loaded database, 2020–2025:

| | |
|---|---|
| `player_game_stats` rows stored | **112,319** (112,450 read; 131 dropped for blank `player_id`) |
| Stat rows, QB/RB/WR/TE | 36,567 |
| Distinct players, all positions | 4,061 |
| Distinct players, QB/RB/WR/TE | **1,243** across six seasons — **578–633 in any one season** |
| `snap_pct` coverage | 99.9% (112,245 / 112,319) |
| Sleeper ids attached | 883 of 25,065 players |
| Rows with fractional `def_sacks` | 1,660 |
| Backfill wall time | 22.8s |

**"1,243 skill players" is a six-season union.** No single season clears 700. Say "across six seasons" or the claim breaks the moment someone asks whether it is one year.

**Daily ingest volume:** ~28,500 records before week 1, rising to ~74,000 by week 18. ~25K of that is the player master. Say the real number and its composition, not "10K+".

## Traps in the source data — each of these cost real time

- **131 stat rows have a blank `player_id`.** They cannot resolve to a player and are dropped. This is why read ≠ stored.
- **`def_sacks` is fractional.** A shared sack credits 0.5; 1,660 rows are non-integer. The column is `NUMERIC(4,1)`. `SMALLINT` would round 4.5 to 5 and silently corrupt the data.
- **Nullable FK columns inside a `UNIQUE` constraint do not constrain.** Postgres treats NULLs as distinct, so `uq_games_matchup` admitted exact duplicate games until `home_team_id`/`away_team_id` were made `NOT NULL`. Proven by inserting a duplicate, not by reasoning.
- **`snap_counts.offense_pct` is a fraction (0.94), not a percent.** The column is a percentage; multiply by 100.
- **The Rams abbreviate as `LA`, not `LAR`,** in some nflverse files.
- **Sleeper pads `gsis_id` with a leading space:** `" 00-0035057"`. Trim before matching.
- **nflverse `players.csv` has no Sleeper id at all** — only espn, pfr, nfl, esb. The crosswalk has to come from Sleeper's own API, matching on gsis.
- **pgjdbc maps `smallint` to `Integer`, not `Short`.** Test assertions must use ints.
- **`JdbcTemplate` reads a jsonb `?` operator as a bind placeholder.** `external_ids ? 'pfr'` will not work; use `external_ids ->> 'pfr' IS NOT NULL`.
- **nflverse release assets 404 until published.** `AssetNotPublishedException` → `ingest_runs.status = 'SKIPPED'`. A future season must not fail the run.

## Commands

```bash
docker compose up -d                      # Postgres on :5433 (not 5432), Redis on :6379
cd backend && ./mvnw -B verify            # needs Docker — Testcontainers boots a real PG 16

# psql
docker compose exec postgres psql -U fantasykai -d fantasykai

# one-shot historical backfill
cd backend && ./mvnw spring-boot:run \
  -Dspring-boot.run.arguments=--fantasykai.ingest.backfill-on-startup=true

# one-shot current-season pull (what launchd runs daily)
./scripts/ingest-once.sh
```

**Postgres is on 5433** because a Homebrew `postgresql@16` launchd service owns 5432 on the dev Mac and wins the connection. Symptom when this bites: `role "fantasykai" does not exist`.

## Working agreement

**Measure before asserting. Prove a constraint by trying to violate it.**

This project exists to be defended out loud in an interview, so any type, constraint or number that was picked by default becomes something its owner has to justify. Before a migration, pull the real values rather than reasoning about what the type should be — that habit has already caught the fractional sacks and the NULL-defeated unique constraint. Before quoting a number, run the query. Fixing a 200-line schema is free; fixing it under 112K rows is not.

Report design gaps you are *not* fixing explicitly rather than staying quiet about them.
