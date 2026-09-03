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
| Player universe | ~700–900 fantasy-relevant NFL players (QB/RB/WR/TE/K/DST), with team, position, status, bye week |
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
- `player_stats` — weekly per-player box score (~114 columns, one row per player-week)
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
       │  weekly pull (Tue 6am ET, in-season)
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
  id            SERIAL PK,
  abbr          VARCHAR(4) UNIQUE,   -- 'DET'
  name          VARCHAR(64),
  conference    VARCHAR(4),
  division      VARCHAR(16)
);

players (
  id            BIGSERIAL PK,
  gsis_id       VARCHAR(16) UNIQUE,  -- nflverse canonical, '00-0036322'
  external_ids  JSONB,               -- {"sleeper":"6794","espn":"4262921","pfr":"..."}
  full_name     VARCHAR(96) NOT NULL,
  position      VARCHAR(4)  NOT NULL,
  team_id       INT REFERENCES teams(id),
  status        VARCHAR(16),         -- ACT / INA / IR / PS
  updated_at    TIMESTAMPTZ
);

games (
  id            BIGSERIAL PK,
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

  PRIMARY KEY (player_id, game_id)
);

users (
  id             BIGSERIAL PK,
  email          CITEXT UNIQUE NOT NULL,
  password_hash  TEXT NOT NULL,        -- Argon2id
  created_at     TIMESTAMPTZ DEFAULT now()
);

scoring_profiles (
  id             BIGSERIAL PK,
  user_id        BIGINT REFERENCES users(id) ON DELETE CASCADE,  -- NULL = system preset
  name           VARCHAR(64) NOT NULL,
  rules          JSONB NOT NULL,
  is_preset      BOOLEAN DEFAULT FALSE,
  created_at     TIMESTAMPTZ DEFAULT now()
);

ingest_runs (                          -- observability + your "10K records" evidence
  id             BIGSERIAL PK,
  source         VARCHAR(32),          -- 'nflverse.player_stats'
  started_at     TIMESTAMPTZ,
  finished_at    TIMESTAMPTZ,
  rows_read      INT,
  rows_upserted  INT,
  status         VARCHAR(16),
  error          TEXT
);
```

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

## 9. Performance — how to actually earn the 40% bullet

**You cannot claim a 40% improvement you didn't measure.** Here is how you legitimately get the number.

### Step 1 — Build it slow and naive, on purpose
No indexes past the primary keys. No cache. Scoring computed per request. Ingest 2020–2026 (~150K+ stat rows).

### Step 2 — Establish the baseline
```bash
# k6, 20 virtual users, 60s, against GET /api/v1/rankings
k6 run --vus 20 --duration 60s rankings.js
```
Record **p50, p95, p99** and throughput. Run `EXPLAIN (ANALYZE, BUFFERS)` on the rankings query and save the plan. **Commit these numbers to `docs/perf/baseline.md`.** This file is the difference between a real bullet and a made-up one.

### Step 3 — Optimize, one change at a time, re-measuring after each

```sql
-- 1. Composite index matching the hot access pattern
CREATE INDEX idx_pgs_season_week_player
  ON player_game_stats (season, week, player_id) INCLUDE (rec, rec_yd, rec_td, rush_yd, rush_td, pass_yd, pass_td);

-- 2. Partial index for the common "current season only" case
CREATE INDEX idx_pgs_current
  ON player_game_stats (player_id, week) WHERE season = 2026;

-- 3. Pre-aggregate season totals; refresh after each ingest
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

Then the Redis layer:
```
Key:  rankings:v1:{sha256(rules)}:{season}:{position}:{scope}
TTL:  until next scheduled ingest (invalidate explicitly on ingest completion)
```
Hashing the *ruleset* rather than the profile ID means two users with identical custom settings share a cache entry. Mention that in the interview.

### Step 4 — Write it up
`docs/perf/results.md`: baseline → each change → delta → final. Whatever the real number is, **that** is your resume bullet. If it's 60%, say 60%. If it's 25%, say 25%. A defensible 25% beats an indefensible 40% every single time.

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

### Phase 0 — Foundation (week 1)
Repo, Docker Compose, Flyway schema, `ingest_runs`, health endpoint, GitHub Actions.
Nothing user-facing. Resist the urge to start on UI.

### Phase 1 — Ingestion (week 1–2)
nflverse CSV pull → parse → upsert. Backfill 2020–2025. Wire the weekly `@Scheduled` job (Tuesday 6am ET). Sleeper player-ID crosswalk.
**Season kicks off September 10 — get this running before week 1 so you have live data flowing all season.**
→ *Earns: "700+ NFL players" and "automated data pipelines processing 10K+ records daily."* Verify both against `ingest_runs` and a `COUNT(*)`. If the real numbers are different, change the resume.

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
Everything in §9. Indexes → matview → Redis, measuring after each.
→ *Earns: "Architected the relational schema and optimized indexed queries, reducing dashboard load times by X%."*

### Phase 7 — Polish
README with architecture diagram and the perf numbers, seeded demo account, deployed URL on the resume.

---

## 12. Interview defense — be able to answer these cold

1. Why store raw stats instead of precomputed fantasy points? *(Answer: N scoring systems × M players is unbounded; recomputation is cheap, storage of every permutation isn't. And a rule change would require a full backfill.)*
2. Walk me through what happens when a user changes their PPR setting. *(Cache key changes → miss → recompute from matview → cache under the new ruleset hash.)*
3. What was slow, what did you change, how did you measure it? *(Point at `docs/perf/`. Numbers, not adjectives.)*
4. Why the composite index in that column order? *(Selectivity and the access pattern of the rankings query. Show the EXPLAIN plans, before and after.)*
5. How do you keep user A from reading user B's scoring profiles? *(Repository-level `user_id` filter from the JWT subject, not a service-layer check.)*
6. What breaks if nflverse goes down mid-season? *(Last ingest persists; app serves stale data with a visible "last updated" timestamp; `ingest_runs` records the failure. Degraded, not down.)*
7. What would you do differently? *(Have a real answer ready. "Ingestion in Python for the projections work" is a good one.)*

---

## 13. Decide these before Phase 0

- [x] **Repo layout** — monorepo. `backend/` exists; `frontend/` lands in Phase 4.
- [x] **Project name** — `fantasy-kai`. Java package `com.fantasykai`.
- [x] **Backfill depth** — **2020–2025, six seasons (~800K stat rows).** Volume is the point: at ~250K rows Postgres scans fast enough that there is no headroom to demonstrate an improvement, and the §9 story collapses.
- [ ] **Resume date.** "July 2026 – Present" is not currently true. Either change it to September 2026, or ship Phase 0–2 fast enough that it becomes defensible. Do not leave it as-is.
- [x] **Attribution block** — written in the README in Phase 0. Site footer still owed in Phase 4.

---

## Appendix — Reference links

- nflverse data releases — https://github.com/nflverse/nflverse-data/releases
- nflreadr data dictionary — https://nflreadr.nflverse.com/
- Sleeper API docs — https://docs.sleeper.com/
- ESPN v3 endpoint notes (community gist) — https://gist.github.com/nntrn/ee26cb2a0716de0947a0a4e9a157bc1c
