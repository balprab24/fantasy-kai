# Decisions

**An append-only log of the decisions that shape this codebase.** Each new decision gets a full
entry: the context, the options, the choice, its consequences, and **when to revisit it**. That last
part is what lets a decision be reviewed later instead of merely remembered.

Decisions made before this file existed are **indexed here, not copied**. Their reasoning already
lives in the doc linked beside each one, and that doc stays the owner. If an index line and its
linked doc disagree, the linked doc wins and this index gets fixed.

## Index — decided before 2026-09-24

| # | Decision | Reasoning lives in |
|---|---|---|
| D-000a | Store raw stat lines and compute points on demand against a ruleset — never store `fantasy_points` | [CLAUDE.md](../CLAUDE.md) "The one idea" · [handoff §6](fantasy-platform-handoff.md#6-the-scoring-engine-this-is-the-project) |
| D-000b | One wide row per player-game, with one column per `StatKey` — not one row per stat | [handoff §5](fantasy-platform-handoff.md#5-data-model) |
| D-000c | Each of `StatKey` and `SignalKey` is at once the allowlist, the array index and the column name | [north-star §3.3](north-star.md#33-signalkey-mirrors-statkey) |
| D-000d | Score each game, then sum — never score summed stats | [handoff §6 Evaluation](fantasy-platform-handoff.md#evaluation) |
| D-000e | Score in Java, not SQL | `ScoringEngine.java` Javadoc · [handoff §6 Implementation notes](fantasy-platform-handoff.md#implementation-notes) |
| D-000f | `JdbcTemplate` with generated SQL; zero `@Entity` classes; `ddl-auto: validate` | [north-star 5a](north-star.md#5a--auth-backend) · [map.md Housekeeping](map.md#housekeeping) |
| D-000g | Flyway owns the schema from the first commit | [handoff §4](fantasy-platform-handoff.md#key-decisions-and-why) |
| D-000h | Ingest every position; filter at query time | [CLAUDE.md](../CLAUDE.md) invariants |
| D-000i | No index on `player_game_stats` past its primary key until Phase 11, to protect the baseline | [handoff §9](fantasy-platform-handoff.md#9-performance--how-to-actually-earn-the-bullet) · [perf/baseline.md](perf/baseline.md) |
| D-000j | Consensus means *market* consensus — no expert rankings, ever | [north-star §4](north-star.md#4-invariants--do-not-break-these) · [handoff §0](fantasy-platform-handoff.md#0-the-honest-read-before-you-write-a-line-of-code) |
| D-000k | An analytics layer over leagues hosted elsewhere: import them, never host them; value trades in expected wins | [north-star §1](north-star.md#1-what-this-is) · [§7](north-star.md#7-the-trade-calculator) · [§9](north-star.md#9-explicitly-not-building) |
| D-000l | JWT access token in memory, plus a rotating opaque refresh token in an `HttpOnly` `SameSite=Strict` cookie, with the whole family revoked on replay | [north-star 5a](north-star.md#5a--auth-backend) · [handoff §8](fantasy-platform-handoff.md#8-security) |
| D-000m | Ownership is enforced in the query; a profile you do not own is 404, not 403 | [.claude/rules/backend.md](../.claude/rules/backend.md) "Read API" |
| D-000n | A default-deny filter chain: reads are public, every mutation is authenticated | [north-star §2](north-star.md#2-who-sees-what) |
| D-000o | Oracle Always Free VM + Caddy + Vercel, on one registrable domain so `SameSite` works | [north-star 5d](north-star.md#5d--deployment) · [DEPLOY-STEPS.md](../DEPLOY-STEPS.md) |
| D-000p | `forward-headers-strategy: framework`, not `native` | [traps.md](traps.md) "A security header that is configured" |
| D-000q | Stay on Boot 3.5 and pin Tomcat 10.1.59 as a stopgap; Boot 4 is Phase 11.5 and overdue | [north-star §10](north-star.md#10-roadmap) row 11.5 |
| D-000r | The daily ingest runs inside the API's JVM in production (`@Scheduled`, 06:00 ET) | [deploy/compose.prod.yml](../deploy/compose.prod.yml) comments |

---

## D-001 · 2026-09-24 · Forwarded headers: an allowlist in the app, and a strip at the proxy

**Status:** accepted · PR [#30](https://github.com/balprab24/fantasy-kai/pull/30) (`a1b9257`, `fb3f096`) · **deployed: not yet** — see [DEPLOY-STEPS.md](../DEPLOY-STEPS.md) check 4c

**Context.** The auth rate limiter keys on the client address, which behind Caddy comes from the
forwarded headers. The documented model was "the app is forgeable; Caddy overwrites
`X-Forwarded-For`, so the deployment is safe". In fact Spring's `ForwardedHeaderFilter` believes
**seven** headers, Caddy overwrites **three**, and the limiter matched on the raw, still-encoded URI.
Three bypasses — `Forwarded`, `X-Forwarded-Prefix`, and a percent-encoded path — got through Caddy as
configured for production. Each was reproduced against a local copy of that stack.

**Options.**
- **A — Caddy strips the four untrusted headers.** One file, and it keeps "the proxy is the boundary".
  It does not fix the encoded path (proven), and a future CDN or proxy change silently reopens the
  header bypasses.
- **B — A, plus the app believes only the headers Caddy writes, plus the limiter matches paths the way
  routing does.** Two independent layers, about 100 lines plus tests.
- **C — `forward-headers-strategy: native` (Tomcat's `RemoteIpValve`).** Rejected in Phase 5d because
  its internal-proxies regex breaks quietly when the Docker network changes. Not reopened here.

**Decision.** B, chosen by the owner after seeing A and B side by side.

**Why — measured, not argued.** A new Caddyfile over the old backend closed the two header bypasses
but **not** the encoded path. The old Caddyfile over the new backend closed all three. Mutation
testing confirms each layer is pinned on its own:
- disable the allowlist → 4 of 12 limiter tests fail
- put the limiter back on the raw URI → 1 fails
- remove both (production as it is today) → 6 fail

An independent review attacked the fix with about 35 variants through the local stack and found no
bypass.

**Consequences.**
- `X-Forwarded-For` is still trusted by the app, and overwriting it is still Caddy's job; a test pins
  that.
- Boot's own `ForwardedHeaderFilter` registration is replaced. A test asserts exactly one is
  registered, and the replacement carries over Boot's `use-relative-redirects` behaviour.
- A proxy that legitimately writes `X-Forwarded-Prefix` or `Forwarded` now needs a deliberate change
  to `ForwardedHeaderConfig.WRITTEN_BY_PROXY`.

**The argument against, from the review.** This enumerates headers instead of deciding who the peer
is. Trusting forwarded headers only from Caddy's address (option C, done carefully) would reject
forgeries whatever proxy config sits in front — at the cost C was originally rejected for.

**Revisit when:**
- a CDN or a second proxy goes in front of Caddy
- `trusted_proxies` is ever set
- the backend ever gets a published port
- Spring Boot 4 (Phase 11.5) changes forwarded-header handling or Boot's registration
- IPv6 traffic grows: per-IP keying lets one /64 rotate through buckets
