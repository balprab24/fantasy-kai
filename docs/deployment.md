# Deployment

Fantasy Kai has been live since 2026-09-23: the site at **https://www.fantasykai.com** and the API
at **https://api.fantasykai.com**. This document covers how the production stack fits together, how
the deployment was verified, what went wrong along the way, and the known gaps. The step-by-step
runbook (setup, redeploy, rollback) is [`deploy/README.md`](../deploy/README.md).

## Summary

The backend, PostgreSQL, Redis and a TLS proxy run as one Docker Compose stack on an Oracle Cloud
Always Free ARM VM. The Next.js frontend is on Vercel. The domain is the only recurring cost.

The constraint that shaped the design was **one registrable domain**. Sign-in uses a
`SameSite=Strict` refresh cookie, and browsers decide "same site" by registrable domain, not by
origin. A frontend on `*.vercel.app` calling an API on another host would therefore drop the cookie
and sign users out on every reload, and `localhost` hides that completely because SameSite ignores
ports. Serving the site from `www.fantasykai.com` and the API from `api.fantasykai.com` fixes it
without weakening the cookie. That was proven in a real browser: register, sign in, hard-reload,
still signed in.

The reason to deploy at all was the data pipeline. The daily stats pull only runs inside a live
JVM, and on a development laptop that sleeps it had stopped silently several times. In production
it runs at 06:00 ET every day, and its first unattended run succeeded.

## How it fits together

```
                 ┌──────────────── Vercel ────────────────┐
 browser ──────► │ www.fantasykai.com   Next.js 16         │
    │            └────────────────────────────────────────┘
    │  fetch + HttpOnly refresh cookie (same site: fantasykai.com)
    ▼
 ┌──────────────────── Oracle Cloud Always Free VM (ARM) ───────────────────────────┐
 │  Caddy :80/:443  ── TLS (Let's Encrypt), HSTS, replaces X-Forwarded-For          │
 │     │                                                                            │
 │     ▼  (compose network only, no published port)                                 │
 │  Spring Boot 3.5 / Java 25   ── scoring, auth, @Scheduled 06:00 ET ingest        │
 │     │                  │                                                         │
 │  PostgreSQL 16       Redis 7        both bound to localhost on the VM,           │
 │                      (rate limits)  reachable only through an SSH tunnel         │
 └──────────────────────────────────────────────────────────────────────────────────┘
 Two firewalls in front: the cloud security list and the VM's own iptables.
```

## Verifying it by trying to break it

Each acceptance check is an attack or a failure tried against the live site, with the result that
came back. Most of them are automated in [`deploy/acceptance.sh`](../deploy/acceptance.sh).

| # | What was tried | What happened | ✓ |
|---|---|---|---|
| 1 | `GET /actuator/health/liveness` | `200 {"status":"UP"}` | ✅ |
| 2 | Read the health endpoint anonymously | status only, no internals leaked | ✅ |
| 3 | Register, sign in, **hard-reload** in a real browser | still signed in; `refresh` returns **200** | ✅ |
| 4a | Six logins, each with a **different forged `X-Forwarded-For`** | `401 ×5`, then **`429`**: Caddy discards the forged header, so all six share one bucket | ✅ |
| 4b | With that bucket full, one login from a **different real client** | not yet run (needs a second network) | ⬜ |
| 4c | With that bucket full: `Forwarded: for=<forged>`, `X-Forwarded-Prefix: /x`, and the path `/api/v1/%61uth/login` | **`429` ×3** on production. Each was `401`, a bypass, on a local copy of the stack before the fix | ✅ |
| 5 | Call the API from `Origin: evil.example` and from `*.vercel.app` | **403** both; the real origin gets `allow-origin` + `allow-credentials` | ✅ |
| 6 | Plain `http://` · inspect HTTPS headers | **308** to https · `strict-transport-security` **actually sent** (it was configured but silently missing before the deploy) | ✅ |
| 7 | Port-scan 5432 / 6379 / 8080 from the internet | all **time out**, dropped before they reach the VM | ✅ |
| 8 | Run the ingest on the VM, then wait for 06:00 ET | sources all SUCCESS, then an **unattended** run at 06:00 ET, all sources SUCCESS | ✅ |
| 9 | Top 10 for all four scoring presets, production vs. local | **identical**: same players, same order, same points | ✅ |
| 10 | Read `/rankings` and `/scoring-profiles` with **no token** | **`401` both**, `application/problem+json` | ✅ |

## What broke, and what it taught

Every finding below cost real time, and each is written into the runbook so it cannot cost it twice.

### Cloud and infrastructure
- **F1: changing the availability domain reset the form.** The first VM came up with the wrong OS,
  shape and no public IP. *Lesson: read the summary, not the fields you set.*
- **F2: a boot-volume swap can't change the OS.** Fixed by creating the new VM alongside, then
  terminating the old one, which kept the scarce ARM capacity. *Lesson: sequence around the scarce
  resource.*
- **F4: CPU and memory resize separately.** Raising OCPUs left memory unchanged until a second
  edit. *Lesson: verify on the machine (`nproc`, `free`), not in the console.*
- **F5: the registrar parks a new domain with a wildcard record.** `api.` resolved to the
  registrar's parking page until the wildcard was deleted.
- **F9: HTTP/3 (UDP 443) is published but no firewall allows it.** Harmless, since browsers fall
  back to TCP, but it is a port that looks open and isn't.

### Runbook bugs found only by running the runbook
- **F3: the firewall rule landed after the REJECT.** The runbook inserted at a fixed position; this
  image had its REJECT earlier, so the rule was *listed* but never *reached*. It is now inserted at
  the REJECT's position. *Lesson: a rule that is listed is not a rule that is reached.*
- **F7: two steps were in an impossible order.** Starting the backend first let Flyway create the
  schema, and the restore then refused the non-empty database. Now: databases, restore, backend.
- **Preview deploys:** the runbook said reads work there. They don't, because the API refuses
  `*.vercel.app` with CORS 403. Measured and corrected.
- **F13: the "run once" ingest command started a second production server.** Its flags were
  appended to the image's `ENTRYPOINT` as unused arguments, so it booted Tomcat, went *healthy*,
  and waited with its own 06:00 scheduler, a duplicate ingest in waiting. Fixed with
  `--entrypoint sh`. *Lesson: "healthy" proves it's running, not that it's doing what you asked.*

### Security
- **F8: the database copy carried development test accounts into production.** They were deleted
  in one transaction. *Lesson: a full dump copies people, not just data.*
- **4a: the rate limiter is only as good as the proxy.** The application trusts the client address
  it is given; what makes that safe is Caddy discarding any incoming `X-Forwarded-For`. Proven
  against the live site with forged headers. *Lesson: find which layer actually enforces a control,
  then test that layer.*

### Mistakes in the verification itself
- **F6: a port test passed against the wrong server.** Ports were tested *by name* while DNS was
  still changing, and a cached record pointed at the registrar's parking page. Let's Encrypt, using
  the real address, timed out: the cloud firewall only allowed SSH. *Lesson: while DNS is changing,
  test by IP.*
- **F12: a parity check "passed" on two identical errors.** A syntax error broke both sides of a
  comparison, and two identical tracebacks compared equal. *Lesson: a comparison must fail when
  either side is empty or an error.*

### Configuration
- **F11: the frontend built into a site where every request 404'd.** The API URL was stored as a
  Vercel *Secret*, which built into an empty string, and the code's `??` fallback only catches
  `undefined`, not `""`. Re-added as a plain config value and confirmed by reading the shipped
  JavaScript. *Lesson: a default that hides an absence is worse than a crash.*

## Known gaps

- **Check 4b** has not been run; it needs a second client on a different network.
- **F8 follow-up:** `scripts/db-restore.sh` should not copy `users`, `refresh_tokens` or user-owned
  `scoring_profiles`.
- **F11 follow-up:** the production frontend build should fail on an empty `NEXT_PUBLIC_API_URL`.
- **F9:** open UDP 443 in both firewalls, or stop publishing it.
- **Backups:** every redeploy takes a `pg_dump`, copies it off the VM and proves it restores
  ([`deploy/README.md`](../deploy/README.md) §7); a scheduled backup is not automated yet.
