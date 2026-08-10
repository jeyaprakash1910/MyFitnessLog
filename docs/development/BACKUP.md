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

- Bump `KEEP` or add weekly/monthly tiers if history ever grows large (it won't, for a
  personal log).
- If you outgrow the free tier, Supabase's own PITR/backups become available; this
  workflow remains a good independent second copy.
- Match `PG_IMAGE` in the workflow to your Supabase Postgres major version if it changes.
