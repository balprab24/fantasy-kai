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
pos=$(sudo iptables -L INPUT --line-numbers -n | awk '/REJECT/{print $1; exit}')
sudo iptables -I INPUT "$pos" -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo iptables -I INPUT "$pos" -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo netfilter-persistent save
sudo iptables -L INPUT -n --line-numbers   # 80 and 443 must sit ABOVE the REJECT
```

**Insert at the REJECT's position, never at a hardcoded number.** This file used to
say `-I INPUT 6`, copied from guides written against an image whose chain had six
rules. Ubuntu 24.04 on A1 (2026-09-22) ships **five**, with the REJECT at 5 — so
`-I INPUT 6` lands *after* it, the rules are present in the listing, and no packet
ever reaches them. A firewall rule that is listed is not a rule that is reached.

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
docker compose --env-file deploy/.env -f deploy/compose.prod.yml up -d --wait postgres redis
```

**Only Postgres and Redis here — the backend waits until §4.** This file used to
start the whole stack in §3, and that order cannot work: the backend boots, Flyway
creates V1–V5 in the empty database, and §4's `db-restore.sh` then refuses the
target as non-empty. Found by running it on 2026-09-22.

Point an `A` record for `api.<domain>` at the VM's public IP **before** Caddy first
starts — it requests a certificate on first contact, and repeated failures are how
you meet Let's Encrypt's rate limit. **Before starting Caddy, prove 80 and 443 reach
the VM by IP, not by name:** `nc -vz <vm-ip> 80` must say *refused* (packets arrive,
nothing listening yet), not *timed out* (the OCI security list is dropping them).
Testing by name while DNS is changing can hit a cached parking page and report open
ports that are not — which is exactly what happened on 2026-09-22.

## 4. Move the data

Through an ssh tunnel. `compose.prod.yml` binds Postgres to `127.0.0.1` on the VM
so this is the only way in:

```bash
ssh -N -L 15432:localhost:5432 ubuntu@<vm-ip> &
./scripts/db-restore.sh 'postgresql://fantasykai:PASSWORD@localhost:15432/fantasykai'
```

It refuses a non-empty target and compares row counts before and after. The dump
was **17 MB** on 2026-09-22, against **37 MB** quoted for the local database on
09-14 — what that 37 MB measured was never written down, so re-measure rather than
quoting either. Then start the rest:

```bash
docker compose --env-file deploy/.env -f deploy/compose.prod.yml up -d --build --wait
```

The backend log should say *"Schema is up to date. No migration necessary."*: the
dump carries `flyway_schema_history`, so Flyway finds V5 applied and does nothing.

**A full dump also carries `users`, `refresh_tokens` and user-owned
`scoring_profiles`** — on 2026-09-22 that was three dev test accounts
(`*@example.com`) — the addresses the test suite registers with a password constant that is
public in the repo — able to log into production until deleted. Delete
them before the site is public; `db-restore.sh` should exclude those rows and does
not yet.

## 5. The frontend

Vercel → import the repo, root directory `frontend`,
`NEXT_PUBLIC_API_URL=https://api.<domain>`. Attach the apex and `www`. Then set
`ALLOWED_ORIGINS` in `deploy/.env` to those two origins and
`docker compose ... up -d backend`.

**Known limitation, and a consequence of the same-site fix:** Vercel preview
deploys are served from `*.vercel.app`, which is cross-site from `api.<domain>`.
**Nothing data-driven works on previews** — not sign-in, and not reads either. This file used to
say "reads do"; measured 2026-09-23, the API answers `Origin: https://fantasykai.vercel.app` with
**403**, because `ALLOWED_ORIGINS` lists only the real domain. The cookie problem is real too, but
CORS stops the request first. Production only.

---

## 6. Acceptance — prove each constraint by trying to violate it

`QuerySafetyTests` proves the SQL-injection defence by attempting the attack and
then checking the table survived. Same treatment here. Each of these is a command
with an expected result, and **`./deploy/acceptance.sh` runs 1, 2, 4a, 4c, 5, 6, 7, 9 and 10** in one
go from the laptop — 3 needs a browser and an account, 4b a second real client, 8 the VM.

| # | Check | Expected |
|---|---|---|
| 1 | `curl https://api.D/actuator/health/liveness` | `200 {"status":"UP"}` |
| 2 | `curl https://api.D/actuator/health` anonymously | status only — **no component details** |
| 3 | Register, log in, hard-reload in a **real browser** | session survives — the `SameSite` fix, proven not reasoned |
| 4a | Six `/api/v1/auth/login` **through Caddy** with a varying forged `X-Forwarded-For` | the 6th is `429` — this tests **Caddy**, not the app |
| 4b | With that bucket at `429`, one request from a **genuinely different client** (phone off wifi) | `401`, not `429` |
| 4c | With that bucket at `429`, three more logins: `Forwarded: for=203.0.113.77`; `X-Forwarded-Prefix: /x`; and the path `/api/v1/%61uth/login` | all three `429`. A `401` on any of them means the bypass is live — every backend and Caddyfile before the 2026-09-24 fix returns it |
| 5 | `curl -H 'Origin: https://evil.example'` | no `access-control-allow-origin`; the real origin gets one |
| 6 | `curl -sI http://api.D/...` and a `https` response | `308` to https, and `strict-transport-security` present |
| 7 | `nc -z <vm-ip> 5432` / `6379` | **refused** — neither is on the internet |
| 8 | Ingest once on the VM (command below), then wait for 06:00 ET | an `ingest_runs` row appears **that nobody triggered** |
| 9 | `/api/v1/rankings` vs local, same profile and season | identical top 10 — as a member on both sides since 2026-09-29 (`PROD_TOKEN`, `LOCAL_TOKEN`) |
| 10 | `/api/v1/rankings` and `/api/v1/scoring-profiles` with **no token** | `401` + `problem+json` on both — the reads need an account (north-star §2). A `200` means the gate is the website's alone again |

**Check 4 said something false until 2026-09-21, and the correction is the point.**

It used to read: six logins with a varying forged `X-Forwarded-For`, the 6th still
`429`, "run locally on 2026-09-14 and passed". A test written against the
application — `AuthRateLimitTests.aForgedForwardedForBuysAFreshBucket_whichIsWhyCaddyMustReplaceIt`
— returns **401, not 429**. Six forged addresses, six fresh buckets. The claim had
survived because nothing ever executed it.

**So the application does not defend this; Caddy does.** `AuthRateLimitFilter` keys
its bucket on `X-Forwarded-For`, falling back to `getRemoteAddr()` — and
`server.forward-headers-strategy: framework` means Spring has *already rewritten*
`getRemoteAddr()` from the forged header before the filter runs, so both paths trust
it. What makes the deployment safe is external, and **both halves are load-bearing**:

1. Caddy with `trusted_proxies` unset **discards** an incoming `X-Forwarded-For` and
   writes the real peer.
2. `compose.prod.yml` gives the backend `expose` and **no published port**, so nothing
   can reach it except through Caddy.

Publish that port, put a CDN or Cloudflare's orange cloud in front, or set
`trusted_proxies`, and the 5/min limit becomes decoration. Fly would have broken it
too — it *appends* to a client-supplied header rather than replacing it.

That is why 4a is now specified **through Caddy**: run against the live host it tests
the one control that actually exists. Run against the app directly it tests nothing,
which is how the old wording came to be believed.

**4c exists because 4a covered one header, and the proxy passed three other ways around the
limiter.** Found 2026-09-24, and reproduced against a local copy of this exact stack before
anything was changed:

- `Forwarded: for=<forged>` — RFC 7239's spelling of the client address. Spring reads it
  *before* `X-Forwarded-For`; Caddy rewrites only the `X-Forwarded-*` trio and passed this
  through. Seven varying forgeries, seven `401`s.
- `X-Forwarded-Prefix: /x` — rewrote the request URI to `/x/api/v1/auth/login`, which the
  limiter's `startsWith` check did not match and routing still sent to login. One constant
  header, limiter never consulted.
- `/api/v1/%61uth/login` — no header at all. The limiter compared the raw, still-encoded URI;
  routing decodes. **No proxy setting can fix this one**, which is why the fix is two layers:
  Caddy strips the four forwarded headers it does not write (`header_up -…`), and the
  application ignores them too (`ForwardedHeaderConfig`) and matches the path the way routing
  does (`AuthRateLimitFilter`). Each layer was proven alone: new Caddyfile over the old
  backend closed the two header bypasses but not the encoded path; the old Caddyfile over the
  new backend closed all three. `AuthRateLimitTests` pins the application half.

**4b is still why 4a alone is not enough,** and it now needs a genuinely different
client (a phone off wifi) rather than a second forged header. 4a passing is also
exactly what you would see if the limiter had collapsed into a **single global
bucket** — every user in the world sharing five attempts a minute, a worse bug than
the one 4a tests for. Every request in 4a comes from one machine, so it cannot tell
them apart. 4b can.

Do not add `trusted_proxies` to the Caddyfile, or change
`server.forward-headers-strategy`, without re-running **both** — and without
re-reading `AuthRateLimitTests`, which will still be green while the deployment is
broken, because it pins the application's behaviour and the application is not the
control.

**Check 8's first half needs a command that works on the VM**, and the obvious one
does not. `scripts/ingest-once.sh` wants a **packaged jar and a JDK** — the VM has
neither, only Docker — and it sources `<repo>/.env`, while the production secrets
live in `deploy/.env`. Run the image instead:

```bash
docker compose --env-file deploy/.env -f deploy/compose.prod.yml \
  run --rm --no-deps --entrypoint sh backend \
  -c 'exec java $JAVA_OPTS -jar app.jar \
      --spring.main.web-application-type=none \
      --fantasykai.ingest.once=true'
```

**`--entrypoint sh` is load-bearing.** The image's `ENTRYPOINT` is
`["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]`, and without the override
compose *appends* the command to it — so it lands in `sh -c`'s `$0`/`$1`, which the
entrypoint string never reads. Both flags vanish silently, Tomcat starts, the
healthcheck goes green, and the "one-shot" runs forever as **a second backend with
its own `IngestScheduler`** — two ingests at 06:00. This file carried that command
until 2026-09-23; running it is how it was found. The fixed form exits 0 in ~5s,
prints no `Tomcat started`, and leaves no `backend-run` container.

`--no-deps` because Postgres and Redis are already up; `run --rm` so the one-shot
does not become a second long-lived backend. `web-application-type=none` is the
shape `OneShotContextTests` covers and the reason `SecurityConfig.filterChain`
carries `@ConditionalOnWebApplication`.

Check 8's second half spans a night by construction. A run that fires with nobody
watching is the entire point of this phase: `IngestScheduler` only fires inside a
live JVM, which is why the backend is `restart: unless-stopped` and why nothing here
is allowed to scale to zero. **Measured on 2026-09-21, before any of this deployed:
the laptop's pull had been dead four days and 2026 week 2 — 1,043 stat rows — was
simply absent from the database.** Third occurrence. That is the argument for this
phase, restated with a number.

## 7. Redeploy a running stack

The procedure every backend change after the first deploy follows, written from the one that
proved it: 2026-09-29, backend `6c2580b` → `366b0ad`, carrying `V6`. On the VM, from
`~/fantasy-kai`, with

```bash
C="docker compose --env-file deploy/.env -f deploy/compose.prod.yml"
```

**1. If the change carries a migration, prove the rollback before you need it.** Build the
*running* commit's image on the laptop and boot it against a database already at the new version:

```bash
git archive <running-sha> backend | tar -x -C "$TMP" && docker build -t fantasykai-backend:<running-sha>-local "$TMP/backend"
docker run --rm -p 18080:8080 -e DB_URL=jdbc:postgresql://host.docker.internal:5433/fantasykai \
  -e DB_USERNAME=... -e DB_PASSWORD=... -e JWT_SECRET=... -e ALLOWED_ORIGINS=http://localhost:3000 \
  fantasykai-backend:<running-sha>-local
```

Flyway ignores migrations newer than the code it ships with, so an additive migration leaves the
old image bootable — measured, not assumed: on 2026-09-29 the `6c2580b` image logged *"Schema
"public" has a version (6) that is newer than the latest available migration (5)"* and served
rankings 3.1 s later. A migration that fails this test makes the backup in step 2 the only way
back, and deserves a second look before it ships.

**2. Back up, copy it off the box, and restore the copy.**

```bash
mkdir -p ~/backups && f=~/backups/pre-<change>-$(date -u +%Y%m%dT%H%M%SZ).dump
$C exec -T postgres pg_dump -U fantasykai -Fc fantasykai > "$f" && sha256sum "$f"
# then, from the laptop:
scp -i ~/.ssh/fantasykai_oracle ubuntu@<vm-ip>:backups/<file> ~/fantasykai-backups/
```

Restore the laptop's copy into a scratch database and compare every table's count with
production's; then drop the scratch database. A backup that has never been restored is a file, not
a backup. The dump carries users' emails and password hashes: it lives in `~/fantasykai-backups/`,
never in the repository.

**3. Tag the running image:** `docker tag fantasykai-backend:latest fantasykai-backend:<running-sha>`.

**4. Pull:** `git status --short` must print nothing, then `git pull --ff-only origin main`.

**5. Build and swap the backend:**

```bash
$C build backend && $C up -d --wait backend
$C logs backend --since 5m | grep -E 'Migrating|Successfully applied|Started FantasyKai'
docker run --rm --entrypoint sh fantasykai-backend:latest -c 'unzip -l app.jar | grep tomcat-embed-core'
```

119 s to build and 18 s from swap to healthy on 2026-09-29, of which the new JVM took ~12 s from
container creation to *Started*. Caddy answers `502` while nothing listens — how long that window
lasts from outside has not been measured.
The last line must still say **10.1.59** — CLAUDE.md, "The EOL clock".

**6. If the Caddyfile changed, recreate Caddy — never just reload it:**

```bash
$C up -d --force-recreate --no-deps caddy
$C exec -T caddy grep -c header_up /etc/caddy/Caddyfile    # must equal the count on disk
```

The Caddyfile is a **single-file bind mount, and a single-file bind mount pins an inode, not a
path.** `git pull` replaces the file — a new inode — and the running container keeps reading the
old one. Measured on 2026-09-29 after the pull: the host's Caddyfile was inode 552587 with four
`header_up` lines; the container's was 552551 with **none**. `caddy reload` would have reloaded
the old config, reported success, and left all three 4c bypasses open behind a green step.
Certificates survive a recreate because they live in the `caddy_data` volume.

**7. Ingest once** (the command in §6) if the change adds columns the ingest fills — then query
those columns. The exit code says the pull ran, not that it wrote what you added.

**8. Run the acceptance checks:** `./deploy/acceptance.sh` from the laptop, with the local stack up
for check 9. It exits 1 on any failure and prints `?`, never PASS, for a check it could not run.

**Rolling back** the backend is the tag from step 3:

```bash
docker tag fantasykai-backend:<running-sha> fantasykai-backend:latest
$C up -d --no-build --wait backend
```

`up` does not rebuild an image that exists; `--no-build` makes that a guarantee rather than a
default, because the checkout is the new code and a build from it would undo the rollback. If the
Caddyfile moved too, `git checkout <running-sha> -- deploy/Caddyfile` and repeat step 6. The
rollback's *image* half is proven by step 1; these two commands have not yet been run on the VM.
