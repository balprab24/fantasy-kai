---
paths:
  - "deploy/**"
  - "scripts/**"
  - ".github/**"
  - "backend/Dockerfile"
  - "backend/pom.xml"
  - "backend/src/main/resources/application*.yml"
  - "docker-compose.yml"
  - "DEPLOY-STEPS.md"
---

# Deploy and ops rules

Loaded when Claude reads deploy, script, CI or build config. The full stories are in
`docs/traps.md` — grep the quoted phrase. The runbook and acceptance checks are in
`deploy/README.md`. **Nothing is deployed, pushed, or run against production without asking.**

- **The client IP is trusted only because Caddy overwrites `X-Forwarded-For` and the backend has no
  published port.** Never set `trusted_proxies`, publish the backend port, or put a CDN in front
  without re-running acceptance checks 4a–4c. → "auth rate limiter"
- **Caddy strips the forwarded headers it does not write** (`header_up -Forwarded` and the rest), and
  the app ignores them too. A trust boundary is a list of everything that crosses it. → "auth rate limiter"
- **A changed Caddyfile is not a reloaded Caddyfile.** It is bind-mounted, so `up -d --build` leaves
  Caddy running its old config whenever Caddy's own service definition did not change. Reload it
  explicitly (`caddy reload`), then verify by behaviour; `grep` on the file proves nothing.
- **A security header that is configured is not one that is sent.** HSTS needs
  `server.forward-headers-strategy: framework` behind a TLS-terminating proxy. Verify with `curl -sI`,
  not by reading config. → "security header"
- **A config value with no way to change it will be wrong.** Origins come from one comma-separated
  env var, and `CorsBindingTests` asserts the split. → "config value"
- **Probes hit `/actuator/health/liveness`, never the aggregate** — `ingestFreshness` DOWN means
  degraded, not dead. → "health indicator"
- **An mtime is not a fact about content.** The stale-jar guard compares a sha256 recorded by
  `scripts/package.sh`; build the ingest jar with it, never a bare `./mvnw package`. → "find -newer"
- **`java_home -v 25` exits 0 and returns 21 when 25 is absent.** Check what `java -version` prints,
  never the exit code. → "java_home"
- **A launchd plist with a placeholder is not an installed job** — use `install-ingest.sh`, and verify
  with `launchctl list | grep fantasykai`. → "launchd plist"
- **The laptop ingest fires in a macOS DarkWake and dies when the Mac goes back to sleep** (proven
  2026-09-24 from `pmset -g log`). Production runs its own 06:00 ET scheduler.
- **`scripts/db-restore.sh` writes to production.** Always ask first.
- **Spring Boot 3.5 is OSS-EOL.** `pom.xml` pins Tomcat 10.1.59; verify the pin took with
  `unzip -l backend/target/*.jar | grep tomcat-embed-core`. See north-star §10, row 11.5.
- **A green CI check is not a check that ran.** The OWASP dependency scan skips itself without
  `NVD_API_KEY` and still reports pass.
