# Learning log

**What the owner of this codebase has learned while building it — in their own words, graded
honestly.** The point is not a record of what the code does (that lives in the code and the other
docs). It is a record of what *I* can explain without notes, and what I cannot yet.

`/teach-feature` appends an entry at the end of every substantial feature: the six questions below,
answered briefly, then a quiz whose answers are graded after I reply. The tracker at the top is
updated each time.

## Concept tracker

Confidence: **—** not yet assessed · **1** can't explain it · **2** can explain it with the code open ·
**3** can explain it and defend the choice without notes.

| Concept | Confidence | Evidence (entry / quiz) | Where to study it here |
|---|---|---|---|
| Proxy trust — who decides a client's IP, and which headers cross the boundary | — | | `deploy/Caddyfile`, `auth/ForwardedHeaderConfig.java`, decision D-001 |
| Token lifecycle — access vs refresh, rotation, replay detection, `SameSite` | — | | `auth/RefreshTokenService.java`, `frontend/src/lib/api.ts` |
| Non-linear aggregation — why score each game, then sum | — | | `scoring/ScoringEngine.java`, `api/RankingsService.java` |
| Fail-fast vs partial success in a pipeline | — | | `ingest/IngestService.java` |
| Little's law — pool size, latency and throughput | — | | `docs/perf/baseline.md` |
| Idempotency and safe retries | — | | the upserts in `ingest/StatIngestor.java` |
| Defense in depth — proving each layer on its own | — | | PR #30, decision D-001 |
| Tests that pass for the wrong reason — shared state, test-first, mutation | — | | `AuthRateLimitTests.java` |

## The six questions (every entry answers them)

1. What did we build?
2. How does it work?
3. Why was it designed this way?
4. What could go wrong?
5. How do we know it works?
6. What did I learn?

---

## 2026-09-24 · Orientation quiz — awaiting answers

Asked at the end of the workflow-setup investigation, before any feature work. Answers are to be
given in my own words; grades and the missing pieces get recorded here.

1. Caddy throws away a client's `X-Forwarded-For`. Why wasn't that enough to protect the rate
   limiter — and what's the general rule about who gets to decide a client's IP?
2. You open `/rankings` in a fresh tab while signed in. Where does the access token come from, and
   why isn't it in `localStorage`?
3. Why score each game and then sum, instead of scoring a player's summed season? Give a stat line
   where the two answers differ.
4. When `schedules` fails, the pipeline aborts. Should `stats` run anyway? Argue both sides.
5. The pool has 10 connections and each request holds one for about 39 ms. What's the throughput
   ceiling, and why wouldn't raising the pool to 50 fix it?
6. The ingest uses upserts. Why does that make a retry safe — and what kind of write would make a
   retry unsafe?

*Answers and grades: pending.*
