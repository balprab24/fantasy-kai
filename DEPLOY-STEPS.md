# What you need to do — Phase 5d, step by step

Everything that can be done without an account is **done and merged into PR #20**. What's left is
three accounts and a domain, because I can't create those for you. This file is only your half.
Mine is [`deploy/README.md`](deploy/README.md).

Work top to bottom. **Stop at the 🛑 and send me what it asks for** — I do the rest.

Rough time: **45–70 minutes**, most of it waiting on Oracle.
Cost: **~$12/year for the domain. Nothing else.**

---

## Step 1 — Buy a domain (~10 min, ~$12/yr)

This is the one thing in the plan that costs money, and it is not cosmetic. The refresh-token
cookie is `SameSite=Strict`, and browsers decide "same site" by **registrable domain**. If the
site is on `something.vercel.app` and the API is on `something.fly.dev`, the browser never sends
that cookie and **every page reload logs you out**. One domain, used for both, fixes it properly
instead of weakening the cookie.

- [ ] Pick a registrar. [Cloudflare Registrar](https://domains.cloudflare.com) sells at cost with
      no markup and no upsells; [Porkbun](https://porkbun.com) and Namecheap are fine too.
- [ ] Buy something short. `.dev` and `.app` are ~$12–15/yr and **force HTTPS in browsers**, which
      suits this project. `.com` is fine too.
- [ ] **Turn on auto-renew.** A lapsed domain here means the site and the login both break.

> **Note it down:** the domain, e.g. `fantasykai.dev`.

You do **not** need to configure DNS yet. That's Step 5.

---

## Step 2 — Make an SSH key (~2 min)

This is how you (and I) get into the server. Run it in this terminal:

```bash
ssh-keygen -t ed25519 -C "fantasykai-oracle" -f ~/.ssh/fantasykai_oracle
```

- [ ] Press Enter twice to skip the passphrase (or set one — you'll just type it on each login).
- [ ] Print the **public** half, which is what you paste into Oracle in Step 3:

```bash
cat ~/.ssh/fantasykai_oracle.pub
```

⚠️ The file **without** `.pub` is the private key. Never paste that anywhere, including to me.

---

## Step 3 — Oracle Cloud account (~15 min + verification wait)

[cloud.oracle.com/free](https://www.oracle.com/cloud/free/) → "Start for free".

- [ ] **A credit card is required for identity verification.** Always Free resources are never
      charged; Oracle places a temporary hold (~$1) and releases it. Your account stays in "Always
      Free" mode unless you deliberately upgrade — there is a clear "Upgrade to Paid" button, and
      the rule is simply never to press it.
- [ ] **🚩 The home region cannot be changed later.** It is the single irreversible choice in this
      whole process. You're on Central time, so **US Midwest (Chicago)** is the natural pick for
      latency. See the capacity note below before you commit.
- [ ] Verify your email, then sign in to the console.

### Then create the machine

In the console: **Compute → Instances → Create instance**.

- [ ] **Image:** Canonical Ubuntu (24.04 or whatever LTS is offered).
- [ ] **Shape:** click *Change shape* → **Ampere** → `VM.Standard.A1.Flex` → set **2 OCPUs** and
      **12 GB memory**. Confirm the form says **"Always Free eligible"**.
- [ ] **Networking:** accept the default VCN it offers to create, and make sure it assigns a
      **public IPv4 address**.
- [ ] **SSH keys:** choose *Paste public keys* and paste the output of `cat ~/.ssh/fantasykai_oracle.pub`.
- [ ] **Boot volume:** the default (~50 GB) is fine. Always Free allows 200 GB total.
- [ ] Create.

> **⚠️ "Out of host capacity" is normal, not a mistake you made.** Free ARM instances are in
> constant demand. If you hit it:
> - Retry — capacity frees up, often at odd hours. Trying over a day or two usually works.
> - Or try a different availability domain if your region offers more than one.
> - Or tell me, and we'll use the fallback: two `VM.Standard.E2.1.Micro` x86 instances (1 GB each,
>   also Always Free). It works, it's just a weaker machine and slightly more setup.
> - If neither pans out, the honest alternative is paying Fly $5.70/month, and that's your call.

### Open the firewall

- [ ] In the console: **Networking → Virtual Cloud Networks →** your VCN **→** the public subnet
      **→ Security List → Add Ingress Rules.** Add two, both with source CIDR `0.0.0.0/0`,
      IP protocol TCP, destination port **80** and **443**.

There's a *second* firewall inside the machine itself that also has to be opened — that one I'll
handle over SSH, since it's a command, not a form. It's the single most common reason an Oracle VM
looks dead when the console says it's running.

> **Note down:** the instance's **public IP address**.

---

## Step 4 — Vercel account (~3 min)

[vercel.com/signup](https://vercel.com/signup) → **Continue with GitHub**.

- [ ] Stay on the **Hobby** plan. It's free and needs no card.
- [ ] Authorize Vercel to see the `fantasy-kai` repo.
- [ ] Don't import the project yet — I'll do that with the right settings, since the root directory
      and the API URL both have to be set at import time.

---

## 🛑 Step 5 — Send me these four things

Paste them here and I'll take it from there:

1. **The domain** — e.g. `fantasykai.dev`
2. **The VM's public IP** — e.g. `129.146.x.x`
3. **The SSH user** — Oracle's Ubuntu images use `ubuntu`; the instance page shows it
4. **Your registrar** — just the name, so I can give you the exact DNS records to add

Then confirm: **can I run commands on that VM over SSH from this session?** If yes I'll do Steps
6–8 myself. If you'd rather run them, say so and I'll hand you a copy-paste script instead.

---

## What happens after that (my half, ~30 min)

| Step | What | Who |
|---|---|---|
| 6 | Open the VM's internal firewall, install Docker, clone the repo, generate the secrets | me |
| 7 | You add 3 DNS records at your registrar — I'll give you the exact values | **you**, 2 min |
| 8 | Start the stack; Caddy gets a Let's Encrypt certificate automatically | me |
| 9 | Copy the database up through an SSH tunnel (37 MB) and check row counts match | me |
| 10 | Import the frontend to Vercel, attach the domain | me |
| 11 | Run the 10 acceptance checks | me — except one |

**The one I'll ask you to do:** register an account on the live site, log in, and hard-reload the
page. If you're still logged in, the `SameSite` fix worked. That's the check this entire
single-domain decision exists for, and it can only be proven in a real browser.

**And one that spans a night:** at 06:00 ET the next morning, the daily ingest should fire on its
own with nobody watching. A run that happens when nobody triggered it is the whole point of this
phase — it's the failure that stopped the pipeline for three days in September and that nothing
noticed.

---

## Things worth knowing before you start

- **Never press "Upgrade to Paid" in the Oracle console.** Always Free stays free; upgrading
  converts the account and starts billing.
- **The home region choice is permanent.** It's the only step you can't undo.
- **Your laptop's daily ingest keeps running** against the local database after this. Local and
  production will drift apart, which is expected — `session-check.sh` now has a `DEPLOY` group that
  watches production separately.
- **Auth won't work on Vercel preview deploys**, only on the real domain. Previews are served from
  `*.vercel.app`, which is cross-site from your API, so the cookie won't be sent. Reads work; login
  doesn't. That's a known consequence, written down in `deploy/README.md` rather than left to be
  discovered.
- **You now own patching and backups.** That's the price of $0 and always-on. A `pg_dump` cron is in
  scope for me to add; storing those backups off the box is not, and it's genuinely owed.
