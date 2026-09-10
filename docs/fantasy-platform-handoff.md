# Fantasy Football Stats Platform — Technical Handoff

**Owner:** Prabhnoor Bal
**Date:** September 3, 2026
**Status:** Design settled → ready to implement
**Stack:** Spring Boot 3.5 (Java 25) · PostgreSQL 16 · Redis · Next.js 15 / React / TypeScript

> **Scope moved on September 7, 2026.** This document remains the source of truth for the
> **engineering rationale** — §5 data model, §6 scoring engine, §8 security, §9 performance — and
> those sections are unchanged and still authoritative. **§1 (product definition) and §11 (build
> plan) are superseded** by [`north-star.md`](north-star.md), which widens the product to league
> import, projections, a trade calculator and an iOS app.
>
> **§0 is kept in full, but it is not uniformly still true — read it with this split in mind:**
>
> - §0 point 3 and the *"one thing you cannot honestly copy"* paragraph — don't pass someone else's
>   expert rankings off as consensus — are **still binding**, and are promoted to a hard invariant in
>   north-star §4. Deleting the warning would delete the argument for the invariant.
> - §0 point 2, *"You are not building Flock Fantasy"*, is the specific judgment the north star
>   **overturns**, deliberately. The reasoning behind it (that a funded team's surface area makes for
>   a broad, shallow solo project) has not been refuted — it has been accepted as a risk and answered
>   with sequencing: one narrow slice at a time, each usable on its own. If the project ends up broad
>   and shallow anyway, §0 was right and this banner is the receipt.
> - §0 point 1 (the resume framing) is unchanged and still the reason Phase 3.5 comes before
>   anything new.

---

## 0. The honest read before you write a line of code

Three things to internalize:

**1. Your resume bullets are currently a liability, not an asset.**
The bullets say "July 2026 – Present," "10K+ records daily," and "reducing dashboard load times by 40%." Fall recruiting starts now. If an interviewer asks *"what was slow, what did you change, how did you measure it"* and you can't answer with real numbers, the whole resume gets discounted — not just this line. The build plan below is sequenced so each phase **earns** a specific bullet with a real artifact behind it. Do not skip the measurement phase.

**2. You are not building Flock Fantasy.**
Flock has expert-panel consensus rankings, ADP, mock drafts, three-platform league sync, a trade calculator, tiering, and best ball tools. That is a funded team over multiple seasons. Trying to clone the surface area will get you a broad, shallow project that interviews badly.

**3. The one thing worth building is the scoring engine.**
Everything Flock does that matters flows from: *store raw stat lines, never fantasy points, and compute points on demand against an arbitrary league ruleset.* Full PPR, half PPR, standard, TE premium, and yardage bonuses stop being three code paths and become three rows in a table. That is a genuine architectural decision you can defend for twenty minutes in an interview. Build that deep, ship a thin UI on top of it, stop.

**One thing you cannot honestly copy:** Flock's rankings are a *consensus of human experts*. You don't have experts. Don't scrape someone else's rankings and call it consensus — it's a legal and ethical problem and it collapses the second an interviewer asks how it works. See §3 for what to build instead.

---

## 1. Product definition

> ⚠️ **Superseded by [`north-star.md`](north-star.md) §1, §2 and §9.** The v1 scope below is what
> Phases 0–3 actually shipped and is accurate as history. It is no longer the target: league sync,
> trade calculator and ADP moved from "explicitly out of scope" to the roadmap, and the free /
> logged-out access model below does not appear here at all. Kept for the reasoning, not the scope.


### v1 scope (ship this)

| Feature | Description |
|---|---|
| Player universe | **1,243 QB/RB/WR/TE across 2020–2025** (578–633 in any one season), with team, position, status, bye week. K and DST rows are stored but not scored in v1 — see §6. |
| Historical stat lines | Weekly raw box-score stats, 2020–2026 |
| Scoring profiles | Preset (Standard / Half PPR / Full PPR / TE Premium) + user-defined custom rulesets |
| Live rankings | Ranked player list recomputed against the selected profile — season, last-4-weeks, and per-game views |
| Player detail | Game log with fantasy points recalculated under the active profile |
| Auth | Register / login, so custom profiles persist per user |

### Explicitly out of scope for v1
League sync (ESPN/Sleeper/Yahoo import), mock drafts, trade calculator, ADP, DFS optimizer, mobile app, expert consensus.

### v2 candidates (only after v1 ships and is measured)
Projections model → tiering → Sleeper league import (read-only) → ownership/trend signals.

---

## 2. Data sources

### Primary: nflverse (use this as the backbone)

The community-maintained NFL data project. Published as CSV/Parquet to GitHub Releases at `github.com/nflverse/nflverse-data/releases`, refreshed weekly during the season, licensed **CC BY 4.0** — meaning you can legally redistribute it as long as you attribute. That license is the reason this is the right backbone and a scraper is not.

Datasets you need:
- **`stats_player_week`** (from the `stats_player` release) — weekly per-player box score, **150 columns, 18,983 rows for the 2024 season**. This is the backbone table.
  - Chosen over the older `player_stats` release (53 columns, **5,597 rows for 2024**, offense only) for two reasons: it carries the kicking and defensive columns that K/DST scoring needs, and six seasons of it is **112,450 rows** read / 112,319 stored versus ~34K — the volume §9 assumes. (Measured; CLAUDE.md pins both numbers and the 131-row gap.) You can always filter down to fantasy-relevant positions with a `WHERE`; you cannot widen the schema without a re-ingest.
  - Every row carries `game_id` (e.g. `2024_01_NYJ_SF`), so stat lines resolve to a game directly instead of joining on team + week.
  - **It also ships `fantasy_points` and `fantasy_points_ppr`. Do not store them.** The entire architecture is that points are computed on demand from raw stats; persisting the source's precomputed values would quietly reintroduce the thing you designed the system to avoid.
- `players` — player master with cross-platform IDs (gsis, espn, sleeper, pfr)
- `rosters_weekly` — team/status by week
- `schedules` — games, byes, opponents
- `snap_counts` — snap share (Flock shows SNAP% in that screenshot; this is where it comes from)

**Ingest pattern:** HTTP GET the release asset URL → parse CSV → upsert. No API key, no rate limit, no auth. Attribute nflverse in your footer and README.

### Secondary: Sleeper API

Free, read-only, no auth, documented at `docs.sleeper.com`. Free for **non-commercial use only** — fine for a portfolio project, note it in your README.

- `GET https://api.sleeper.app/v1/players/nfl?position=RB&active=true` — player universe. Use the filtered form; the unfiltered map is ~5MB. Call at most once daily.
- `GET https://api.sleeper.app/v1/players/nfl/trending/add?lookback_hours=24&limit=25` — waiver trends
- `GET https://api.sleeper.com/players/nfl/research/regular/2026/{week}` — league-wide ownership/roster percentages. **This is your best free proxy for a market-consensus signal.**

Rate limit: stay under 1000 calls/min or you risk an IP block. You'll be nowhere near it.

### Tertiary / optional: ESPN fantasy API

Undocumented. Base URL is `https://lm-api-reads.fantasy.espn.com/apis/v3/games/ffl/`. Filtering and result limits >50 require an `X-Fantasy-Filter` JSON header; some endpoints need a browser cookie.

**Risk:** ESPN has silently changed this API at least twice (a version bump in 2019, a base-URL move in April 2024), breaking every downstream library both times. It is not a dependency to build a data pipeline on.

**Recommendation:** you called this "my ESPN project," but do not make ESPN the backbone. Build on nflverse. Add ESPN later as an optional *league import* feature, isolated behind an interface, with the assumption that it will break.

### Source decision summary

| Source | Auth | License | Stability | Role |
|---|---|---|---|---|
| nflverse | none | CC BY 4.0 | High | **Backbone** — all stats, players, schedules |
| Sleeper | none | Free non-commercial | High | Player IDs, ownership, trends |
| ESPN v3 | cookie (partial) | Undocumented / gray | **Low** | Optional league import, v2+ |

---

## 3. What to build instead of "expert consensus"

Flock's rankings come from paid analysts. Yours will come from data. Three honest tiers, build in order:

1. **Performance rankings (v1).** Rank by actual fantasy points scored under the selected profile — total, per-game, and last-4-weeks. 100% your own computation, zero external dependency, immediately useful. Call it what it is: *Performance Rankings*, not consensus.
2. **Projection rankings (v2).** Simple, explainable model: weighted recent form (last 4 weeks > season average) × opportunity share (target share, snap %, carries) × opponent strength adjustment. Publish the formula in the UI. An explainable model you built beats an opaque one you scraped, both ethically and in interviews.
3. **Market signal (v2+).** Overlay Sleeper's ownership % as a "market vs. model" delta — surfaces buy-low/sell-high candidates. This is the differentiated feature and it's genuinely yours.

---

## 4. Architecture

```
┌──────────────────────────────────────────────────────────┐
│  Next.js 15 (App Router) + TypeScript + Tailwind         │
│  TanStack Query · TanStack Table (virtualized)           │
│  Deployed: Vercel                                        │
└───────────────────────┬──────────────────────────────────┘
                        │  HTTPS / JSON  (JWT bearer)
┌───────────────────────▼──────────────────────────────────┐
│  Spring Boot 3.5.16 (Java 25)                            │
│  ┌────────────┬─────────────┬──────────┬──────────────┐  │
│  │ Web layer  │  Scoring    │  Auth    │  Ingestion   │  │
│  │ (REST)     │  Engine     │ Security │  @Scheduled  │  │
│  └────────────┴─────────────┴──────────┴──────────────┘  │
│  Spring Data JPA · Flyway · Bucket4j · Micrometer        │
└──────┬─────────────────────────────────┬─────────────────┘
       │                                 │
┌──────▼──────────┐              ┌───────▼────────┐
│  PostgreSQL 16  │              │  Redis 7       │
│  raw stats      │              │  ranking cache │
│  matviews       │              │  rate limits   │
└─────────────────┘              └────────────────┘
       ▲
       │  daily pull (6am ET, in-season)
┌──────┴──────────────────────────────────────────┐
│  nflverse releases · Sleeper API                │
└─────────────────────────────────────────────────┘
```

### Key decisions and why

| Decision | Choice | Rationale |
|---|---|---|
| Backend language | Java 25 / **Spring Boot 3.5.16** | Matches your resume claim; you have to be able to defend it. Records, pattern matching, and virtual threads make this pleasant. **Moved 21 → 25 on 2026-09-10, and the reason is dates rather than features.** 25 is the current LTS (GA 2025-09-16, premier support to 2030); September 2026 closes the one-year overlap in which Oracle shipped JDK 21 updates under a permissive licence. Temurin's 21 builds stay GPL+CE, so this was a choice and not a forcing function — but a runtime bump is cheapest taken on a green suite rather than under pressure. Spring Boot 3.5.16 documents Java **17 up to and including 25**, so this sits at the top of the supported range rather than past it, and the upgrade cost exactly one property: no source file changed and all 130 tests passed on 25 unmodified. **3.3 is EOL — the line ended at 3.3.13 and Initializr no longer offers it.** 3.5.x is the last 3.x line and still patched; 4.x was available but its renamed starters, Hibernate 7 and Testcontainers 2 put you off the beaten path for the Phase 5 auth work. |
| Ingestion service | **Same Spring Boot app**, not a separate Python service | Python + pandas is genuinely better for this data, but two runtimes = two deploy targets = a whole extra failure surface for a solo project. nflverse ships plain CSV; parse it with Apache Commons CSV. Revisit if the model work in v2 demands pandas. |
| ORM | ~~Spring Data JPA for CRUD, native queries for the hot rankings path~~ → **`JdbcTemplate` throughout, zero `@Entity` classes.** Decided again in Phase 5 and kept: two persistence idioms for two small tables is worse than one | JPA is a bad fit for wide aggregate reads — and once every read is a hand-written query, the CRUD half never earns its second idiom. Cost, named rather than hidden: `ddl-auto: validate` has nothing to validate, so it guards only against becoming `update`. Don't fight it — drop to SQL where it matters. |
| Cache | Redis | The 40% story lives here. |
| Migrations | Flyway | Versioned schema from commit 1. Non-negotiable. |
| Frontend | Next.js 15 App Router | You already know it from Aurex. Don't learn two new things at once. |
| Auth | Spring Security + JWT | Different from Aurex's Clerk on purpose — this project's value is that you built the auth yourself. See §8. |
| Local Postgres port | **5433**, not 5432 | The dev Mac runs a Homebrew `postgresql@16` launchd service that already owns `localhost:5432` and wins the connection. The compose Postgres publishes on 5433; `POSTGRES_PORT` and `DB_URL` in `.env` override it on a machine where 5432 is free. |

---

## 5. Data model

```sql
teams (
  id            INT IDENTITY PK,
  abbr          VARCHAR(4) UNIQUE,   -- 'DET'
  name          VARCHAR(64),
  conference    VARCHAR(4),
  division      VARCHAR(16)
);

players (
  id            BIGINT IDENTITY PK,
  gsis_id       VARCHAR(16) UNIQUE,  -- nflverse canonical, '00-0036322'
  external_ids  JSONB,               -- {"sleeper":"6794","espn":"4262921","pfr":"..."}
  full_name     VARCHAR(96) NOT NULL,
  position      VARCHAR(4)  NOT NULL,
  team_id       INT REFERENCES teams(id),
  status        VARCHAR(16),         -- ACT / INA / IR / PS
  updated_at    TIMESTAMPTZ
);

games (                                 -- V1 + V2 + V4. 18 columns, not the 7 V1 shipped.
  id               BIGINT IDENTITY PK,
  season           SMALLINT NOT NULL,
  week             SMALLINT NOT NULL,
  season_type      VARCHAR(8),          -- REG / POST. Weeks run to 22; 19-22 are POST
  -- NOT NULL is load-bearing, and this block said "nullable" until 2026-09-08.
  -- Postgres treats NULLs as distinct inside a UNIQUE, so nullable team ids let
  -- uq_games_matchup admit exact duplicate games. Proved by inserting one.
  home_team_id     INT NOT NULL REFERENCES teams(id),
  away_team_id     INT NOT NULL REFERENCES teams(id),
  kickoff_at       TIMESTAMPTZ,
  nflverse_game_id VARCHAR(20) NOT NULL, -- V2. '2024_01_NYJ_SF'. The natural key the upsert conflicts on

  -- V4, the Vegas layer. Every type below is justified by a probe of the real
  -- file rather than by reasoning; north-star §10 records what each measured.
  home_score       SMALLINT,            -- nullable: 272 games unplayed. 0 is a real score
  away_score       SMALLINT,
  spread_line      NUMERIC(4,1),        -- 3,321 of 7,388 carry a half point. SMALLINT would corrupt every one
  total_line       NUMERIC(4,1),
  home_moneyline   INTEGER,             -- headroom, not overflow: the 27-season extreme is -5,000
  away_moneyline   INTEGER,
  roof             VARCHAR(12),
  surface          VARCHAR(16),
  temp             SMALLINT,            -- nullable: blank means not recorded, not "dome"
  wind             SMALLINT,            -- nullable: 0 is a real reading, 29 rows since 2020

  UNIQUE (season, week, home_team_id, away_team_id),
  UNIQUE (nflverse_game_id)
);
-- NOT stored, deliberately: result, total, over/under_odds, *_spread_odds.
-- total = home + away and result = home - away across all 7,276 played games
-- with zero exceptions, so both are the implied_team_total mistake one layer
-- down. The odds are the vig, not the line. See north-star §10.

-- The core table. Raw stats ONLY. Never store fantasy points here.
player_game_stats (
  player_id      BIGINT REFERENCES players(id),
  game_id        BIGINT REFERENCES games(id),
  season         SMALLINT NOT NULL,   -- denormalized on purpose; IntegrityChecks holds it true
  week           SMALLINT NOT NULL,
  team_id        INT REFERENCES teams(id),
  snap_pct       NUMERIC(5,2),

  pass_att       SMALLINT DEFAULT 0,
  pass_cmp       SMALLINT DEFAULT 0,
  pass_yd        SMALLINT DEFAULT 0,
  pass_td        SMALLINT DEFAULT 0,
  pass_int       SMALLINT DEFAULT 0,
  pass_2pt       SMALLINT DEFAULT 0,

  rush_att       SMALLINT DEFAULT 0,
  rush_yd        SMALLINT DEFAULT 0,
  rush_td        SMALLINT DEFAULT 0,
  rush_2pt       SMALLINT DEFAULT 0,

  targets        SMALLINT DEFAULT 0,
  rec            SMALLINT DEFAULT 0,
  rec_yd         SMALLINT DEFAULT 0,
  rec_td         SMALLINT DEFAULT 0,
  rec_2pt        SMALLINT DEFAULT 0,

  fum_lost       SMALLINT DEFAULT 0,
  ret_td         SMALLINT DEFAULT 0,

  -- returns
  punt_ret, punt_ret_yd, kick_ret, kick_ret_yd            SMALLINT DEFAULT 0,

  -- kicking (distance buckets stored raw; a 50-yarder scores differently
  -- from a 20-yarder and the bucket cannot be derived after the fact)
  fg_att, fg_made, fg_missed, fg_blocked, fg_long         SMALLINT DEFAULT 0,
  fg_made_0_19 .. fg_made_60_plus                         SMALLINT DEFAULT 0,
  pat_att, pat_made, pat_missed                           SMALLINT DEFAULT 0,

  -- individual defence (aggregates to a team DST line for every category
  -- except points/yards allowed -- see §6)
  def_sacks                                               NUMERIC(4,1) DEFAULT 0,  -- shared sacks are 0.5
  def_int, def_td, def_safety, def_fumbles_forced,
  def_fumble_rec, def_pass_defended, def_tackles_solo,
  def_tackle_assists, def_tackles_for_loss, def_qb_hits,
  def_blocked_kicks                                       SMALLINT DEFAULT 0,

  PRIMARY KEY (player_id, game_id)
);

users (
  id             BIGINT IDENTITY PK,
  email          CITEXT UNIQUE NOT NULL,
  password_hash  TEXT NOT NULL,        -- Argon2id
  created_at     TIMESTAMPTZ DEFAULT now()
);

scoring_profiles (
  id             BIGINT IDENTITY PK,
  user_id        BIGINT REFERENCES users(id) ON DELETE CASCADE,  -- NULL = system preset
  name           VARCHAR(64) NOT NULL,
  rules          JSONB NOT NULL,
  is_preset      BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMPTZ DEFAULT now()
);

ingest_runs (                          -- observability + your "10K records" evidence
  id             BIGINT IDENTITY PK,
  source         VARCHAR(32),          -- 'nflverse.player_stats'
  started_at     TIMESTAMPTZ,
  finished_at    TIMESTAMPTZ,
  rows_read      INT,
  rows_upserted  INT,
  status         VARCHAR(16),
  error          TEXT
);
```

**The migrations are the source of truth; the block above is a summary of all of them.** `V1__initial_schema.sql` alone no longer describes `games` or `players` — `V2` added the natural key and the Sleeper expression index, `V4` widened `games` by ten columns. Read `db/migration/` in order, not `V1` on its own; each column carries the measured range that chose its type.

**`ingest_runs` is not optional.** It is the table that lets you say "10K+ records daily" and then *show the row*. Build it in Phase 0.

---

## 6. The scoring engine (this is the project)

### Ruleset shape

```json
{
  "version": 1,
  "base": {
    "pass_yd": 0.04, "pass_td": 4, "pass_int": -2, "pass_2pt": 2,
    "rush_yd": 0.1,  "rush_td": 6, "rush_2pt": 2,
    "rec": 1.0, "rec_yd": 0.1, "rec_td": 6, "rec_2pt": 2,
    "fum_lost": -2, "ret_td": 6
  },
  "position_overrides": {
    "TE": { "rec": 1.5 }
  },
  "bonuses": [
    { "stat": "rush_yd", "gte": 100, "points": 3 },
    { "stat": "rec_yd",  "gte": 100, "points": 3 },
    { "stat": "pass_yd", "gte": 300, "points": 3 }
  ]
}
```

Standard = `rec: 0`. Half PPR = `rec: 0.5`. Full PPR = `rec: 1.0`. TE Premium = full PPR + the override block. Four presets, zero branching logic.

### Evaluation

```
points(statline, ruleset) =
    Σ over stats:  statline[stat] × effectiveRate(stat, statline.position, ruleset)
  + Σ over bonuses: statline[b.stat] >= b.gte ? b.points : 0

effectiveRate(stat, pos, rules) =
    rules.position_overrides[pos][stat]  ?? rules.base[stat]  ?? 0
```

### Implementation notes

- **Compute in Java for v1**, not in SQL. Position overrides and threshold bonuses get ugly fast in a query, and the Java version is trivially unit-testable. Benchmark it before you optimize it — that benchmark *is* your performance story.
- Represent a ruleset as a resolved `Map<Position, double[]>` at load time so scoring a row is one array dot-product, not a hash lookup per stat.
- Round to 2 decimals **once, at the API boundary.** Never mid-calculation.
- **Validate rulesets on write.** Unknown keys rejected, rates bounded to a sane range (e.g. −10..10), bonuses capped in count. An unvalidated JSONB column is an injection surface and a data-quality bomb.

### K and DST need rule shapes this ruleset doesn't have yet

The schema now carries kicking and defensive columns, so the data is there — but the ruleset format above cannot express how they are actually scored, and that is a Phase 2 design decision, not an oversight to paper over:

- **Kickers** score by distance bucket, not a flat per-FG rate. That needs either per-bucket keys (`fg_made_40_49: 4`) or a distance→points band list.
- **Team DST** scores on points allowed and yards allowed in *tiers* (0 allowed = 10 pts, 1–6 = 7, …). Neither is a per-stat multiplier, so both need a rule form the current `base` map has no room for.
- **Points and yards allowed are not player stats and are not in `stats_player_week`.** A team DST line has to be assembled from a team-level source (`schedules` gives the final scores). The per-player defensive columns give you sacks, INTs, fumble recoveries and defensive TDs by summing over a team-game; they do not give you the two tiered categories.

**Decision: v1 ships QB/RB/WR/TE only. K and DST are deferred to v2.**

The dot-product evaluator is the one clean architectural idea in this project. Neither a distance bucket nor a points-allowed tier is a per-stat multiplier, so bolting them into `base` means either a second scoring path or a rule shape general enough to be mush — trading the defensible idea for two positions nobody makes real roster decisions with. The columns are stored now (§5) so v2 needs no backfill; only the ruleset format has to grow.

**`"version": 1` is the extension point — that is what it is for.** When K/DST lands it becomes `"version": 2` with a `tiers` block alongside `base`, and the evaluator branches on the version field, not on the presence of keys. Profiles stored under version 1 keep evaluating against the v1 path unchanged, so no migration of user data is needed and no stored profile silently changes meaning. Write the version check into the evaluator in Phase 2 even though only one version exists — retrofitting it after users have saved profiles is where this gets expensive.

### Test strategy (do this, it's your credibility)

Three real 2025 lines, hand-computed under all four presets: **Josh Allen wk 1** (394 pass yd, 2 pass TD, 30 rush yd, 2 rush TD — no receptions, so all four presets must return the same 38.76), **Juwan Johnson wk 1** (a TE with 8 catches for 76, so the presets separate cleanly at 7.6 / 11.6 / 15.6 / 19.6 and the TE Premium override is visible), and **Derrick Henry wk 1** (169 rush yd, 2 TD, one fumble lost — the penalty and the 100-yard bonus boundary).

**But hand-computed expectations only test your arithmetic against your arithmetic.** The stronger check: nflverse ships `fantasy_points` and `fantasy_points_ppr`, and while §5 says never to *store* them, nothing stops you *checking against* them. 53 real 2025 lines — chosen to cover 2-point conversions of all three kinds, return TDs, multi-interception games, both flavours of lost fumble, 100- and 300-yard games, and scoreless lines — are scored under Standard and Full PPR and compared to the source's own numbers. That is an oracle you did not write, and *"I validated my evaluator against the source's precomputed column, then threw the column away"* is a much better answer to Q8 than the reasoning alone.

**One place we disagree with nflverse, on purpose.** Across all 19,422 rows of the 2025 file, our formula reproduces `fantasy_points` on 19,383 of them. The 39 that differ are all off by exactly +2.00, and every one has `fumbles_lost_total = 1` with all three offensive fumble columns at zero — they are returners who muffed a punt or kickoff. nflverse penalises only fumbles lost on offence; we ingest `fumbles_lost_total`, which includes return fumbles, because a real league penalises the roster player for any fumble they lose. Ours is the behaviour a fantasy platform wants.

The point is not that we found it — it is that the test **pins the difference exactly** rather than widening a tolerance until it passes. A tolerance loose enough to hide a two-point return fumble is loose enough to hide a bug, and the divergent rows have their own test so a future fixture refresh cannot quietly drop them.

Round-trip and drift are covered too: the four presets are built in Java for the pure tests and seeded by `V3__seed_scoring_presets.sql` for the app, and an integration test asserts both compile to the **same canonical hash** — so the migration and the code cannot drift apart silently. A final end-to-end test reads a stat row back out of Postgres by column name generated from `StatKey`, scores it, and asserts Ja'Marr Chase's 2024 week 10 line comes to 55.4 under full PPR.

---

## 7. API surface

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login              → { accessToken }  + httpOnly refresh cookie
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

GET    /api/v1/players?position=RB&team=DET&season=2026&page=0&size=50
GET    /api/v1/players/{id}
GET    /api/v1/players/{id}/gamelog?season=2026&profileId=3

GET    /api/v1/rankings
         ?profileId=3&position=RB&season=2026&scope=season|per_game|last4&page=0&size=50

GET    /api/v1/scoring-profiles        → presets + caller's own
POST   /api/v1/scoring-profiles
PUT    /api/v1/scoring-profiles/{id}
DELETE /api/v1/scoring-profiles/{id}

GET    /actuator/health
```

Conventions: cursor or offset pagination everywhere (never unbounded lists), RFC 7807 `application/problem+json` for errors, `/api/v1` prefix from day one.

---

## 8. Security

| Area | Requirement |
|---|---|
| Password storage | **Argon2id** (Spring Security `Argon2PasswordEncoder`). Not BCrypt, not SHA-anything. |
| Access token | JWT, 15-minute expiry, `Authorization: Bearer`. Signed HS256 with a secret from env — never committed, never in `application.yml`. |
| Refresh token | Opaque random 256-bit value, **hashed** in the DB, delivered as `HttpOnly; Secure; SameSite=Strict` cookie. **Rotate on every use**; detect reuse of a consumed token and revoke the whole family. |
| Tenant isolation | Every `scoring_profiles` read/write filters on the authenticated `user_id` from the token — **in the repository query, not the service layer**. This is the same invariant you enforced with Clerk `userId` in Aurex; carry the discipline over. |
| Authorization | Method-level `@PreAuthorize` on mutations. Never trust a client-supplied `userId` in a body or path. |
| Input validation | Bean Validation on every DTO. Ruleset JSON validated against an explicit allowlist of stat keys (§6). |
| SQL injection | JPA / parameterized native queries only. Zero string concatenation into SQL — including for the dynamic sort/filter params on `/rankings`; whitelist sortable columns by name. |
| Rate limiting | Bucket4j + Redis. Tight on `/auth/*` (e.g. 5/min/IP), looser on reads. |
| CORS | Explicit allowlist of your Vercel origin. Not `*`. |
| Transport | HTTPS only, HSTS on. |
| Dependencies | Dependabot on. `mvn dependency-check` in CI. |
| Secrets | `.env` gitignored, `.env.example` committed. Rotate anything you've ever pasted into a chat window. |

**Threat model worth writing down (interviewers love this):** the app is read-heavy and public-data-only, so the crown jewels aren't the stats — they're user credentials and custom rulesets. Auth surface and tenant isolation are where the effort goes; the stats endpoints can be public.

---

## 9. Performance — how to actually earn the bullet

**You cannot claim an improvement you didn't measure.** Here is how you legitimately get the number.

> ⚠️ **This section was written before the measurement and rewritten after it,
> on 2026-09-08.** Its original framing — that the bottleneck was Java
> recomputation — was tested at 1/5/10/20 VUs and did not survive. The framing
> below is the corrected one; the original is kept as history where it is
> useful, because a hypothesis that was tested and failed is a better story
> than one that was assumed and never checked. Numbers and method:
> [`docs/perf/baseline.md`](perf/baseline.md).

### Get the framing right first — and the original framing here was wrong

**What this section used to say:** the endpoint is CPU-bound rather than
I/O-bound, and the busy CPU is the Java scorer recomputing points for every
player on every request. Therefore cache, and treat the index and matview work
as supporting evidence.

**What the measurement said.** Half of that is right and it is the half that
matters least. The endpoint *is* CPU-bound and not disk-bound — 4,044 buffer
hits, zero disk reads, p95 climbing 21.4 → 125.0 ms from 1 to 20 VUs while
throughput flatlines at ~256 req/s. But the busy CPU is **Postgres, not the
scorer**: 5.3 cores against the JVM's 0.73 at 20 VUs, an **88/12 split**,
20.7 ms against 2.9 ms per request. Three sequential scans cost about seven
times the dot-product over the 6,037 rows that survive them.

The lesson is specific and worth keeping: **the concurrency curve says the cost
is compute, it does not say whose.** Answering that took a second measurement —
sampling both processes — and the second one contradicted the first one's
interpretation.

Six seasons of `stats_player_week` is **112,319 rows** (measured, not
estimated), and filtered to fantasy-relevant positions it is a good deal less.
The instinct that an index on a table this small is a shrug-worthy win turns out
to be wrong here, for a reason worth stating: the scan is not slow, it is
*repeated* — 256 times a second, three times per request. Small × often is the
whole cost.

So the ordering below survives but its reasoning inverts. **Cache still comes
first**, because a hit skips the scan and the scoring both, and it is the only
step that removes work rather than making it cheaper. The matview and the index
are no longer supporting evidence for a story about recomputation — they attack
the dominant cost directly, and should therefore be worth **more** than the rest
of this section predicts, not less.

> *"I expected the bottleneck to be my scoring code, measured it, and found it
> was 12% — Postgres was 88%. The concurrency curve told me it was compute-bound
> but not which compute; sampling both processes told me that, and it
> contradicted what I'd designed around."* That is the answer to give, and it is
> a better one than the original precisely because it describes a hypothesis
> that failed. See also §12 Q4.

### Step 1 — Build it slow and naive, on purpose
No indexes past the primary keys. No cache. Scoring computed per request. Backfill 2020–2025.

### Step 2 — Establish the baseline
```bash
# k6, 20 virtual users, 60s, against GET /api/v1/rankings
k6 run --vus 20 --duration 60s rankings.js
```
Record **p50, p95, p99** and throughput. Run `EXPLAIN (ANALYZE, BUFFERS)` on the rankings query and save the plan.

**Also record CPU utilisation during the run, and the p95 at 1 VU versus at 20.** That pair of numbers is the evidence that the endpoint is compute-bound rather than I/O-bound — a scan-bound endpoint degrades far less as you add concurrency.

**Then sample the database process and the JVM separately, because the curve above cannot tell them apart.** This is the step the original version of this section skipped, and skipping it is how the whole document came to assert the wrong bottleneck. "Compute-bound" is not a diagnosis until you know *whose* compute; the 88/12 split is the number that made it one.

**Commit all of it to `docs/perf/baseline.md`.** This file is the difference between a real bullet and a made-up one.

### Step 3 — Cache first, because that is where the time is

```
Key:  rankings:v1:{sha256(rules)}:{season}:{position}:{scope}
TTL:  until next scheduled ingest (invalidate explicitly on ingest completion)
```

Hashing the *ruleset* rather than the profile ID means two users with identical custom settings share a cache entry — and the four presets collapse to four entries no matter how many users you have. Mention that in the interview.

Re-measure. This is where the large delta should appear, and it should widen as you add virtual users.

### Step 4 — Then cut the work a cache miss has to do

```sql
-- Pre-aggregate season totals; refresh after each ingest
CREATE MATERIALIZED VIEW player_season_agg AS
SELECT player_id, season,
       COUNT(*) AS games_played,
       SUM(pass_yd) AS pass_yd, SUM(pass_td) AS pass_td, SUM(pass_int) AS pass_int,
       SUM(rush_yd) AS rush_yd, SUM(rush_td) AS rush_td,
       SUM(rec)     AS rec,     SUM(rec_yd) AS rec_yd, SUM(rec_td) AS rec_td,
       SUM(fum_lost) AS fum_lost, AVG(snap_pct) AS snap_pct
FROM player_game_stats GROUP BY player_id, season;

CREATE UNIQUE INDEX ON player_season_agg (player_id, season);
-- REFRESH MATERIALIZED VIEW CONCURRENTLY player_season_agg;  -- needs the unique index
```

Note what this actually buys, because it is easy to mis-explain: it collapses ~19K player-game rows per season into ~600 player-season rows. **That is a ~10× reduction in the number of stat lines the Java scorer has to touch on a miss** — it is mostly a compute win, not an I/O win. That is precisely why it belongs in this story rather than in a generic "I added a matview" bullet.

### Step 5 — Indexes last, and only where the plan says so

```sql
-- Composite index matching the hot access pattern
CREATE INDEX idx_pgs_season_week_player
  ON player_game_stats (season, week, player_id) INCLUDE (rec, rec_yd, rec_td, rush_yd, rush_td, pass_yd, pass_td);

-- Partial index for the common "current season only" case.
-- The season here MUST match the season perf/rankings.js pins, or the
-- benchmark cannot move: an index over a season with no stat lines in it
-- covers zero rows and shows zero delta. This said 2026 until 2026-09-08,
-- when 2026 had a loaded schedule and not one stat row.
CREATE INDEX idx_pgs_current
  ON player_game_stats (player_id, week) WHERE season = 2025;
```

Re-measure after each. Be honest in the write-up if the delta here is small — at this row count it may well be, and *"the index barely moved it, which is itself the evidence that the bottleneck was elsewhere"* is a stronger thing to be able to say than a number you inflated. Be ready to defend the column order from the EXPLAIN plans, before and after.

### Step 6 — Write it up
`docs/perf/results.md`: baseline → each change → delta → final, with the concurrency curve. Whatever the real number is, **that** is your resume bullet. If it's 60%, say 60%. If it's 25%, say 25%. A defensible 25% beats an indefensible 40% every single time.

---

## 10. Local setup and deployment

### Local (works identically on the Win11 PC and the M2 Mac)

`docker-compose.yml` for Postgres 16 + Redis 7 only; run Spring Boot and Next.js on the host for fast reload. **Postgres publishes on host port 5433** (see §4) — Redis stays on 6379. Both images have native arm64 builds, so the M2 needs no `platform:` override. Use Testcontainers for integration tests so CI matches local.

### Deployment

| Component | Target | Notes |
|---|---|---|
| Spring Boot | Railway or Fly.io | Both have usable free/hobby tiers; Fly gives you a region near Chicago |
| PostgreSQL | Neon | Generous free tier, branching is great for testing migrations |
| Redis | Upstash | Free tier, HTTP-friendly |
| Next.js | Vercel | You already know the flow |
| CI | GitHub Actions | Build + test + Flyway validate on every PR |

---

## 11. Build plan

> ⚠️ **Superseded by [`north-star.md`](north-star.md) §10 from Phase 4 onward.** Phases 0–3 keep
> their numbers and their commits. The new roadmap inserts **Phase 3.5: capture the k6 baseline**,
> which is the one item below that became unrecoverable if it slipped — it did not, it was captured
> 2026-09-07 and it disproved §9's premise.
>
> **This section's numbering is history, and it is kept as history rather than rewritten.** Use the
> table to decode it. The mistake to avoid is reading a bare "Phase 6" here or in an applied
> migration as the live Phase 6, which is Projections and has nothing to do with performance.
>
> | This document says | Live roadmap |
> |---|---|
> | Phase 4 — Frontend | folded into **Phase 5**, which ships its own UI slice |
> | Phase 5 — Auth + custom profiles | **Phase 5**, unchanged in substance |
> | Phase 6 — Performance pass | **Phase 11** |
> | Phase 7 — Polish | dissolved; each phase polishes its own slice |
>
> Two applied migrations carry the old number in a comment — `V1__initial_schema.sql` and
> `V2__ingestion_support.sql`, both saying "Phase 6" for the performance pass. **They stay wrong on
> purpose.** Flyway checksums an applied migration, so editing even a comment fails local startup
> with `Validate failed` while CI stays green, because Testcontainers always starts from an empty
> database. That was hit for real on V4. `V4`'s own "Phase 6" is *correct* — it means Projections.


Each phase names the resume bullet it earns. Do not write the bullet before the phase is done.

### Phase 0 — Foundation (week 1) · ✅ done
Repo, Docker Compose, Flyway schema, `ingest_runs`, health endpoint, GitHub Actions.
Nothing user-facing. Resist the urge to start on UI.
Commits `921e21a`, `8fdad10`, `d06f133`. The §9 baseline invariant is live and verified: `player_game_stats` carries exactly one index, its primary key.

### Phase 1 — Ingestion (week 1–2) · ✅ done
nflverse CSV pull → parse → upsert. Backfill 2020–2025. Wire the weekly `@Scheduled` job (Tuesday 6am ET). Sleeper player-ID crosswalk.
Add the GIN/expression index on `players.external_ids` here, not in the performance pass — it serves the ingestion crosswalk (`external_ids->>'sleeper'`), not the rankings query, so it does not contaminate the §9 baseline. Say so in the commit message.
**Season kicks off September 10 — get this running before week 1 so you have live data flowing all season.**
Commit `6c591e5`. Backfill of six seasons ran in **22.8s**; 12/12 tests green. `snap_pct` resolved on 99.9% of stored rows (112,245 / 112,319), 883 Sleeper ids attached, and the post-ingest integrity check reports **zero** stat rows whose denormalized season/week disagrees with the game they point at. The 2026 schedule is already loaded (272 games, first kickoff Sept 10); 2026 stat lines are not published yet and correctly record `SKIPPED`.
**Ingest every position, filter at query time.** v1 scores QB/RB/WR/TE only (§6), but storing only those rows would shrink `player_game_stats` from ~112K to ~37K and gut the §9 baseline — and K/DST in v2 would then need a backfill after all. The `WHERE` clause belongs in the rankings query, not the ingest.

**Measured against the loaded database, 2020–2025 (not estimated):**

| | per season | 6-season total |
|---|---|---|
| Stat rows read from source | 17,602 – 19,422 | 112,450 |
| Stat rows **stored** | 17,581 – 19,400 | **112,319** |
| Stat rows, QB/RB/WR/TE | 5,817 – 6,321 | 36,567 |
| Distinct players, all positions | 1,947 – 2,087 | **4,061** |
| Distinct players, QB/RB/WR/TE | **578 – 633** | **1,243** |

**Read and stored are different numbers, and the gap is the interesting part.** 131 stat lines across the six seasons carry a blank `player_id` and cannot be resolved to a player, so they are dropped — 21 in 2020, 22 in each season after. Quote **112,319**: it is the number you can run a `COUNT(*)` for in front of someone.

→ *Earns: "1,200+ NFL skill-position players across six seasons."* Note the framing: **no single season clears 700 skill-position players** — the number that beats 700 is the six-season union, which is exactly what the `players` table holds. Say "across six seasons" or the claim breaks the moment someone asks whether that is one year.

→ **Decision: the scheduled pull runs daily in-season, not weekly.** A Tuesday-only job moves ~19K rows *per week*, which does not support the word "daily" on a resume. Daily is defensible on its own merits anyway: nflverse revises the current week mid-week as corrections land, and the upsert is idempotent, so re-pulling costs nothing and a day-old number is the difference between a useful waiver view and a stale one. `cron: "0 0 6 * * *"` at `America/New_York`, no-op from March through August.

**What one daily run actually processes — say this, not "10K+":**

Measured from an actual run on 2026-09-04, six `ingest_runs` rows totalling **28,255 records read in 5.6s**:

| source | rows read per daily run |
|---|---|
| `nflverse.teams` | 36 |
| `nflverse.players` | 24,832 |
| `nflverse.schedules` (current season) | 272 |
| `nflverse.stats_player_week` | 0 pre-week-1 → ~1,100 after wk 1 → ~19,400 by wk 18 |
| `nflverse.snap_counts` | 0 pre-week-1 → ~26,500 by wk 18 |
| `sleeper.players` | 3,115 read, 883 matched |
| **total** | **28,255 before week 1 → ~74,000 by week 18** |

Be straight about the composition rather than hiding it: the player master is ~25K of that, and before week 1 it is most of it. *"Processes 28K+ records per daily run, rising to ~74K late in the season"* is both truer and stronger than "10K+ records daily", and every number in it is a row in `ingest_runs` you can point at. Before the season's first stat file is published, `stats_player_week` and `snap_counts` record `SKIPPED` rather than failing — also visible in `ingest_runs`, and the honest thing for it to do.

**A detail worth having ready, because it argues the case better than the reasoning does:** `players.csv` read 25,065 rows during the September 3 backfill and 24,832 the next day. The source revises published files. That is the concrete answer to *"why daily and not weekly"* — not "in case something changed", but "it changed overnight, here are the two `ingest_runs` rows."

**Running it.** The in-app `@Scheduled` job needs a long-lived JVM, which on a laptop that sleeps is not a schedule. `scripts/ingest-once.sh` runs one current-season pull and exits with the app's status code; `scripts/com.fantasykai.ingest.plist` is the launchd agent that fires it. Two caveats live in the script header: launchd uses the machine's local timezone rather than ET, and a sleeping Mac runs the job on wake rather than at 06:00. Both show up honestly as gaps in `ingest_runs`.

### Phase 2 — Scoring engine (week 2–3) · ✅ done
Ruleset model, validator, evaluator, four seeded presets, the verified-box-score test suite.
Pure backend. This is the deepest work in the project; give it the time.

`com.fantasykai.scoring` + `V3__seed_scoring_presets.sql`. 35 scoring tests, 47 in the suite.

`StatKey` is the one idea worth explaining out loud: a single enum that is simultaneously the validation allowlist, the array index for the dot-product, and the `player_game_stats` column name — so a ruleset cannot name a stat that does not exist, scoring never does a hash lookup per stat, and the Phase 3 query is generated from the enum rather than maintained beside it. `ResolvedRuleset.compile` branches on the rule-format version now, with only version 1 in existence, for the reason §6 gives. `Ruleset.canonicalHash()` exists early because it is a property of the model, not of the cache: it is tested here so the performance pass can rely on it.

### Phase 3 — Read API (week 3) · ✅ done, and the baseline captured 2026-09-07
`/players`, `/rankings`, `/gamelog`. Naive and unoptimized — **that's the point.** Capture the k6 baseline here.
→ *Earns: "Designed RESTful APIs."*

`com.fantasykai.api` + `com.fantasykai.query`. 25 new tests, 72 in the suite. `JdbcTemplate` throughout rather than the JPA/native split §4 imagines: there are no entities in this codebase, all four endpoints are read projections rather than CRUD, and entities land in Phase 5 where the writes are. `GET /scoring-profiles` ships early because `/rankings?profileId=` is undiscoverable without it.

**The load-bearing decision is that a ranking scores each game and then sums, rather than scoring a summed stat line.** Threshold bonuses make `ScoringEngine` non-linear — a 100-yard bonus belongs to a game — and §6 already requires the rounding to happen once at this boundary, so a season total is the rounded sum of weeks. It also protects Step 4 below: if Phase 3 pre-aggregated, the matview would have nothing left to collapse.

Rankings join `games` and filter `season_type = 'REG'`. The data runs to week 22; a season total that quietly folded in four playoff weeks would flatter players on deep teams, and `last4` would mean "the postseason". The game log deliberately does not filter — it is a record of what a player did.

**Baseline so far** (`docs/perf/baseline.md`): the 2025 rankings query is **three** sequential scans, not one — `player_game_stats` discards 92,919 of 112,319 rows and `players` discards 16,689 of 25,065, together touching 4,044 shared buffers (~31.6 MB) in **29.5 ms** warm, and handing **6,037 player-weeks** to the Java scorer to produce a ranking of 610. A single warm HTTP request is ~38 ms median. **The k6 passes were run on 2026-09-07** at 1/5/10/20 VUs, and they did not say what this section expected: the endpoint is compute-bound rather than disk-bound, but the busy CPU is Postgres at 88%, not the Java scorer. See the correction at the head of §9 and the full numbers in `docs/perf/baseline.md`.

**Two corrections to §9 Step 4, found while measuring.** First, the "~30× reduction" conflates populations: 19,400 rows/season is *all* positions while ~613 is *skill* players, and the rankings query reads 6,037 rows — the real reduction is **~10×**. Second, and more serious: pre-aggregating season totals and scoring them once pays a threshold bonus at most once per season instead of once per qualifying game. Every seeded preset is bonus-free so nothing is wrong today, but Phase 5 ships custom profiles and `RulesetValidator` allows up to 20 bonuses. **Phase 11** must gate the matview path on `bonuses().isEmpty()` or materialize per-game bonus counts.

### Phase 4 — Frontend (week 4–5) · superseded — this is now Phase 5's UI slice
Rankings table (virtualized — ~610 rows, the measured count), position filter tabs, profile switcher, player detail with game log. Tailwind, no component library beyond TanStack Table.

### Phase 5 — Auth + custom profiles (week 5–6)
Everything in §8. Custom ruleset builder UI.

### Phase 6 — Performance pass (week 6) · superseded — this is now **Phase 11**
Everything in §9, **in that order: cache → matview → indexes**, measuring after each. The ordering is the point — it follows the bottleneck instead of reaching for the reflex fix.
→ *Earns: "Profiled a compute-bound rankings endpoint and cut p95 latency by X% under 20 concurrent users by caching on a hash of the scoring ruleset."* Note what that bullet leads with: the diagnosis, then the number. Fill in X from `docs/perf/results.md` and nowhere else.

### Phase 7 — Polish · superseded — dissolved into each phase's own slice
README with architecture diagram and the perf numbers, seeded demo account, deployed URL on the resume.

---

## 12. Interview defense — be able to answer these cold

1. Why store raw stats instead of precomputed fantasy points? *(Answer: N scoring systems × M players is unbounded; recomputation is cheap, storage of every permutation isn't. And a rule change would require a full backfill.)*
2. Walk me through what happens when a user changes their PPR setting. *(Cache key changes → miss → recompute from matview → cache under the new ruleset hash.)*
3. What was slow, what did you change, how did you measure it? *(Point at `docs/perf/`. Numbers, not adjectives.)*
4. How did you know the bottleneck was recomputation and not the query? *(**It wasn't recomputation — that hypothesis was wrong and the measurement caught it.** The p95 curve from 1 to 20 VUs shows compute-bound rather than disk-bound: p95 21.4 → 125.0 ms while throughput flatlines at ~256 req/s, and `EXPLAIN` reports 4,044 buffer hits with zero disk reads. But the curve alone cannot say **which** compute, so I sampled both processes: Postgres 5.3 cores against the JVM's 0.73 — an 88/12 split — which puts the cost in three sequential scans, not the scorer. The honest version of this answer is that one measurement narrowed it, a second one located it, and the second contradicted what I expected. That is a better story than the one I planned to tell. See `docs/perf/baseline.md`.)*
5. Why the composite index in that column order? *(Selectivity and the access pattern of the rankings query. Show the EXPLAIN plans, before and after — and be willing to say the index moved p95 less than the cache did.)*
6. How do you keep user A from reading user B's scoring profiles? *(Repository-level `user_id` filter from the JWT subject, not a service-layer check.)*
7. What breaks if nflverse goes down mid-season? *(Last ingest persists; app serves stale data with a visible "last updated" timestamp; `ingest_runs` records the failure. Degraded, not down.)*
8. Why don't you store the `fantasy_points` column the source hands you? *(Because a stored point value is only correct for one ruleset. The source's is full-PPR; every other league would need its own copy, and a rule change would need a full backfill. See §5. Then the good part: I don't store it, but I do **test against** it — 53 real lines scored under my presets and compared to nflverse's own arithmetic. It agrees on all of them except a class of return fumbles I penalise deliberately and they don't, and that difference is pinned in a test rather than absorbed into a tolerance.)*
9. What would you do differently? *(Have a real answer ready. "Ingestion in Python for the projections work" is a good one.)*

---

## 13. Decide these before Phase 0

- [x] **Repo layout** — monorepo. `backend/` exists; `frontend/` lands in **Phase 5**, with auth.
- [x] **Project name** — `fantasy-kai`. Java package `com.fantasykai`.
- [x] **Backfill depth** — **2020–2025, six seasons.** Loaded: **112,319 stat rows** (112,450 read). The original ~800K estimate conflated play-by-play volume with weekly stat lines. The real figure still leaves the §9 story intact, and it is measured rather than assumed.
- [x] **Resume date** — **September 2026 – Present.** Phase 0 landed September 3, 2026 and the commit history proves it. "July 2026" was not true and there was nothing to gain by defending it.
- [x] **Attribution block** — written in the README in Phase 0. Site footer still owed, and it moved to **Phase 5** with the web shell (north-star §10).

---

## Appendix — Reference links

- nflverse data releases — https://github.com/nflverse/nflverse-data/releases
- nflreadr data dictionary — https://nflreadr.nflverse.com/
- Sleeper API docs — https://docs.sleeper.com/
- ESPN v3 endpoint notes (community gist) — https://gist.github.com/nntrn/ee26cb2a0716de0947a0a4e9a157bc1c
