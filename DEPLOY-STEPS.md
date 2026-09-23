# Phase 5d — Deploy

> ## ▶ Status — last updated 2026-09-23
>
> **Live: https://www.fantasykai.com** (the apex 308s to `www`) · API **https://api.fantasykai.com**
>
> **9 of 10 acceptance checks pass.** Owed: **4b** (needs a phone on cellular — see below).
> The production daily ingest **fired on its own at 06:00:00 ET on 2026-09-23**.
> Next phase: **11.5 — Spring Boot 3.5 → 4** (overdue security work), then **6 — Projections**.

---

## The 30-second version

> fantasy-kai is an NFL fantasy analytics app. Its one idea: **store raw stat lines, never fantasy
> points, and score on demand against any league's rules** — so PPR, half-PPR and a custom
> 6-point-passing-TD league are three rows in a table, not three code paths.
>
> I deployed it for **$11 a year** — the domain is the only cost. The backend, Postgres, Redis and a
> TLS proxy run on one Oracle Cloud Always Free ARM VM; the Next.js frontend is on Vercel.
>
> The interesting constraint was **one registrable domain**. Login uses a `SameSite=Strict`
> refresh cookie, and browsers decide "same site" by registrable domain — so `vercel.app` talking
> to a separate API host would log you out on every reload, and `localhost` hides that completely.
> Putting the site on `www.fantasykai.com` and the API on `api.fantasykai.com` fixes it properly
> instead of weakening the cookie. I proved it in a real browser: register, log in, hard-reload,
> still signed in.
>
> The reason to deploy at all: the stats pull only runs inside a live JVM, and on my laptop it had
> **silently stopped four times**. Now it fires at 06:00 ET every morning with nobody watching —
> and the first unattended run succeeded.
>
> I verified it by **trying to break each guarantee** rather than reading config: forging IP
> headers against the rate limiter, calling from a hostile origin, port-scanning the database.
> Doing the deploy for real found **five bugs in my own runbook** and two mistakes in my own
> checks.

---

## How it fits together

```
                 ┌──────────────── Vercel ────────────────┐
 browser ──────► │ www.fantasykai.com   Next.js 16         │
    │            └────────────────────────────────────────┘
    │  fetch + HttpOnly refresh cookie (same site: fantasykai.com)
    ▼
 ┌────────────── Oracle Always Free VM · 2 OCPU / 12 GB ARM · Chicago ─────────────┐
 │  Caddy :80/:443  ── TLS (Let's Encrypt), HSTS, replaces X-Forwarded-For          │
 │     │                                                                            │
 │     ▼  (compose network only — no published port)                                │
 │  Spring Boot 3.5 / Java 25   ── scoring, auth, @Scheduled 06:00 ET ingest        │
 │     │                  │                                                         │
 │  Postgres 16         Redis 7        both bound to 127.0.0.1 — reachable only     │
 │  (114,542 stat rows)  (rate limits)  through an ssh tunnel                       │
 └──────────────────────────────────────────────────────────────────────────────────┘
 Two firewalls in front: the OCI security list (22, 80, 443) and the VM's own iptables.
```

---

## Prove it by breaking it — the acceptance checks

Each check is an attack or a failure tried against the live site, with the result that came back.

| # | What I tried | What happened | ✓ |
|---|---|---|---|
| 1 | `GET /actuator/health/liveness` | `200 {"status":"UP"}` | ✅ |
| 2 | Read the health endpoint anonymously | status only — no internals leaked | ✅ |
| 3 | Register → log in → **hard-reload** in a real browser | still signed in; `refresh` → **200** | ✅ |
| 4a | Six logins, each with a **different forged `X-Forwarded-For`** | `401 ×5`, then **`429`** — Caddy throws the forged header away, so all six share one bucket | ✅ |
| 4b | With that bucket full, one login from a **different real client** | owed — needs a phone off wifi | ⬜ |
| 5 | Call the API from `Origin: evil.example` and from `*.vercel.app` | **403** both; the real origin gets `allow-origin` + `allow-credentials` | ✅ |
| 6 | Plain `http://` · inspect HTTPS headers | **308** to https · `strict-transport-security` **actually sent** (it was configured but silently missing before 5d) | ✅ |
| 7 | Port-scan 5432 / 6379 / 8080 from the internet | all **time out** — dropped before they reach the VM | ✅ |
| 8 | Run the ingest on the VM · then wait for 06:00 ET | 5 sources SUCCESS · then an **unattended** run at **06:00:00 ET**, 6 sources SUCCESS | ✅ |
| 9 | Top 10 for all four scoring presets, production vs local | **identical** — same players, same order, same points | ✅ |

---

## What broke, and what it taught me

Every finding below cost real time, and each is written down in the runbook so it cannot cost it
twice. The one-line **lesson** is the part worth saying out loud.

### Cloud and infrastructure traps
- **F1 — Changing the availability domain reset the form.** The first VM came up Oracle Linux,
  1 OCPU / 6 GB, no public IP. *Lesson: read the summary, not the fields you set.*
- **F2 — A boot-volume swap can't change the OS.** Fixed by creating the Ubuntu VM alongside, then
  terminating the old one — which kept the scarce ARM capacity. *Lesson: sequence around the
  scarce resource.*
- **F4 — CPU and memory resize separately.** 2 OCPU left memory at 6 GB until a second edit.
  *Lesson: verify on the machine (`nproc`, `free`), not in the console.*
- **F5 — The registrar parks a new domain with a wildcard.** `api.` resolved to Porkbun until the
  `CNAME *` was deleted.
- **F9 — HTTP/3 (UDP 443) is published but no firewall allows it.** Harmless — browsers fall back
  to TCP — but it's a port that looks open and isn't. Owed.

### Runbook bugs found only by running the runbook
- **F3 — The firewall rule landed after the REJECT.** The runbook said `iptables -I INPUT 6`; this
  image has five rules with REJECT at 5, so the rule is *listed* but *never reached*. Now inserted
  at the REJECT's position. *Lesson: a rule that is listed is not a rule that is reached.*
- **F7 — Steps 3 and 4 were in an impossible order.** Starting the backend first let Flyway create
  the schema, and the restore then refused the non-empty database. Now: databases → restore →
  backend.
- **Preview deploys — the runbook said reads work there. They don't:** the API refuses
  `*.vercel.app` with CORS 403. Measured, and corrected.
- **F13 — The "run once" ingest command started a second production server.** Its flags were
  appended to the image's `ENTRYPOINT` as unused shell arguments, so it booted Tomcat, went
  *healthy*, and sat there with its own 06:00 scheduler — a duplicate ingest waiting to happen.
  Fixed with `--entrypoint sh`. *Lesson: "healthy" proves it's running, not that it's doing the
  thing you asked.*

### Security
- **F8 — The database copy carried dev test accounts into production.** Three `@example.com`
  logins — the same addresses the test suite registers with a password constant that is **public
  in the repo**. Whether these rows used it is not recoverable; they were deleted either way. Deleted in one transaction (`DELETE 4` tokens · `1` profile ·
  `3` users). *Lesson: a full dump copies people, not just data.* The restore script should
  exclude them — owed.
- **4a — The rate limiter is only as good as the proxy.** The app trusts `X-Forwarded-For`; what
  makes that safe is Caddy discarding the incoming value. Proved against the live site with forged
  headers. *Lesson: find which layer actually enforces a control, then test that layer.*

### Mistakes in my own verification
- **F6 — My port test passed against the wrong server.** I tested 80/443 *by name*; my laptop had
  cached the parking page's address, so Porkbun answered and I reported the ports open. Let's
  Encrypt, using the real IP, got *"Timeout during connect"* — the cloud firewall only allowed SSH.
  I stopped Caddy to protect the certificate rate limit, fixed the firewall, re-tested by IP.
  *Lesson: while DNS is changing, test by IP.*
- **F12 — My parity check "passed" on two identical errors.** A syntax error broke both sides of a
  comparison, and two identical tracebacks compared equal. Caught, re-run properly.
  *Lesson: a comparison must fail when either side is empty or an error.*

### Configuration traps
- **F11 — The frontend built into a site where every request 404'd.** Vercel stored the API URL as
  a *Secret*, which built into an empty string. The code's `??` fallback only catches `undefined`,
  not `""` — so the site used neither the real URL nor the dev one, and called itself. Re-added as
  *Config*, confirmed by reading the shipped JavaScript. *Lesson: a default that hides an absence
  is worse than a crash.* The build should refuse an empty value — owed.

### Cost
- **F10 — `.online` was $1.96, then $28.84 a year.** `.com` was $11.08 flat. *Lesson: price the
  renewal.*

### Before the deploy started
- The laptop's daily ingest had **failed at 05:15** on 2026-09-22 (nflverse timeout on wake) — the
  fourth silent gap, and the case for the deploy in one line.
- **And again on 2026-09-23, side by side with production:** the laptop failed at 05:59 local
  (`Connection reset` reading `games.csv` as it woke); the VM's unattended run at 06:00 ET the same
  morning succeeded on all six sources. Same code, same source — the difference is a machine that
  is always on.

---

## Talking points

**"What was the hardest bug?"** — F13. The command looked right and the container reported
*healthy* — it just wasn't doing the job. It was only visible because I checked that the ingest wrote rows,
not that the container was up.

**"How do you know it works?"** — The table above. Every guarantee was tested by trying to
violate it on the live site, including the rate limiter with forged headers and the login cookie
in a real browser.

**"Why not Fly, Render or Koyeb?"** — Fly is now $5.70/month, Koyeb dropped its free tier, Render sleeps after 15 minutes (surveyed in
north-star §5d). Oracle's Always Free ARM VM is 2 cores / 12 GB permanently; the trade is that I
own patching and backups.

**"What would you do differently?"** — Test ports by IP from the start (F6), and make the
frontend build fail on an empty API URL (F11). Both were *verification* gaps, not code bugs.

**"What's the architecture's one idea?"** — Points are never stored. A ruleset is a vector, a stat
line is a vector, a score is a dot product plus threshold bonuses — computed per game, then summed.

---

## Where things are

| Thing | Value |
|---|---|
| Site | `https://www.fantasykai.com` — Vercel project `fantasykai` (Hobby), root `frontend` |
| API | `https://api.fantasykai.com` |
| Domain / DNS | `fantasykai.com` at Porkbun, $11.08/yr, expires 2027-09-22. `A @ 216.198.79.1` · `CNAME www → 8c5fe79700091090.vercel-dns-017.com` · `A api → 163.192.220.190` |
| VM | `fantasykai-ubuntu` · Ubuntu 24.04.5 · A1.Flex 2 OCPU / 12 GB · AD-2 · US Midwest (Chicago) |
| VM public IP | `163.192.220.190` (**ephemeral** — survived the resize reboot; lost if the instance is terminated) |
| SSH | `ssh -i ~/.ssh/fantasykai_oracle ubuntu@163.192.220.190` |
| Network | `vcn-20260922-1704` / `subnet-20260922-1704` · ingress 22, 80, 443 |
| Secrets | `~/fantasy-kai/deploy/.env` on the VM only (mode 600). Vercel holds one non-secret: `NEXT_PUBLIC_API_URL` (type **Config**) |
| Compose, on the VM | `cd ~/fantasy-kai && docker compose --env-file deploy/.env -f deploy/compose.prod.yml <cmd>` |
| Certificates | API: Let's Encrypt via Caddy, renews itself. Site: Vercel-managed |

### Measured on the live host

- Restore dump **17 MB**; source = target: 114,542 stat rows · 25,066 players · 1,965 games ·
  36 teams. Flyway: *"Schema is up to date. No migration necessary."* · Tomcat **10.1.59**.
- Image build on the VM **2m13s**; backend start **9.6s**.
- Half PPR 2025 top 3: McCaffrey 365.6 · Allen 364.62 · Maye 351.46.

---

## What's owed

- [ ] **Check 4b** — lock the laptop's bucket (six wrong logins), then one wrong login from a phone
      on cellular within a minute: it must say *wrong password*, not *too many attempts*.
- [ ] **F8** — `scripts/db-restore.sh` should not copy `users`, `refresh_tokens` or user-owned
      `scoring_profiles`.
- [ ] **F11** — `frontend/src/lib/api.ts` should fail the production build on an empty
      `NEXT_PUBLIC_API_URL`.
- [ ] **F9** — open UDP 443 in both firewalls, or stop publishing it.
- [ ] **Backups** — a `pg_dump` cron on the VM **and an off-box copy**. One free VM with no copy is
      one reclamation away from losing seven seasons.
- [ ] Confirm **auto-renew** is on at Porkbun; consider a **reserved** public IP.
- [ ] Decide whether the laptop's launchd ingest stays as a local mirror or is uninstalled — it
      failed on wake two mornings running (09-22, 09-23), so `session-check.sh` will keep reporting
      `FAIL ingest` locally while production is healthy.

---

*Everything below is the original step-by-step, kept for reference.*

## Collect these as you go

| #   | Thing        | From                              | Value |
| --- | ------------ | --------------------------------- | ----- |
| 1   | Domain       | Step 3                            | `fantasykai.com` |
| 2   | VM public IP | Step 2                            | `163.192.220.190` |
| 3   | SSH user     | Step 2 (Oracle Ubuntu = `ubuntu`) | `ubuntu` |
| 4   | Registrar    | Step 3                            | Porkbun |

---

## Why this order

`DEPLOY-STEPS.md` used to put the domain first. The dependency actually runs the other way:

> **VM exists → you get its IP → you add the `api.` DNS record → Caddy can start.**

The domain is a prerequisite of the _DNS record_, not of the VM. And **Oracle is the only step here
that can block for days** — Ampere A1 capacity runs out regularly. So Oracle goes first, and the
domain gets bought during Oracle's waiting periods.

The $12 is not at risk either way: every fallback host still needs the same single domain, for the
cookie reason in Step 3.

---

## ✅ Step 1 — SSH key (done)

Already generated at `~/.ssh/fantasykai_oracle`, no passphrase, `-rw-------`.

The **public** half, which is what you paste into Oracle in Step 2:

```bash
cat ~/.ssh/fantasykai_oracle.pub
```

⚠️ The file **without** `.pub` is the private key. It never leaves this machine — not to Oracle,
not to me, not into a repo. Add a passphrase later if you want: `ssh-keygen -p -f ~/.ssh/fantasykai_oracle`.

---

## Step 2 — Oracle Cloud (~15 min + verification wait)

[oracle.com/cloud/free](https://www.oracle.com/cloud/free/) → **Start for free**.

A credit card is required **for identity verification only**. Oracle places a ~$1 hold and releases
it. Always Free resources are never charged.

### 2a. The signup form

| Field                        | Enter                                                                                       |
| ---------------------------- | ------------------------------------------------------------------------------------------- |
| Customer type                | **Individual**                                                                              |
| Cloud Account / Tenancy Name | **`fantasykai`** — if taken: `fantasykai-cloud`, then `fantasykai-prod`, then `fantasykai1` |
| Home Region                  | **US Midwest (Chicago)**                                                                    |

Two things on this form are permanent, and the form says so in passing:

- **The tenancy name cannot be changed and appears in your login URL.** Oracle pre-fills it from
  your email handle — which is your real name. Don't keep it. Use the project name; it's already
  public on GitHub and it reads correctly to anyone you show this to.
- **🚩 The home region cannot be changed.** It is the single irreversible choice in this whole
  process. Central time → Chicago. The form also warns _"do not attempt to create multiple accounts
  in order to change regions"_ — Oracle enforces that, so get it right rather than planning to redo it.

Then verify your email and sign in to the console.

### 2b. Create the instance

Console → **Compute → Instances → Create instance**.

| Field       | Value                                                                                                                             |
| ----------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Image       | **Canonical Ubuntu** 24.04 (or the current LTS)                                                                                   |
| Shape       | _Change shape_ → **Ampere** → `VM.Standard.A1.Flex` → **2 OCPUs**, **12 GB memory**. The form must say **"Always Free eligible"** |
| Networking  | accept the default VCN it offers to create; make sure it **assigns a public IPv4 address**                                        |
| SSH keys    | _Paste public keys_ → the output of `cat ~/.ssh/fantasykai_oracle.pub`                                                            |
| Boot volume | default (~50 GB) is fine; Always Free allows 200 GB total                                                                         |

Click **Create**, then **write down the public IP address** in the table at the top.

> ### ⚠️ "Out of host capacity" is normal, not a mistake you made
>
> Free ARM instances are in constant demand. In order:
>
> 1. **Retry.** Capacity frees up, often at odd hours. Over a day or two it usually works.
> 2. **Try a different availability domain**, if your region offers more than one.
> 3. **Tell me** and we take the documented fallback: two `VM.Standard.E2.1.Micro` x86 instances
>    (1 GB each, also Always Free) — one for the JVM and Caddy, one for Postgres and Redis. Both on
>    a single 1 GB box does not fit; the JVM alone claims ~700 MB.
> 4. If none of that works, the honest alternative is paying Fly $5.70/month. Your call.

### 2c. Open the firewall (the first of two)

Console → **Networking → Virtual Cloud Networks →** your VCN **→** the public subnet **→ Security
List → Add Ingress Rules.**

Add **two** rules:

| Source CIDR | IP Protocol | Destination Port |
| ----------- | ----------- | ---------------- |
| `0.0.0.0/0` | TCP         | **80**           |
| `0.0.0.0/0` | TCP         | **443**          |

There is a **second firewall inside the machine itself** — Ubuntu's own default-deny INPUT chain —
that also has to be opened. That one is mine, over SSH, because it's a command rather than a form.
**It is the single most common reason an Oracle VM looks dead while the console says it's running.**

### ❌ Never press "Upgrade to Paid"

Always Free stays free indefinitely. Upgrading converts the account and starts billing. There is no
other way to accidentally get charged.

---

## Step 3 — Buy a domain (~10 min, ~$12/yr)

This is the only money in the plan, and it is **not cosmetic**.

> The refresh-token cookie is `SameSite=Strict`, and browsers decide "same site" by **registrable
> domain**. A site on `something.vercel.app` calling an API on `something.fly.dev` is cross-site, so
> the browser never sends that cookie to `/api/v1/auth/refresh` — and **every page reload logs you
> out.** One domain, apex on Vercel and `api.` on the VM, fixes it properly instead of weakening the
> cookie to `SameSite=None`.
>
> `localhost:3000` → `localhost:8080` hides this completely, because SameSite ignores ports. That is
> why local development has never shown it.

### Do this

1. **Pick a registrar.** Any of these are fine:
   - [**Cloudflare Registrar**](https://domains.cloudflare.com) — sells at cost, no markup, no
     upsells. **Read the warning below before choosing this one.**
   - [**Porkbun**](https://porkbun.com) — cheap, clean DNS, no surprises.
   - **Namecheap** — fine.
2. **Buy something short.** `.dev` and `.app` are ~$12–15/yr and **force HTTPS in browsers**, which
   suits this project. `.com` is fine too.
3. **Turn on auto-renew.** A lapsed domain breaks the site and the login together.
4. **Write the domain and the registrar into the table at the top.**

You do **not** configure DNS yet. That's Step 5 — I'll give you the exact records once the VM has
an IP and Vercel has printed its values.

> ### ⚠️ If you choose Cloudflare: the `api.` record must stay **DNS-only** (grey cloud)
>
> Cloudflare Registrar requires Cloudflare DNS, and Cloudflare defaults new records to **proxied**
> (orange cloud). Proxied is wrong here, for a specific and quiet reason:
>
> `AuthRateLimitFilter.clientIp()` takes the **first** entry of `X-Forwarded-For`. That is only safe
> because Caddy, with `trusted_proxies` unset, _discards_ any incoming value and writes the real
> peer. Put Cloudflare in front and every request arrives from a Cloudflare edge IP instead — so the
> 5/min limit on `/api/v1/auth/**` **collapses into one global bucket shared by every user on
> earth.**
>
> That is exactly the failure acceptance check **4b** exists to catch — and **check 4a would still
> pass while it was broken**, which is what makes it dangerous. A proxied record would also break
> Caddy's HTTP-01 certificate challenge.
>
> Grey cloud on `api.`. I'll verify it with check 4b rather than trust the setting.

---

## Step 4 — Vercel (~3 min)

[vercel.com/signup](https://vercel.com/signup) → **Continue with GitHub**.

- [ ] Stay on the **Hobby** plan — free, no card.
- [ ] Authorize Vercel to see the `fantasy-kai` repo.
- [ ] **Don't import the project yet.** The root directory and `NEXT_PUBLIC_API_URL` both have to be
      set _at import time_ — `frontend/src/lib/api.ts:14` falls back to `http://localhost:8080`
      otherwise, and a deployed site pointing at localhost looks fine until you click something.

---

## 🛑 Step 5 — Send me the four things

Paste the filled-in table from the top of this file:

1. **Domain** — e.g. `fantasykai.dev`
2. **VM public IP** — e.g. `129.146.x.x`
3. **SSH user** — `ubuntu` on Oracle's Ubuntu images; the instance page confirms it
4. **Registrar** — just the name, so I can give you the exact DNS records

That's your half done.

---

## What happens next (my half, ~30 min)

| Step | What                                                                                             | Who             |
| ---- | ------------------------------------------------------------------------------------------------ | --------------- |
| 6    | Open the VM's internal firewall, install Docker, clone the repo, generate the production secrets | me              |
| 7    | **You add 3 DNS records** — I give you the exact values                                          | **you**, 2 min  |
| 8    | Start the stack; Caddy gets a Let's Encrypt certificate on its own                               | me              |
| 9    | Copy the database up through an SSH tunnel and check the row counts match                        | me              |
| 10   | Import the frontend to Vercel, attach the domain                                                 | me              |
| 11   | Run the 10 acceptance checks in [`deploy/README.md`](deploy/README.md) §6                        | me — except one |

### The one check only you can do

**Register an account on the live site, log in, and hard-reload the page.** Still logged in ⇒ the
`SameSite` fix worked. That is the check this entire single-domain decision exists for, and it can
only be proven in a real browser.

### And one that spans a night

At **06:00 ET the next morning**, the daily ingest should fire with nobody watching, and an
`ingest_runs` row should appear that nobody triggered. That is the whole point of this phase — it's
the failure that stopped the pipeline for three days in September and that nothing noticed.

---

## Things worth knowing

- **The home region choice is the only step you can't undo.**
- **Never press "Upgrade to Paid."**
- **Your laptop's daily ingest keeps running** against the local database. Local and production will
  drift apart, which is expected — `session-check.sh` has a `DEPLOY` group that watches production
  separately.
- **Auth won't work on Vercel preview deploys.** Previews are served from `*.vercel.app`, which is
  cross-site from your API, so the cookie isn't sent — and CORS refuses that origin outright (403,
  measured 2026-09-23), so reads fail too. Production only.
  This is a written-down consequence of the same-site fix, not a bug to report.
- **You now own patching and backups.** That's the price of $0 and always-on. A `pg_dump` cron on the
  VM is in scope for me; storing those dumps **off the box** is not, and it's genuinely owed — one
  Always Free VM with no offsite copy is one Oracle reclamation away from losing six seasons.
