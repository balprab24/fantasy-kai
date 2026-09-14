# Deploying fantasy-kai — Phase 5d

The runbook. Why this host and not the one the docs used to name is in
[`../docs/north-star.md`](../docs/north-star.md) §5d; this file is the steps.

**The shape:** one Oracle Cloud Always Free VM runs Postgres, Redis, the backend
image and Caddy from [`compose.prod.yml`](compose.prod.yml). Vercel serves the
Next.js frontend. One domain joins them — `example.com` on Vercel,
`api.example.com` on the VM.

**That single domain is a design constraint, not packaging.** The refresh token
is a `SameSite=Strict` cookie, and Strict is decided by *registrable domain*, not
by origin. A `*.vercel.app` frontend calling a `*.fly.dev` API is cross-site, so
the browser never sends the cookie to `POST /api/v1/auth/refresh` and every page
reload silently logs the user out. `localhost:3000` → `localhost:8080` hides this
completely, because ports do not affect same-site. Sharing one registrable domain
is what lets `SameSite=Strict` stay as handoff §8 specified it, rather than being
weakened to `None` to accommodate the hosting.

---

## 1. Before anything: the accounts

1. **A domain.** Any registrar, ~$12/yr. The one cost in this plan.
2. **Oracle Cloud.** A card is required for identity verification; Always Free
   resources are never charged. **Choose the home region carefully — it cannot be
   changed later**, and Ampere A1 capacity is frequently exhausted in popular ones.
3. **Vercel**, Hobby tier. GitHub sign-in is enough.

## 2. The VM

Shape `VM.Standard.A1.Flex`, 2 OCPU / 12 GB, Ubuntu LTS. (Oracle's Always Free page states 1,500 OCPU-hours and 9,000 GB-hours a month for
`VM.Standard.A1.Flex`, which is 2 OCPU / 12 GB run continuously. Secondary reports
say this was halved from 4 OCPU / 24 GB in mid-2026; the halving date is not from
Oracle's own page and is not worth relying on — the current allowance is.)

**Open 80 and 443 in two places, not one.** The OCI security list on the subnet
*and* the instance's own firewall — Oracle's Ubuntu images ship a default-deny
INPUT chain, so traffic the security list permits is still dropped by the host
with no log and no error. This is the classic first-hour trap:

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

Then Docker:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu   # log out and back in
```

**If Ampere A1 is out of capacity** — which is common — the fallback is two
`VM.Standard.E2.1.Micro` x86 instances (1 GB each, also Always Free): one for the
JVM and Caddy, one for Postgres and Redis, over the VCN's private network. Both
on a single 1 GB box does not fit: `-XX:MaxRAMPercentage=70` gives the JVM ~700 MB
and leaves nothing for Postgres.

## 3. Configure and start

```bash
git clone https://github.com/balprab24/fantasy-kai.git && cd fantasy-kai
cp deploy/.env.example deploy/.env
$EDITOR deploy/.env        # every variable is required; see the file
docker compose --env-file deploy/.env -f deploy/compose.prod.yml up -d --build
```

Point an `A` record for `api.<domain>` at the VM's public IP **before** the first
start — Caddy requests a certificate on first contact, and repeated failures are
how you meet Let's Encrypt's rate limit.

## 4. Move the data

Through an ssh tunnel. `compose.prod.yml` binds Postgres to `127.0.0.1` on the VM
so this is the only way in:

```bash
ssh -N -L 15432:localhost:5432 ubuntu@<vm-ip> &
./scripts/db-restore.sh 'postgresql://fantasykai:PASSWORD@localhost:15432/fantasykai'
```

It refuses a non-empty target and compares row counts before and after. The local
database was **37 MB** on 2026-09-14 — re-measure the dump rather than quoting an
older figure. Afterwards the backend log should say *"Schema is up to date. No
migration necessary."*: the dump carries `flyway_schema_history`, so Flyway finds
V5 applied and does nothing.

## 5. The frontend

Vercel → import the repo, root directory `frontend`,
`NEXT_PUBLIC_API_URL=https://api.<domain>`. Attach the apex and `www`. Then set
`ALLOWED_ORIGINS` in `deploy/.env` to those two origins and
`docker compose ... up -d backend`.

**Known limitation, and a consequence of the same-site fix:** Vercel preview
deploys are served from `*.vercel.app`, which is cross-site from `api.<domain>`.
**Auth does not work on previews** — reads do, sign-in does not. Production only.

---

## 6. Acceptance — prove each constraint by trying to violate it

`QuerySafetyTests` proves the SQL-injection defence by attempting the attack and
then checking the table survived. Same treatment here. Each of these is a command
with an expected result.

| # | Check | Expected |
|---|---|---|
| 1 | `curl https://api.D/actuator/health/liveness` | `200 {"status":"UP"}` |
| 2 | `curl https://api.D/actuator/health` anonymously | status only — **no component details** |
| 3 | Register, log in, hard-reload in a **real browser** | session survives — the `SameSite` fix, proven not reasoned |
| 4a | Six `/api/v1/auth/login` with a **varying forged** `X-Forwarded-For` | the 6th is still `429` |
| 4b | With that bucket at `429`, one request from a **different client** | `401`, not `429` |
| 5 | `curl -H 'Origin: https://evil.example'` | no `access-control-allow-origin`; the real origin gets one |
| 6 | `curl -sI http://api.D/...` and a `https` response | `308` to https, and `strict-transport-security` present |
| 7 | `nc -z <vm-ip> 5432` / `6379` | **refused** — neither is on the internet |
| 8 | Ingest once on the VM, then wait for 06:00 ET | an `ingest_runs` row appears **that nobody triggered** |
| 9 | `/api/v1/rankings` vs local, same profile and season | identical top 10 |

Check 4 is the one that is easy to skip, and **4b is why 4a alone is not enough.**
`AuthRateLimitFilter.clientIp()` takes the *first* entry of `X-Forwarded-For`, which
is only sound because Caddy — with `trusted_proxies` unset — discards an incoming
value and writes the real peer. The same code is **bypassable** behind a proxy that
appends instead of replacing, which is what Fly does.

But 4a passing is also exactly what you would see if the limiter had collapsed into
a **single global bucket** — every user in the world sharing five attempts a minute.
That is a worse bug than the one 4a tests for, and 4a cannot distinguish them,
because every request in it comes from one machine. 4b separates them. Both were run
locally on 2026-09-14 and both passed. Do not add `trusted_proxies` to the Caddyfile,
or change `server.forward-headers-strategy`, without re-running **both**.

Check 8 spans a night by construction. A run that fires with nobody watching is
the entire point of this phase: `IngestScheduler` only fires inside a live JVM,
which is why the backend is `restart: unless-stopped` and why nothing here is
allowed to scale to zero.
