# Phase 5d — your half, step by step

Everything that can be done without an account is **done and merged** (PR #20). What's left is
three accounts and a domain, because I can't create those for you. This file is only your half.
Mine is [`deploy/README.md`](deploy/README.md).

**Work top to bottom. Stop at the 🛑 and send me the four things it asks for** — I do the rest.

- **Time:** ~45–70 minutes, most of it waiting on Oracle.
- **Cost:** ~$12/year for the domain. Nothing else, ever.

---

## Collect these as you go

You need four values at the 🛑. Write them here as you get them:

| # | Thing | From | Value |
|---|---|---|---|
| 1 | Domain | Step 3 | |
| 2 | VM public IP | Step 2 | |
| 3 | SSH user | Step 2 (Oracle Ubuntu = `ubuntu`) | |
| 4 | Registrar | Step 3 | |

---

## Why this order

`DEPLOY-STEPS.md` used to put the domain first. The dependency actually runs the other way:

> **VM exists → you get its IP → you add the `api.` DNS record → Caddy can start.**

The domain is a prerequisite of the *DNS record*, not of the VM. And **Oracle is the only step here
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

| Field | Enter |
|---|---|
| Customer type | **Individual** |
| Cloud Account / Tenancy Name | **`fantasykai`** — if taken: `fantasykai-cloud`, then `fantasykai-prod`, then `fantasykai1` |
| Home Region | **US Midwest (Chicago)** |

Two things on this form are permanent, and the form says so in passing:

- **The tenancy name cannot be changed and appears in your login URL.** Oracle pre-fills it from
  your email handle — which is your real name. Don't keep it. Use the project name; it's already
  public on GitHub and it reads correctly to anyone you show this to.
- **🚩 The home region cannot be changed.** It is the single irreversible choice in this whole
  process. Central time → Chicago. The form also warns *"do not attempt to create multiple accounts
  in order to change regions"* — Oracle enforces that, so get it right rather than planning to redo it.

Then verify your email and sign in to the console.

### 2b. Create the instance

Console → **Compute → Instances → Create instance**.

| Field | Value |
|---|---|
| Image | **Canonical Ubuntu** 24.04 (or the current LTS) |
| Shape | *Change shape* → **Ampere** → `VM.Standard.A1.Flex` → **2 OCPUs**, **12 GB memory**. The form must say **"Always Free eligible"** |
| Networking | accept the default VCN it offers to create; make sure it **assigns a public IPv4 address** |
| SSH keys | *Paste public keys* → the output of `cat ~/.ssh/fantasykai_oracle.pub` |
| Boot volume | default (~50 GB) is fine; Always Free allows 200 GB total |

Click **Create**, then **write down the public IP address** in the table at the top.

> ### ⚠️ "Out of host capacity" is normal, not a mistake you made
> Free ARM instances are in constant demand. In order:
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
|---|---|---|
| `0.0.0.0/0` | TCP | **80** |
| `0.0.0.0/0` | TCP | **443** |

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
> Cloudflare Registrar requires Cloudflare DNS, and Cloudflare defaults new records to **proxied**
> (orange cloud). Proxied is wrong here, for a specific and quiet reason:
>
> `AuthRateLimitFilter.clientIp()` takes the **first** entry of `X-Forwarded-For`. That is only safe
> because Caddy, with `trusted_proxies` unset, *discards* any incoming value and writes the real
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
      set *at import time* — `frontend/src/lib/api.ts:14` falls back to `http://localhost:8080`
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

| Step | What | Who |
|---|---|---|
| 6 | Open the VM's internal firewall, install Docker, clone the repo, generate the production secrets | me |
| 7 | **You add 3 DNS records** — I give you the exact values | **you**, 2 min |
| 8 | Start the stack; Caddy gets a Let's Encrypt certificate on its own | me |
| 9 | Copy the database up through an SSH tunnel and check the row counts match | me |
| 10 | Import the frontend to Vercel, attach the domain | me |
| 11 | Run the 10 acceptance checks in [`deploy/README.md`](deploy/README.md) §6 | me — except one |

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
  cross-site from your API, so the cookie isn't sent. Reads work; login doesn't. Production only.
  This is a written-down consequence of the same-site fix, not a bug to report.
- **You now own patching and backups.** That's the price of $0 and always-on. A `pg_dump` cron on the
  VM is in scope for me; storing those dumps **off the box** is not, and it's genuinely owed — one
  Always Free VM with no offsite copy is one Oracle reclamation away from losing six seasons.
