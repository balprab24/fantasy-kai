# Measured numbers

**Moved verbatim from CLAUDE.md on 2026-09-24.** These are measurements, each with how it was taken —
the project's rule is to quote them rather than re-derive or estimate them, and to re-measure (with
a date) rather than edit a number by reasoning.

Live counts the docs claim (tests, endpoints, files, migrations, row totals) are re-derived at every
session start by `scripts/session-check.sh`; where this file and that check disagree, the check wins.
The k6 baseline's own home is [`perf/baseline.md`](perf/baseline.md); the table below is a copy of
its headline numbers.

## Measured numbers — do not re-derive or estimate these

From the loaded database, 2020–2026. **Re-measured 2026-09-21** after a four-day ingest outage was
found and the missing week refilled — the 2020–2025 rows below are unchanged and historical, the
2026 rows move every day the pull runs. **Week 2 is 15 games, not 16: the Monday night game had not
been played when this was measured.** That is the same "a week arrives in pieces" behaviour the
daily pull exists for, not a gap.

| | |
|---|---|
| `player_game_stats` rows stored | **112,319** (112,450 read; 131 dropped for blank `player_id`) |
| …plus 2026 week 1, first partial pull 2026-09-12 | **134** (135 read; 1 dropped, same blank-`player_id` cause) → **112,453** |
| …week 1 complete, 2026-09-14 | **1,040** (1,041 read; 1 dropped) → **113,359** total |
| …2026 weeks 1–2, re-measured **2026-09-21** | week 1 **1,117** / 16 games · week 2 **1,043** / 15 games → **114,479** total |
| Stat rows, QB/RB/WR/TE | 37,261 |
| Distinct players, all positions | 4,232 |
| Distinct players, QB/RB/WR/TE | **1,298** across seven seasons — **578–633 in any one 2020–2025 season** |
| `snap_pct` coverage | 99.9% (114,397 / 114,479) |
| Sleeper ids attached | 883 of 25,066 players |
| Rows with fractional `def_sacks` | 1,660 |
| Backfill wall time | 22.8s |

**nflverse `games.csv`, probed 2026-09-08** (7,548 rows, seasons 1999–2026). These are what V4's
column types are justified by — do not re-derive them either:

| | |
|---|---|
| Spreads / totals carrying a **half point** | **3,321** of 7,388 · 3,681 of 7,388 — never more than 1 dp |
| Most extreme moneyline in 27 seasons | **−5,000** — `SMALLINT` would hold it 6.5× over |
| `temp` range · rows with no reading | −6 … 109 · 2,342 |
| `wind` = 0 (calm, *not* missing), 2020–2026 | **29** |
| Distinct `roof` / `surface` values (longest) | 4 (`outdoors`, 8) / 8 (`matrixturf`, 10) |
| 2026 games with a line | **112 of 272** — they land ~a week ahead of kickoff |
| Cost of V4 to `games` | 19 → 30 heap pages, +88 kB; +11 buffers of ~2,640 in the rankings plan |

**"1,243 skill players" is a six-season union.** No single season clears 700. Say "across six seasons" or the claim breaks the moment someone asks whether it is one year.

**k6 baseline, 2026-09-07** (`docs/perf/baseline.md`, four 60s passes, one warm JVM):

| | 1 VU | 5 VUs | 10 VUs | 20 VUs |
|---|---|---|---|---|
| p50 | 12.38 ms | 18.11 ms | 34.71 ms | 71.92 ms |
| p95 | **21.41 ms** | 33.03 ms | 69.18 ms | **124.99 ms** |
| Throughput | 72.9 req/s | 246.4 req/s | 259.0 req/s | 256.4 req/s |
| JVM CPU (of 800%) | 16% | 59% | 74% | 73% |

**The bottleneck is Postgres, not the Java scorer.** Measured at 20 VUs:
**Postgres 486–587% CPU (~5.3 cores, 20.7 ms/req) against the JVM's 73%
(0.73 cores, 2.9 ms/req) — an 88/12 split.** It isn't disk (4,044 buffer hits,
zero reads). Throughput saturates at ~256 req/s from 10 VUs on while latency
doubles 10→20 — queueing at a resource at capacity. Phase 11's *order* survives
(a cache hit skips both), but the matview and index attack the dominant cost, so
they should be worth **more** than the original plan predicted. Machine had 1.7
of 8 cores free, so this is the endpoint, not the laptop.

**The thing to say out loud:** the concurrency curve proves the endpoint is
compute-bound but *cannot say whose compute*. That took a second measurement —
sampling both processes — and it contradicted the design. handoff §9 and §12 Q4
both now teach the corrected version; §9's original framing is kept visible as a
hypothesis that was tested and failed, which is a better story than one that was
assumed.

Secondary ceiling: Hikari is at Spring Boot's **default 10 connections** (no
config in `application.yml`); 10 × ~39 ms occupancy ≈ the 256 req/s observed.
Raising it without making the query cheaper moves the queue, it does not remove it.

**Daily ingest volume:** ~28,500 records before week 1, rising to ~74,000 by week 18. ~25K of that is the player master. Say the real number and its composition, not "10K+".

## Phase ledger — what each phase shipped, and the suite size at the time

| Phase | Status |
|---|---|
| 0 — Foundation | ✅ `921e21a`, `8fdad10`, `d06f133` |
| 1 — Ingestion | ✅ `6c591e5` — six-season backfill in 22.8s |
| 2 — Scoring engine | ✅ `com.fantasykai.scoring` + V3 presets, 47 tests |
| 3 — Read API | ✅ `ec5a8e9` — `com.fantasykai.api` + `.query`, 25 tests (72 in the suite) |
| 3.5 — k6 baseline | ✅ 1/5/10/20 VUs measured — p95 **21.4 ms → 125.0 ms**, throughput saturates at ~256 req/s. **The bottleneck is Postgres, not the Java scorer (88/12).** See above. |
| 4 — Vegas in the schema | ✅ `73a0842` — `V4` widens `games` by 10 columns; `GameIngestor` reads 18 of the source's 46 and generates its upsert from one ordered list. 8 new tests (80 in the suite) |
| 4.5 — pre-Phase-5 fixes | ✅ `f478233` daily ingest installed + freshness health + CSV header assertion · `5d5b8b4` canonical hash over the resolved form + decimal rounding. **102 in the suite** |
| 5a/5b — Auth + tenant isolation | ✅ `com.fantasykai.auth` (16 classes) + `V5`. Default-deny chain, Argon2id, HS256 JWT, rotating refresh with family revocation, Bucket4j on Redis. **130 in the suite** |
| 4.75 — toolchain recovery | ✅ 2026-09-12 — JDK 25 found, enforcer rule, jjwt/bucket4j/bcprov bumped, **the headless-context bug the suite could not see** fixed. **132 in the suite** |
| 5c — Web shell | ✅ Next.js 16 App Router, 21 `.ts`/`.tsx` files (23 under `frontend/src`). Landing, rankings, player detail, auth, ruleset builder. **Attribution footer shipped — owed since Phase 0.** Proved end to end in a browser: a user-built 6-point-passing-TD ruleset put Stafford at #1 with 442.4 where Half PPR had him 4th at 350.4 |
| 5d — Deploy | ✅ **2026-09-23 — live at `https://www.fantasykai.com`, API `https://api.fantasykai.com`.** Oracle Cloud Always Free A1 VM (2 OCPU / 12 GB, Chicago) running Postgres + Redis + backend + Caddy from `deploy/compose.prod.yml`; Vercel for the frontend; Porkbun domain, $11/yr — the only cost. **9 of 10 acceptance checks pass** (4b owed — needs a second real client); the production ingest **fired unattended at 06:00:00 ET on 2026-09-23**. Running the runbook for real found five bugs in it, all fixed. **Progress, findings F1–F13 and what's owed: top of [`DEPLOY-STEPS.md`](DEPLOY-STEPS.md)** |
| 5d.1 — security pass | ✅ 2026-09-21 — **Boot 3.5 went OSS-EOL on 2026-06-30 and nobody had checked.** Tomcat pinned to 10.1.59 over the parent's CVE-bearing 10.1.55; the auth rate limiter proved **forgeable at the application layer**; four-day ingest outage found and refilled. See "The EOL clock" in [`traps.md`](traps.md) |
| 6 — Projections · 7 — League import (ESPN + Sleeper) · 8 — Roster tools | |
| 9–11 | consensus board · iOS (Expo) · perf pass |

Full roadmap and the reasoning for the order: [`docs/north-star.md`](docs/north-star.md) §10.
