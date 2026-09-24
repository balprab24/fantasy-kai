---
paths:
  - "backend/src/main/resources/db/migration/**"
---

# Schema rules — Flyway and Postgres

Loaded when Claude reads a migration. The full stories are in `docs/traps.md` — grep the quoted
phrase. **Before choosing a type, query the real values**: `def_sacks` and the moneylines were both
decided by measurement, not by reasoning.

- **Never edit an applied migration** — not even a comment. Flyway checksums it, local startup fails,
  and CI stays green because Testcontainers starts from an empty database. In production the backend
  would refuse to start. Add a new `V<n>__*.sql` instead. → "Editing an applied migration"
- **A nullable column inside a `UNIQUE` constraint does not constrain** — Postgres treats NULLs as
  distinct. Make the columns `NOT NULL`, and prove it by inserting a duplicate. → "Nullable FK columns"
- **`player_game_stats` carries no index past its primary key** until Phase 11; an early index
  destroys the measured baseline. (CLAUDE.md invariant)
- **Never add a column that stores a computed point value** — `fantasy_points`, a projected total, a
  blended rank. (CLAUDE.md invariant)
- **Half points need `NUMERIC(4,1)`** (`def_sacks`, spreads, totals) — `SMALLINT` silently rounds them. → "def_sacks"
- **A backfill leaves dead tuples** — run `VACUUM (FULL, ANALYZE)` before comparing against
  `docs/perf/baseline.md`. → "dead tuples"
- **`ddl-auto` stays `validate`, forever.** There are zero `@Entity` classes, so the real guarantee is
  that it can never become `update`.
