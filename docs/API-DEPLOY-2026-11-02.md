# API deployment preparation — 2026-11-02

**Prepared only. No production changes, backup, restart, migration, ingest or acceptance attack
were run. The owner must explicitly authorize execution in this session.** This supplements
`deploy/README.md` §7; it does not authorize a deploy or merge.

## Evidence collected

- GitHub deployment record: Production frontend `72fbe500e195b99ba170d8cdf48c205d06580d41`,
  created 2026-10-31 21:54:59 UTC. Current `origin/main` is that commit.
- Read-only SSH to the API's DNS address, checked against the existing trusted host:
  VM checkout `7e585306b6636aca3871453bdad97e08cc078809`.
- Running container `fantasykai-backend-1`, created **2026-10-08T16:52:06.78607951Z**.
  Image `sha256:25dc3d637d8259b91d1e969a71a2199d181fde93e62330141de31bb0a356cfb6`,
  image creation `2026-10-08T16:52:02.546718099Z`.
- `unzip -l app.jar` inside the running container lists migrations V1–V6 and neither
  `LeagueController` nor `BoardController`. Database history lists V1–V6, all successful.
- Thus the handoff's September 29 last-deploy claim is incorrect. Missing league/board API code
  and missing V7–V11 are directly confirmed; the actual image commit is not asserted from the
  checkout alone (the image has no recorded commit label in this evidence).
- **Member HTTP check confirmed by Claude QA, relayed by the owner on 2026-11-03:** signed in
  at `http://www.fantasykai.com`, `GET /api/v1/leagues` returned **404** and My Teams showed
  “Your leagues could not be loaded”. This is supplied browser evidence, not a request Codex
  performed. It confirms the missing-route diagnosis alongside the running-jar and V6 evidence.

## Target and stop conditions

Target the reviewed **current main**, pinned initially to `72fbe500e195b99ba170d8cdf48c205d06580d41`.
If main advances (including the S2/S3 PR), inspect the added diff and rerun its gates before choosing
that newer SHA. S3 remains held; do not enable Trades or run another calibration attempt.
V7–V11 are applied by Flyway on application startup, never by hand. Applied migrations are immutable.
The projection job remains `fantasykai.projections.job-enabled=false`; scheduled ingest keeps its
existing 06:00 America/New_York schedule and enabled state.

## Exact execution sequence, after approval only

Use Bash for the arrays below. Never print `.env`, bearer tokens or `docker inspect` environment.

1. On the VM, check the state and save the running image identity:

   ```bash
   cd ~/fantasy-kai
   C=(docker compose --env-file deploy/.env -f deploy/compose.prod.yml)
   git status --short                         # stop on unexpected changes
   git rev-parse HEAD
   old_image=$(docker inspect --format '{{.Image}}' fantasykai-backend-1)
   docker tag "$old_image" fantasykai-backend:pre-leagues-20261102
   "${C[@]}" exec -T postgres psql -U fantasykai -d fantasykai -c \
     'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank'
   ```

2. Back up **before migrations**, copy off the VM, and restore that copy. Keep the backup outside
   the repository; it contains member data. Coordinate a quiet write window for count comparison.

   ```bash
   umask 077
   mkdir -p ~/backups
   backup=~/backups/pre-leagues-$(date -u +%Y%m%dT%H%M%SZ).dump
   "${C[@]}" exec -T postgres pg_dump -U fantasykai -Fc fantasykai > "$backup"
   test -s "$backup" && sha256sum "$backup"
   ```

   On the laptop, substitute the exact filename just printed:

   ```bash
   mkdir -p ~/fantasykai-backups
   scp -i ~/.ssh/fantasykai_oracle \
     ubuntu@163.192.220.190:backups/<exact-backup-filename> ~/fantasykai-backups/
   shasum -a 256 ~/fantasykai-backups/<exact-backup-filename>  # match VM checksum
   docker exec fantasy-kai-postgres-1 createdb -U fantasykai fk_deploy_restore_20261102
   docker exec -i fantasy-kai-postgres-1 pg_restore -U fantasykai \
     -d fk_deploy_restore_20261102 --exit-on-error < ~/fantasykai-backups/<exact-backup-filename>
   ```

   Resolve the local container with `COMPOSE_PROJECT_NAME=fantasy-kai docker compose ps -q postgres`
   if its name differs. Run this psql script against production and the restored scratch DB,
   save both outputs outside the repo, and compare every public table count:

   ```sql
   SELECT format('SELECT %L AS table_name, count(*) FROM %I.%I;', tablename, schemaname, tablename)
   FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename
   \gexec
   ```

   Stop if restoration fails or any unexplained count differs. Do not drop the scratch DB until
   the migration and rollback rehearsal below has passed.

3. Rehearse V7–V11 and **the actual old image** against the scratch DB, never the local development
   database or production. Export the tagged running image from the VM (`docker save`, copy,
   `docker load`) so the test uses its bytes, rather than assuming the VM checkout built it.
   Build the pinned target from a clean worktree. Supply scratch DB credentials and a fresh random
   local JWT through a mode-600 env file outside the repository. Start the target image on :18080,
   with `DB_URL=jdbc:postgresql://host.docker.internal:5433/fk_deploy_restore_20261102`, local Redis,
   and these explicit command arguments:

   ```bash
   docker run --rm --name fk-migration-rehearsal -p 127.0.0.1:18080:8080 \
     --env-file /private/tmp/fk-rehearsal.env --entrypoint sh fantasykai-backend:deploy-candidate \
     -c 'exec java $JAVA_OPTS -jar app.jar --fantasykai.projections.job-enabled=false --fantasykai.ingest.scheduled-enabled=false --fantasykai.ingest.backfill-on-startup=false'
   ```

   Confirm V11 and liveness. Stop only this rehearsal container, then boot
   `fantasykai-backend:pre-leagues-20261102` against the same upgraded scratch DB using the same
   flags. Confirm liveness and a real locally registered member's rankings request. The image
   warning about newer Flyway versions is expected; startup failure is a stop condition.
   Remove the rehearsal account, stop its container and drop only the named scratch database.
   Record the outcomes; **none of this rehearsal has been executed in this preparation**.

4. On the VM, fast-forward to the approved SHA and build. Do not enable the projection job:

   ```bash
   git fetch origin
   git switch main
   git merge --ff-only origin/main
   test "$(git rev-parse HEAD)" = '<approved-full-sha>'  # STOP if different
   # Verify the committed application.yml still has projections.job-enabled: false
   # and ingest.scheduled-enabled: true. Check only those keys and the cron,
   # including any existing runtime overrides; do not display the whole environment.
   "${C[@]}" build backend
   "${C[@]}" up -d --wait backend
   "${C[@]}" logs backend --since 5m
   "${C[@]}" exec -T postgres psql -U fantasykai -d fantasykai -c \
     'SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank'
   docker run --rm --entrypoint sh fantasykai-backend:latest \
     -c 'unzip -l app.jar | grep tomcat-embed-core'
   ```

   Require V7–V11 successful, healthy liveness, and Tomcat 10.1.59 (or a separately reviewed newer
   pin). Compare Caddyfile against the prior checkout; if changed, recreate Caddy using §7 step 6
   and verify its mounted contents. Never merely reload an inode-stale mount.
   V9 adds ingest-owned data; after approval, run the one-shot image command in §6 with projection
   job disabled, then query appearances/rosters/injuries to verify actual data. Scheduled ingest
   stays unchanged. Observe the next unattended 06:00 ET run as acceptance check 8.

5. Acceptance from the laptop, using owner-supplied member sessions (tokens only in private
   environment, never pasted into logs or shell command arguments):

   ```bash
   VM=163.192.220.190 ./deploy/acceptance.sh
   ```

   `PROD_TOKEN` and `LOCAL_TOKEN` are needed for check 9. A skipped check is not a pass. Check 3
   requires a real browser and reload, 4b a genuinely different client, 8 the next scheduled run.
   Check 9 compares the same historical season/profile so local freshness cannot explain drift.
   In the signed-in browser network panel verify `GET /api/v1/leagues` is **200 `[]`** for a member
   with no imported leagues. For an existing importer require 200 with their own list, not empty.
   Also verify `/api/v1/rankings/board` is 200 and My Teams no longer shows the interim message.
   Record status and response shape only, never cookies or Authorization headers.

6. If acceptance fails, roll back the image immediately:

   ```bash
   docker tag fantasykai-backend:pre-leagues-20261102 fantasykai-backend:latest
   "${C[@]}" up -d --no-build --wait backend
   ```

   Use the rehearsed old-image-on-V11 path. Never undo migrations manually. A database restore
   loses intervening writes and requires a separate owner decision. Restore the old Caddyfile
   and recreate Caddy if it changed; rerun liveness and member ranking checks.
