# Backup & Restore

**Goal:** 10 years of workout history must always be recoverable. Nothing fancier.

The database is the only irreplaceable asset in this system (code is in git, the app is
rebuildable). This document is the durable record of how it is backed up and — the part
that actually matters — how to get it back.

## What backs up, where, how often

- **What:** a full logical `pg_dump` of the Supabase database, gzipped.
- **How:** the [`Database Backup`](../../.github/workflows/backup.yml) GitHub Actions
  workflow. Daily at 02:17 UTC, plus a manual **Run workflow** button.
- **Where:** committed to an **orphan `backups` branch of this same repository**, under
  `dumps/myfitnesslog-<UTC-timestamp>.sql.gz`. The 60 most-recent dumps are kept in the
  branch's working tree (older ones are pruned from the tree but remain in history).
- **Reading the timestamps:** the cron and the filenames are both **UTC**. Judging "did
  today's backup run?" against a local date is how a healthy schedule gets mistaken for a
  failed one — from IST (UTC+5:30) the local day rolls over five and a half hours early,
  so the newest dump looks a day stale when it is current. Compare against
  `date -u`, not the local clock. Expect drift too: GitHub runs the 02:17 schedule
  40–60 minutes late under load, consistently, and that is normal rather than a fault.
- **Why this storage:** durable for the life of the repo, versioned, free, no second
  service, no third-party action, and it uses only the workflow's built-in token. Dumps
  are tiny (KB–MB), so the branch stays small.

## Secrets involved

One repository secret (Settings → Secrets and variables → Actions):

- **`SUPABASE_SESSION_URL`** — full connection string to the Supabase **session pooler**
  (port 5432), including password and `?sslmode=require`. Session pooler, not the
  transaction pooler (6543), because `pg_dump` needs a stable session.

Keep a copy of this connection string in your password manager too — if GitHub is lost,
you still need it to reach the database.

**Verified working in production 2026-08-03:** the `SUPABASE_SESSION_URL` secret is
configured and a manual `workflow_dispatch` run succeeded, producing the first real dump
(`dumps/myfitnesslog-2026-08-03T06-48-09Z.sql.gz`) on the `backups` branch. Prior to this
the secret was unset, so every scheduled run failed at the first step.

## Restore procedure (verified 2026-07-29)

The dump restores into a **fresh, empty** database. Roundtrip verified locally: a dump
restored into a new database reproduced exact row counts and preserved Flyway history.

1. Get the dump — from the `backups` branch:
   ```bash
   git clone --depth 1 --branch backups <repo-url> bk
   ls bk/dumps/            # newest timestamp = latest backup
   ```
2. Create a fresh target database (local example; for Supabase, target a new project):
   ```bash
   createdb -O <owner> myfitnesslog_restore
   ```
3. Restore:
   ```bash
   gunzip -c bk/dumps/myfitnesslog-<timestamp>.sql.gz | psql -d myfitnesslog_restore
   ```
4. Sanity-check:
   ```bash
   psql -d myfitnesslog_restore -c "select count(*) from workout_set;"
   psql -d myfitnesslog_restore -c "select count(*) from flyway_schema_history;"
   ```

The dump is taken with `--no-owner --no-privileges`, so it restores cleanly regardless of
which role owns the target.

## Wiping user data (performed 2026-08-18)

Returning production to seed-only: routines and workout history gone, the exercise
library and the default user kept. Done once, on 2026-08-18, to clear dogfooding data
before the first real training history was recorded.

**This is a two-sided operation, and the server half alone is not enough.** That is the
part worth reading before starting.

### The server

Deletion order is child-before-parent, so foreign keys hold at every point. Run it in a
transaction and check the counts *before* committing:

```sql
BEGIN;
DELETE FROM workout_set;
DELETE FROM workout_exercise;
DELETE FROM workout_session;
DELETE FROM routine_exercise;
DELETE FROM routine;
-- verify here, then:
COMMIT;
```

Keep `app_user`, `exercise_category`, `exercise`. Those are Flyway-seeded and their
counts double as proof you are on the right database: **1, 14, 313**. Losing the
`app_user` row breaks every write, because the client sends no `userId` and the backend
attaches the default user server-side.

### The devices, which is the half that surprises

Wiping the backend does **not** clear a phone, and the phone will not accept the
deletion. `RefreshManagerImpl.wouldDeleteEverything` treats an empty remote list as *the
backend lost its data*, withholds every deletion, and logs the remedy: clear the app's
data to restore from an empty backend deliberately. That guard is correct and should not
be worked around - it exists so a wiped or mispointed backend cannot destroy the
irreplaceable copy.

So each device holding real data must be handled directly, in this order:

1. **Airplane mode on** - before the server is touched. Closing the app is not enough:
   background sync runs through WorkManager and can upload while the app is shut. Any
   row still `PENDING` will otherwise re-populate the database you just cleared.
2. Wipe the server.
3. **Clear app data while still offline** - Settings, Apps, App management, MyFitnessLog,
   Storage usage, Clear data. Not *Clear cache*, which leaves the Room database intact.
4. Airplane mode off, then launch.

Only devices pointing at production need this. Since TD-018 a debug build resolves its
backend separately and defaults to local, so an emulator on `localhost:8080` holds local
dev data and must be left alone - clearing it destroys a dev fixture and protects
nothing. Verify rather than assume, against the **installed** APK rather than the
current source, since an APK built before TD-018 could still carry the production URL:

```bash
adb shell pm path com.myfitnesslog        # then pull and inspect the dex for the URL
```

### Afterwards: the first launch looks broken and is not

Clearing local data forces a foreground download of the exercise library, and the free
Render instance spins down when idle, so the first request pays a cold start - 22.4s
measured, 100.8s in one deploy log. The app waits correctly (`NetworkModule` allows a
120s read for exactly this) but the spinner is long enough to read as a hang. It is a
one-off; the library is cached afterwards. Background sync normally hides this because
WorkManager retries, which is why a manual clear is the situation that exposes it.

## Disaster-recovery tiers & assumptions

| Scenario | Recovery |
|---|---|
| Bad migration / accidental delete | Restore the latest daily dump into a fresh DB, point the backend at it. |
| Supabase project **paused** (free-tier idle) | Un-pause in the Supabase dashboard — data is intact, no restore needed. |
| Supabase project **deleted / lost** | Create a new Supabase project, restore the newest dump, update `SPRING_DATASOURCE_URL`/`FLYWAY_URL` on Render. |
| **GitHub account lost** | See "off-GitHub copy" below — this is the one gap the workflow alone does not cover. |

**Assumptions:** the backup and the primary data live in different services (Supabase vs
GitHub), so a single provider outage does not lose both. RPO ≈ 24h (daily dump); RTO is
minutes (restore is a single `psql`).

## The one manual habit that closes the last gap

The workflow protects against Supabase failure but **not against losing your GitHub
account**. A few times a year, download one dump to durable personal storage (external
drive / personal cloud):

```bash
git clone --depth 1 --branch backups <repo-url> bk && cp bk/dumps/$(ls bk/dumps | tail -1) ~/backups/
```

That is the difference between "GitHub has my backups" and "I have my backups." For a
10-year horizon, do it.

## Verification, now automated

An untested backup is a hope, not a backup. Since 2026-08-10 that is checked by
machine rather than remembered by a person.

`.github/workflows/backup-restore-test.yml` runs every **Monday at 03:41 UTC**,
after the nightly dump, and can be triggered by hand. It:

1. takes the newest dump from the `backups` branch and **fails if it is more than
   three days old**, which is how a silently stopped nightly backup gets noticed,
2. restores it into a throwaway PostgreSQL 17 service container, the same major
   version as production,
3. asserts the seeded reference data came back (at least 300 exercises, 14
   categories, 7 Flyway migrations), and
4. asserts **no orphaned rows** across sets, workout exercises and routine
   exercises.

Two of those choices are deliberate:

* **No floor on routines or workouts.** Those are the user's own data and zero is a
  legitimate value, so requiring a minimum would fail for the wrong reason the
  first time a database is reset. The seeded catalogue is the floor instead,
  because it distinguishes "restored" from "restored an empty shell".
* **Integrity is checked, not just counts.** A dump that restores rows but loses
  the relationships between them is worse than one that fails outright, because it
  looks like a backup.

The restore itself runs with `ON_ERROR_STOP` off. A Supabase dump carries that
platform's own schemas and extensions, some of which do not exist on stock
Postgres, and those failures say nothing about whether the training data survived.
Measured on 2026-08-10 against a real dump: **zero errors**, 313 exercises, 7
migrations, no orphans.

**Weekly rather than nightly, on purpose.** What this catches is the dump format
becoming unrestorable, which changes when Postgres or Supabase change, not daily.
A nightly run would mostly re-prove the same thing and add noise to a repository
where a red check is meant to mean something.

The manual procedure below still applies for a real disaster, and the restore path
was last exercised by hand on 2026-07-29 and again on 2026-08-10 while cutting
1.6.0.

## Future improvements (not needed now)

- **The branch does grow without bound — see TD-020.** This list previously said it
  "won't, for a personal log", and that was measured and found wrong on 2026-08-18.
  Not because a personal log is large, but because `KEEP` prunes the working *tree*
  while git history retains every blob forever, and because gzipped dumps cannot be
  delta-compressed against one another — so each night costs a full copy of a diary
  that is ~80% unchanging seed data. Projection: ~3.8 GB by year ten, against a ~5 GB
  practical ceiling. Nothing to do yet (the branch is 1.1 MB); TD-020 carries the
  numbers, the tripwire to add first, and the fix to reach for when it fires.
- If you outgrow the free tier, Supabase's own PITR/backups become available; this
  workflow remains a good independent second copy.
- Match `PG_IMAGE` in the workflow to your Supabase Postgres major version if it changes.
