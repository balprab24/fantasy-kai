# Orientation

**The plain-English door.** Everything else in `docs/` is written for someone who already knows
the vocabulary. This file assumes you don't, and explains rather than asserts.

It **owns no facts.** Same rule the [map](map.md) follows: where a number or a decision lives in
another document, this one links to it instead of repeating it. If you find a fact stated here
and nowhere else, that is a bug in this file.

---

## 1. What this actually is

A website that ranks NFL players for fantasy football — but with one difference from every other
one.

Fantasy leagues disagree about scoring. In one league a catch is worth 1 point; in another it's
worth half a point; in another it's worth nothing. A touchdown pass is 4 points in most leagues
and 6 in some. So "who is the best wide receiver" has no single answer — it depends on your
league's rules, and most sites either pick one set of rules or make you choose from a short menu.

This one computes the answer against **whatever rules you give it**, including rules you type in
yourself. Change one number in your league's settings and the whole board reorders, correctly,
in a few milliseconds.

## 2. The one idea, with the receipts

The trick is what **doesn't** get saved.

The obvious way to build this is to calculate each player's fantasy points and store them. That
works until someone asks for different scoring rules, at which point every stored number is
wrong and you need a second copy of the whole table — then a third, then a fourth.

So the database stores **only what happened**: Stafford threw for 253 yards and 2 touchdowns in
week 3, 2025. No points anywhere. Points are calculated when you ask, against the ruleset you
asked with.

Here is the same data, scored twice — this actually happened in a browser during Phase 5c:

| Ruleset | Where Stafford ranks | His total |
|---|---|---|
| Half PPR (a standard preset) | **4th** | 350.4 |
| A custom ruleset: 6 points per passing TD | **1st** | 442.4 |

Nothing in the database changed between those two rows. Both numbers were computed from the same
stat lines about a second apart. That is the entire product in one comparison.

Everything else in the architecture exists to protect this. The rule "never store a computed
point value" appears in [CLAUDE.md's invariant table](../CLAUDE.md) because the moment one gets
saved, the thing above stops being true.

## 3. What exists today, and what is a plan

The roadmap in [`north-star.md` §10](north-star.md) lists eleven phases. Six are built. The
distinction matters, because the docs describe planned work in the same confident voice as
finished work.

| You can actually do this today | Not built yet |
|---|---|
| Load 7 seasons of real NFL stats in ~23 seconds | Deploy it anywhere — it runs on this laptop only ([§5d](north-star.md)) |
| Ask for rankings under any of 4 preset rulesets, or your own | Project what a player *will* do (Phase 6) |
| See a player's week-by-week game log, scored | Import your real league from ESPN or Sleeper (Phase 7) |
| Create an account and save your own scoring rules | Lineup optimizer, trade calculator (Phase 8) |
| Use all of it from a web UI | A consensus board, an iOS app, the performance pass (9–11) |

The honest one-line summary: **the engine is real and measured; nothing is deployed, and nothing
predicts the future yet.**

## 4. How a request actually works

You open the rankings page and pick a ruleset. What happens:

1. The browser asks the API for rankings, passing the ruleset's id and the season.
2. The API checks the ruleset is one you're allowed to use — the presets, or one you own. It
   does that **in the database query**, not with an `if` statement afterwards, so there is no
   version of the code where the check gets skipped.
3. It loads the raw stat lines for that season — about 6,000 rows for 2025.
4. It scores **every one of them**, one at a time, in Java. There is no shortcut: you asked for
   the top 50, but you can't know which 50 are on top until everything is scored and sorted.
5. It adds up each player's weeks, sorts, takes your page, and rounds — **once, at the very
   end**. Rounding earlier would make a season total differ from the sum of its weeks.

Step 4 is deliberately the slow, naive version. Making it fast is Phase 11, and the whole point
of doing it last is that [the baseline was measured first](perf/baseline.md), so the improvement
will be a real before-and-after rather than a claim.

## 5. How to read a number in this repo

Numbers here come in three kinds and the docs are careful about which is which. You should be too.

- **Measured** — someone ran it and wrote down what came back. The 22.8-second backfill, the
  21.4 ms p95, the 112,453 rows. These are in
  [CLAUDE.md's "Measured numbers"](../CLAUDE.md) and [`perf/baseline.md`](perf/baseline.md)
  and are not to be re-derived from memory or estimated.
- **Claimed** — written in prose and true when written. File counts, test counts, "12
  endpoints". These rot. `./scripts/session-check.sh` re-derives them every session precisely
  because they rot; a `drift` row means a doc is now lying.
- **Conditional** — true only under stated conditions. `perf/baseline.md` still says "Temurin
  21.0.11" even though the project runs on 25, because that file records *what the measurement
  ran on*, not what the stack is today. A measurement file that quietly adopts the current
  stack stops being evidence.

When a number in a doc and a number from `session-check.sh` disagree, **the check wins** — it
ran the query, the doc remembered.

## 6. The five things most likely to be broken right now

All five are checked in about three seconds by `./scripts/session-check.sh`, which runs
automatically at the start of every session. These are what it is looking for and why:

| Symptom | What's really happening |
|---|---|
| The build dies with "release version 25 not supported" | `java_home -v 25` returns whatever JDK it *does* have and exits 0. It only looks in two directories. Check what `java -version` **prints**. |
| Rankings are empty | The app starts against an empty database. You have to load the data — see [README](../README.md). |
| `role "fantasykai" does not exist` | Something else owns port 5432. This project's Postgres is on **5433**. |
| The data is days out of date and nothing said so | The daily pull runs under launchd on a laptop that sleeps. It has stopped silently three times. The check reads `launchctl`'s exit code, which is ~36 hours louder than the freshness indicator. |
| The app won't start after you touched a migration | Flyway checksums every migration it has already applied. Editing one — even a comment — breaks local startup while CI stays green, because the test database always starts empty. The check recomputes all five checksums. |

## 7. Glossary

The terms the other docs use without introducing.

**Fantasy football terms**

- **PPR** — "points per reception". How much a catch is worth: 1 point (full PPR), 0.5 (half), or
  0 (standard). The single biggest difference between league rulesets.
- **TE premium** — a ruleset that pays tight ends more per catch than other positions, because
  otherwise nobody drafts them.
- **Ruleset** — one league's complete scoring rules. In this codebase it's a JSON document with a
  rate per stat, optional per-position overrides, and threshold bonuses ("+3 for 100 rushing
  yards").
- **Stat line** — what one player did in one game. 13 numbers this project can score.
- **ADP** — average draft position. Where a player actually gets drafted in real drafts; used as
  market consensus instead of pundit rankings.
- **Snap %** — the share of his team's offensive plays a player was on the field for. A usage
  signal: a player can score zero and still have been on the field all game.

**Database and backend**

- **Migration** — a numbered `.sql` file that changes the database's shape. They run in order and
  are never edited afterwards, so any database can be rebuilt from an empty one by replaying them.
- **Flyway** — the tool that runs them and records which have been applied, with a checksum of
  each so an edit is caught.
- **JdbcTemplate vs JPA/Hibernate** — two ways to talk to a database from Java. JPA maps Java
  objects to tables automatically; `JdbcTemplate` means you write the SQL. This project writes
  the SQL on purpose ([north-star §5a](north-star.md)).
- **Testcontainers** — starts a real, throwaway PostgreSQL in Docker for the test suite, so tests
  run against the actual database engine rather than a fake one. It's why Docker must be running
  to run tests, and why an edited migration passes CI: the test database always starts empty.
- **Seq scan** — the database reading every row of a table rather than using an index.
  Deliberate here until Phase 11.
- **Buffer hit** — the database found the data already in memory instead of reading the disk. In
  the baseline, every single read was a buffer hit, which is how we know the bottleneck isn't disk.
- **Materialized view ("matview")** — a saved, pre-computed answer to a query. A Phase 11 idea.
- **Dot product** — multiplying two lists position-by-position and summing. Scoring a stat line
  is exactly this: 13 stats × 13 rates. It is fast because it's an array walk, not a lookup per
  stat.
- **RFC 7807 / `problem+json`** — a standard shape for HTTP error responses, so every error from
  this API has the same fields instead of each endpoint inventing its own.
- **Actuator** — Spring's built-in status endpoints, e.g. `/actuator/health`.

**Auth**

- **Argon2id** — the algorithm that turns a password into something safe to store. Deliberately
  slow, so guessing is expensive.
- **JWT** — a signed token proving who you are. Short-lived here (15 minutes).
- **Access token vs refresh token** — the access token is the 15-minute one the browser sends
  with each request and holds only in memory. The refresh token is long-lived, lives in a cookie
  JavaScript cannot read, and exists only to obtain new access tokens.
- **Rotation and reuse detection** — every refresh issues a new refresh token and invalidates the
  old one. If an old one is ever presented again, that means someone copied it, so the whole
  family is revoked.
- **Bucket4j** — the rate limiter. Caps login attempts per IP per minute.

**Performance**

- **p50 / p95** — the median response time, and the time 95% of requests beat. p95 is the one
  that matters: it describes the bad-but-not-freak case.
- **VU** — "virtual user" in a load test. 20 VUs means 20 simulated people hammering the endpoint
  at once.
- **k6** — the load-testing tool. The script is `perf/rankings.js`.

**Operational**

- **launchd** — macOS's scheduler. Runs the daily data pull. Fires on *local* time, and on wake
  rather than at the scheduled hour if the laptop was asleep.
- **nflverse** — the open-source project the NFL data comes from, published as CSV files.
- **Sleeper** — a fantasy platform whose free API supplies player ids and roster rates.

---

## Where to go next

| You want | Go to |
|---|---|
| To run it | [`../README.md`](../README.md) |
| Where a specific class or file is | [`map.md`](map.md) |
| Why a decision was made | [`north-star.md`](north-star.md) (product) · [`fantasy-platform-handoff.md`](fantasy-platform-handoff.md) (engineering) |
| What bit someone already | [`../CLAUDE.md`](../CLAUDE.md), the traps section — every entry cost real time |
| What is true right now | `./scripts/session-check.sh` |
