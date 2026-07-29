# Build-Now Bundle — Cloud Cutover Operational Floor

Status: Proposed
Date: 2026-07-28
Owner: Jeya Prakash S
Related: SYNC.md, ADR-0003 (backend is source of truth), ADR-0006 (JPA auditing),
docs/ADR/0007 (tombstones), application.yml

---

## Context

MyFitnessLog is architecturally sound and has a mature one-way sync engine, but it
has **never been deployed** and lacks the operational floor a public, long-lived
personal system needs. Three reviews converged on the same conclusion: the remaining
work is **operational maturity, not redesign**, and the highest-value next step is to
make the system durable and *used*, not more built.

This plan covers the five "Build now" items that must land **before** the Supabase +
Render cutover — everything else is deliberately deferred to after daily usage. The
intended outcome: a deployed, authenticated, backed-up system running on
Supabase/Render/Vercel that Present-Me can start daily-driving on a signed APK.

Non-goals (explicitly out of scope here): pull-direction sync, real JWT/Supabase Auth,
multi-device, analytics, the web client. Those are gated on real need per the roadmap.

---

## Work Items

### 1. Schema de-quoting → `snake_case` (biggest item; do first) — ✅ DONE (2026-07-28, ADR-0012, 92/92 tests green)

**Problem.** Every table AND column is a quoted, case-sensitive identifier:
`@Table(name = "\"WorkoutSet\"")`, `@Column(name = "\"workoutExerciseId\"")`, and
`"User"` is a reserved word. This forces exact-quoting in every hand-written query,
`psql` session, `pg_dump` inspection, and future BI/analytics tool — a decade-long
paper-cut. Cheapest to fix now, while Supabase is greenfield (no production data).

**Approach — rewrite the baseline, do not ALTER.** Because Supabase has no data yet,
rewrite the Flyway migrations to emit `snake_case` unquoted identifiers rather than
shipping a rename migration. Local dev DBs get rebuilt from scratch.

- Rename tables: `"User"` → `app_user` (avoid reserved word), `"ExerciseCategory"` →
  `exercise_category`, `"Exercise"` → `exercise`, `"Routine"` → `routine`,
  `"RoutineExercise"` → `routine_exercise`, `"WorkoutSession"` → `workout_session`,
  `"WorkoutExercise"` → `workout_exercise`, `"WorkoutSet"` → `workout_set`, plus the
  `workout_set_tombstone` shape if any backend table references it.
- Rename all columns to `snake_case` (`workoutExerciseId` → `workout_exercise_id`,
  `setNumber` → `set_number`, `startedAt` → `started_at`, …).
- Files: `backend/src/main/resources/db/migration/V1__Initial_schema.sql` through
  `V7__*.sql` (V2–V7 are seeds/indexes that reference the renamed identifiers — update
  every reference). Keep version numbers; this is a baseline rewrite, valid only
  because no environment has these applied durably yet.
- **Entities:** strip the embedded quotes from every `@Table`/`@Column`/`@JoinColumn`
  in `backend/src/main/java/com/myfitnesslog/entity/*.java` (all 8 mapped entities +
  `AbstractAuditableEntity`). With unquoted `snake_case` names and Hibernate's default
  naming, most `@Column` annotations can be **deleted entirely** (camelCase field →
  snake_case column happens automatically) — prefer deletion over renaming to reduce
  surface. Keep explicit `@Column` only where type/precision/nullability is declared.
- Update `physical-strategy` in `application.yml`: the current
  `PhysicalNamingStrategyStandardImpl` preserves names verbatim. Switch to Spring
  Boot's default `CamelCaseToUnderscoresNamingStrategy` so field→column mapping is
  automatic and the annotations can be dropped.

**Blast radius.** Android is unaffected — it talks REST DTOs, never table names. The
future web client is unbuilt. So this is a backend-only change.

**Verify.** Rebuild backend, run the existing DAO/repository tests against a fresh
local Postgres (drop/recreate), confirm Flyway applies cleanly and seeds load. Record
the decision in a new ADR (see below).

### 2. API-key authentication boundary — ✅ DONE (2026-07-29; ADR-0013, docs/internal/2026-07-29-api-key-auth.md; enforcement probed, 92/92 tests green)

**Problem.** The API will be a public, unauthenticated write endpoint to personal
workout history the moment it hits Render. SYNC.md §17 already flags "must not assume
anonymous access beyond V1."

**Approach.** Add `spring-boot-starter-security`, then a single
`SecurityFilterChain` + a `OncePerRequestFilter` that checks a static `X-API-Key`
header against `app.api-key` (env var `APP_API_KEY`). Default-deny all routes except
`/actuator/health` and the OpenAPI/Swagger endpoints. Keep CORS (`CorsConfig`) working
for the web origin.

- New: `backend/src/main/java/com/myfitnesslog/config/SecurityConfig.java` and an
  `ApiKeyAuthFilter`.
- Structure it so B → JWT/Supabase Auth later is a swap of one filter, not a rewrite
  (this is the "cheap now, upgradeable" seam).
- Android/web send the key; on Android surface it via `BuildConfig` from a gitignored
  `local.properties` (never commit the key).

**Verify.** `curl` an endpoint without the header → 401; with the correct header →
200; `/actuator/health` reachable without a key.

### 3. Production datasource config (Supabase pooling) — ✅ DONE (2026-07-29; Actuator dropped — reused DB-free `/api/v1/health` as Render's liveness check; see docs/internal/2026-07-29-production-configuration.md)

**Problem.** Supabase's transaction pooler (Supavisor `:6543`) breaks pgjdbc
server-side prepared statements and has a small connection budget; Flyway needs the
session pooler (`:5432`). Default Hikari pool size (10) will exhaust the free tier.

**Approach.** Add `backend/src/main/resources/application-prod.yml` (activated by
`SPRING_PROFILES_ACTIVE=prod`) with:

- App datasource → **transaction pooler** URL with `?prepareThreshold=0&sslmode=require`,
  Hikari `maximum-pool-size: 3`, `minimum-idle: 0`, `max-lifetime: 1500000`.
- Flyway → **session pooler** URL (`:5432`, `sslmode=require`) via separate
  `spring.flyway.url/user/password`.
- All credentials from env vars (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`,
  `FLYWAY_URL/USER/PASSWORD`) — nothing sensitive committed.
- Add `spring-boot-starter-actuator`, expose only `/actuator/health` (used as Render
  health check + Android reachability probe).

**Verify.** Run backend locally with `--spring.profiles.active=prod` pointed at the
real Supabase project. Hammer an endpoint repeatedly (>5 calls) to confirm NO
`prepared statement "S_1" does not exist` error — that proves `prepareThreshold=0`
works under the pooler. Confirm Flyway history + seeds present via session pooler.

### 4. Backups + one tested restore — ✅ DONE (2026-07-29; .github/workflows/backup.yml, BACKUP.md, docs/internal/2026-07-29-backup-strategy.md; restore roundtrip verified locally)

**Problem.** Free Supabase can pause/vanish; the data is the entire point of the app.

**Approach.** Add `.github/workflows/backup.yml`:

- Daily `pg_dump` against the **session pooler** (transaction pooler can choke on
  dumps), gzipped; `workflow_dispatch` for manual runs.
- Store as workflow artifact (retention 30d) and/or commit to a **private** repo.
- Retention: 7 daily + 4 weekly + 12 monthly (prune in-workflow).
- Credentials via GitHub Actions secret `SUPABASE_SESSION_URL`.
- Write `docs/development/BACKUP.md` with the **tested** restore commands
  (`createdb` → `gunzip | psql`) and the disaster-recovery tiers (bad migration →
  restore daily; project paused → un-pause; project lost → new project + newest dump +
  repoint Render env var).

**Verify.** Run the workflow manually once; then do ONE real restore into a scratch
DB and record the exact commands. An untested backup does not count as done.

### 5. Keystore + secrets custody — ✅ DONE (2026-07-29; SECRETS.md, DEPLOYMENT.md, docs/internal/2026-07-29-secrets-and-deployment.md)

**Approach (checklist, not code).**

- Generate the release signing keystore; back up the keystore file + passwords to a
  password manager off-machine. Losing it = can never update the same app identity.
- Back up the Supabase DB password + `APP_API_KEY` to the same manager.
- Confirm `local.properties`, keystore, and any `.env` are gitignored.

---

## Documentation to produce alongside

- **ADR — schema identifier convention** (`snake_case`, why, and that it was done
  pre-cutover while greenfield). This is the "boring expensive decision" that deserves
  an ADR more than some existing ones.
- **ADR — API-key as the V1 auth boundary** (why not JWT yet, the upgrade path).
- **ADR — dual-pooler datasource** (transaction pooler runtime, session pooler Flyway;
  load-bearing and easily "simplified" back into a bug).
- **`docs/development/DEPLOYMENT.md`** — the Phase 0→6 cutover checklist.
- **`docs/development/BACKUP.md`** — dump + tested restore + DR tiers.
- Rename "event-driven" → "state machine" in ADR-0008/0009 headings/prose (docs-only,
  low priority, batch it in).

---

## Execution Order

1. Schema de-quoting (#1) + its ADR — largest, everything else assumes final names.
2. Prod datasource config + actuator (#3) — needed to verify anything against Supabase.
3. API-key auth (#2) + its ADR.
4. Backup workflow (#4) + BACKUP.md + one tested restore.
5. Keystore/secrets custody (#5).
6. DEPLOYMENT.md, then execute the actual Render/Supabase/Vercel cutover.
7. Sign the APK, install on phone, log a real workout. **Then stop and use it.**

## End-to-end Verification

- Backend builds; existing tests green against a fresh `snake_case` local DB.
- `--spring.profiles.active=prod` against Supabase: repeated calls, no prepared-stmt
  error; Flyway clean via session pooler.
- `curl` without API key → 401; with key → 200; `/actuator/health` open.
- Backup workflow run manually → artifact produced → restored into scratch DB.
- Signed APK installed; one full offline workout logged and confirmed synced to
  Supabase (query the `snake_case` tables directly to confirm).

---

## Deliberately NOT in this plan (gated on real need)

Server-authoritative timestamp column (before web *write*, not now) · read-only web
client (portfolio trigger, after daily use) · pull sync / change_log (second writer) ·
real auth (second user) · multi-tenancy (commercializing) · keep-warm cron (only if
cold-start latency actually annoys). Per the roadmap, daily usage — not anticipation —
promotes anything off this list.
</content>
</invoke>
