# Fantasy Football Stats Platform — Technical Handoff

**Owner:** Prabhnoor Bal
**Date:** September 3, 2026
**Status:** Design settled → ready to implement
**Stack:** Spring Boot 3.5 (Java 21) · PostgreSQL 16 · Redis · Next.js 15 / React / TypeScript

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
  - Chosen over the older `player_stats` release (53 columns, **5,597 rows for 2024**, offense only) for two reasons: it carries the kicking and defensive columns that K/DST scoring needs, and six seasons of it is ~114K rows versus ~34K — the volume §9 assumes. You can always filter down to fantasy-relevant positions with a `WHERE`; you cannot widen the schema without a re-ingest.
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
│  Spring Boot 3.5.16 (Java 21)                            │
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
| Backend language | Java 21 / **Spring Boot 3.5.16** | Matches your resume claim; you have to be able to defend it. Records, pattern matching, and virtual threads make this pleasant. **3.3 is EOL — the line ended at 3.3.13 and Initializr no longer offers it.** 3.5.x is the last 3.x line and still patched; 4.x was available but its renamed starters, Hibernate 7 and Testcontainers 2 put you off the beaten path for the Phase 5 auth work. |
| Ingestion service | **Same Spring Boot app**, not a separate Python service | Python + pandas is genuinely better for this data, but two runtimes = two deploy targets = a whole extra failure surface for a solo project. nflverse ships plain CSV; parse it with Apache Commons CSV. Revisit if the model work in v2 demands pandas. |
| ORM | Spring Data JPA for CRUD, **native queries for the hot rankings path** | JPA is a bad fit for wide aggregate reads. Don't fight it — drop to SQL where it matters. |
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

games (
  id            BIGINT IDENTITY PK,
  season        SMALLINT NOT NULL,
  week          SMALLINT NOT NULL,
  season_type   VARCHAR(8),          -- REG / POST
  home_team_id  INT REFERENCES teams(id),
  away_team_id  INT REFERENCES teams(id),
  kickoff_at    TIMESTAMPTZ,
  UNIQUE (season, week, home_team_id, away_team_id)
);

-- The core table. Raw stats ONLY. Never store fantasy points here.
player_game_stats (
  player_id      BIGINT REFERENCES players(id),
  game_id        BIGINT REFERENCES games(id),
  season         SMALLINT NOT NULL,   -- denormalized on purpose, see §9
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

`backend/src/main/resources/db/migration/V1__initial_schema.sql` is the source of truth; the block above is a summary.

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

Pull three real 2025 game lines you can verify against a public box score. Assert exact expected points under all four presets. If Ja'Marr Chase's Week 3 line doesn't come out to the right number under full PPR, nothing downstream is trustworthy. These tests are also what you show an interviewer.

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

### Get the framing right first: the bottleneck is recomputation, not I/O

Six seasons of `stats_player_week` is ~114K rows, and filtered to fantasy-relevant positions it is a good deal less. Postgres seq-scans that in tens of milliseconds. If your headline is *"I added a composite index and went from 60ms to 10ms"*, an interviewer can reasonably shrug — that is a small absolute win on a small table, and they will know it.

The real cost in `/rankings` is that **every request recomputes fantasy points in Java for every player under the caller's ruleset, and then sorts.** That work is CPU-bound, a faster scan does not touch it, and unlike the scan it **scales with concurrency** — at 20 virtual users you are doing the same computation twenty times over. The fix is not to read the rows faster. It is to stop recomputing them.

So the headline is **the ruleset-hashed cache.** The index and matview work is real and it stays in, but it is supporting evidence rather than the story.

> *"I profiled it, found the bottleneck was recomputation rather than I/O, and cached on a hash of the ruleset so that two users with identical league settings share an entry"* is a far better answer than *"I added a composite index."* The first is a diagnosis. The second is a reflex.

### Step 1 — Build it slow and naive, on purpose
No indexes past the primary keys. No cache. Scoring computed per request. Backfill 2020–2025.

### Step 2 — Establish the baseline
```bash
# k6, 20 virtual users, 60s, against GET /api/v1/rankings
k6 run --vus 20 --duration 60s rankings.js
```
Record **p50, p95, p99** and throughput. Run `EXPLAIN (ANALYZE, BUFFERS)` on the rankings query and save the plan.

**Also record CPU utilisation during the run, and the p95 at 1 VU versus at 20.** That pair of numbers is the evidence that the endpoint is compute-bound rather than I/O-bound — a scan-bound endpoint degrades far less as you add concurrency. It is what justifies going to the cache first instead of reaching for an index, and it is the measurement that makes the diagnosis credible instead of asserted.

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

Note what this actually buys, because it is easy to mis-explain: it collapses ~19K player-game rows per season into ~600 player-season rows. **That is a ~30× reduction in the number of stat lines the Java scorer has to touch on a miss** — it is mostly a compute win, not an I/O win. That is precisely why it belongs in this story rather than in a generic "I added a matview" bullet.

### Step 5 — Indexes last, and only where the plan says so

```sql
-- Composite index matching the hot access pattern
CREATE INDEX idx_pgs_season_week_player
  ON player_game_stats (season, week, player_id) INCLUDE (rec, rec_yd, rec_td, rush_yd, rush_td, pass_yd, pass_td);

-- Partial index for the common "current season only" case
CREATE INDEX idx_pgs_current
  ON player_game_stats (player_id, week) WHERE season = 2026;
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

Each phase names the resume bullet it earns. Do not write the bullet before the phase is done.

### Phase 0 — Foundation (week 1) · ✅ done
Repo, Docker Compose, Flyway schema, `ingest_runs`, health endpoint, GitHub Actions.
Nothing user-facing. Resist the urge to start on UI.
Commits `921e21a`, `8fdad10`, `d06f133`. The §9 baseline invariant is live and verified: `player_game_stats` carries exactly one index, its primary key.

### Phase 1 — Ingestion (week 1–2) · ✅ done
nflverse CSV pull → parse → upsert. Backfill 2020–2025. Wire the weekly `@Scheduled` job (Tuesday 6am ET). Sleeper player-ID crosswalk.
Add the GIN/expression index on `players.external_ids` here, not in Phase 6 — it serves the ingestion crosswalk (`external_ids->>'sleeper'`), not the rankings query, so it does not contaminate the §9 baseline. Say so in the commit message.
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

### Phase 2 — Scoring engine (week 2–3)
Ruleset model, validator, evaluator, four seeded presets, the verified-box-score test suite.
Pure backend. This is the deepest work in the project; give it the time.

### Phase 3 — Read API (week 3)
`/players`, `/rankings`, `/gamelog`. Naive and unoptimized — **that's the point.** Capture the k6 baseline here.
→ *Earns: "Designed RESTful APIs."*

### Phase 4 — Frontend (week 4–5)
Rankings table (virtualized — 900 rows), position filter tabs, profile switcher, player detail with game log. Tailwind, no component library beyond TanStack Table.

### Phase 5 — Auth + custom profiles (week 5–6)
Everything in §8. Custom ruleset builder UI.

### Phase 6 — Performance pass (week 6)
Everything in §9, **in that order: cache → matview → indexes**, measuring after each. The ordering is the point — it follows the bottleneck instead of reaching for the reflex fix.
→ *Earns: "Profiled a compute-bound rankings endpoint and cut p95 latency by X% under 20 concurrent users by caching on a hash of the scoring ruleset."* Note what that bullet leads with: the diagnosis, then the number. Fill in X from `docs/perf/results.md` and nowhere else.

### Phase 7 — Polish
README with architecture diagram and the perf numbers, seeded demo account, deployed URL on the resume.

---

## 12. Interview defense — be able to answer these cold

1. Why store raw stats instead of precomputed fantasy points? *(Answer: N scoring systems × M players is unbounded; recomputation is cheap, storage of every permutation isn't. And a rule change would require a full backfill.)*
2. Walk me through what happens when a user changes their PPR setting. *(Cache key changes → miss → recompute from matview → cache under the new ruleset hash.)*
3. What was slow, what did you change, how did you measure it? *(Point at `docs/perf/`. Numbers, not adjectives.)*
4. How did you know the bottleneck was recomputation and not the query? *(The p95 curve from 1 VU to 20 VUs, plus CPU utilisation during the run. A scan-bound endpoint degrades far less under concurrency. This is the single best question in this list — it is the one that separates a diagnosis from a reflex.)*
5. Why the composite index in that column order? *(Selectivity and the access pattern of the rankings query. Show the EXPLAIN plans, before and after — and be willing to say the index moved p95 less than the cache did.)*
6. How do you keep user A from reading user B's scoring profiles? *(Repository-level `user_id` filter from the JWT subject, not a service-layer check.)*
7. What breaks if nflverse goes down mid-season? *(Last ingest persists; app serves stale data with a visible "last updated" timestamp; `ingest_runs` records the failure. Degraded, not down.)*
8. Why don't you store the `fantasy_points` column the source hands you? *(Because a stored point value is only correct for one ruleset. The source's is full-PPR; every other league would need its own copy, and a rule change would need a full backfill. See §5.)*
9. What would you do differently? *(Have a real answer ready. "Ingestion in Python for the projections work" is a good one.)*

---

## 13. Decide these before Phase 0

- [x] **Repo layout** — monorepo. `backend/` exists; `frontend/` lands in Phase 4.
- [x] **Project name** — `fantasy-kai`. Java package `com.fantasykai`.
- [x] **Backfill depth** — **2020–2025, six seasons.** Loaded: **112,319 stat rows** (112,450 read). The original ~800K estimate conflated play-by-play volume with weekly stat lines. The real figure still leaves the §9 story intact, and it is measured rather than assumed.
- [x] **Resume date** — **September 2026 – Present.** Phase 0 landed September 3, 2026 and the commit history proves it. "July 2026" was not true and there was nothing to gain by defending it.
- [x] **Attribution block** — written in the README in Phase 0. Site footer still owed in Phase 4.

---

## Appendix — Reference links

- nflverse data releases — https://github.com/nflverse/nflverse-data/releases
- nflreadr data dictionary — https://nflreadr.nflverse.com/
- Sleeper API docs — https://docs.sleeper.com/
- ESPN v3 endpoint notes (community gist) — https://gist.github.com/nntrn/ee26cb2a0716de0947a0a4e9a157bc1c
