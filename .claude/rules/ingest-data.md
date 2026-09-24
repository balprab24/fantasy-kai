---
paths:
  - "backend/src/main/java/com/fantasykai/ingest/**"
  - "backend/src/test/java/com/fantasykai/ingest/**"
  - "backend/src/test/resources/nflverse/**"
  - "scripts/ingest-once.sh"
---

# Source-data rules — nflverse and Sleeper

Loaded when Claude reads ingest code or its fixtures. The full stories are in `docs/traps.md` — grep
the quoted phrase. **Before changing a column type or a parser, pull the real values**: that habit is
what caught most of these.

- **A missing CSV column and a zero are the same thing to `CsvValues.shortValue`.** Headers are
  verified against the columns each ingestor reads; keep that list generated from the same
  `List<Field>` as the upsert. → "absent CSV column"
- **For a fact rather than a box score, blank must land as `NULL`** — use `CsvValues.shortOrNull`. A
  blank `temp` is not "dome", and `wind = 0` is a real reading. → "blank `temp`"
- **131 stat rows have a blank `player_id`** and are dropped, so read ≠ stored is expected. → "131 stat rows"
- **`def_sacks` is fractional** (a shared sack is 0.5): `NUMERIC(4,1)`, never `SMALLINT`. → "def_sacks"
- **`snap_counts.offense_pct` is a fraction (0.94)** — multiply by 100. → "offense_pct"
- **The Rams are `LA`, not `LAR`,** in some nflverse files. → "Rams"
- **Sleeper pads `gsis_id` with a leading space** — trim before matching. → "Sleeper pads"
- **nflverse `players.csv` has no Sleeper id** — the crosswalk comes from Sleeper's API, matched on gsis. → "no Sleeper id"
- **We score `fumbles_lost_total`, deliberately unlike nflverse's `fantasy_points`.** Do not "fix" it
  toward nflverse; `NflverseOracleTests` pins the difference. → "fumbles"
- **Weeks run to 22.** 19–22 are `POST`; rankings filter to `REG`, and the game log deliberately does not. → "Weeks run to 22"
- **`games.result` / `games.total` are derived on read, never stored** — like `implied_team_total`. → "result"
- **nflverse assets 404 until published** → `AssetNotPublishedException` → `SKIPPED`, never `FAILED`. → "404 until published"
- **Ingest every position and filter at query time** — never store only QB/RB/WR/TE. (CLAUDE.md invariant)
- **Known, not yet fixed (2026-09-24):**
  - nothing in the ingest retries
  - one failed source aborts every source after it (`IngestService.record`)
  - `PlayerIngestor`'s upsert replaces `external_ids` wholesale, wiping Sleeper ids until the
    crosswalk runs again

  These are the subject of the next task — check `docs/decisions.md` before changing them.
