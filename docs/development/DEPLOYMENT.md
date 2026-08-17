# Deployment Guide (canonical)

Deploy the full MyFitnessLog production system from this repository alone. Written so a
future you, on a new laptop, can go from clone to running system using only this file and
the docs it links.

**Topology:** Android (offline-first) → Spring Boot on **Render** → PostgreSQL on
**Supabase**; future React web on **Vercel**. Backend is the only writer of record.

> Read once end-to-end before starting. The ordering exists to avoid rework (e.g.
> verifying against Supabase locally *before* deploying to Render).

---

## 1. Prerequisites (local tooling)

- **JDK 21** (backend targets Java 21). Maven 3.9+. See the backend build note in repo
  memory / `docs/development/TESTING.md`.
- **Android Studio / JDK 17+** for the Android build; `keytool` (bundled with the JDK).
- **`psql`** client (for verification / restore).
- **git**, a **password manager** (see `SECRETS.md`).

## 2. Required accounts & services

| Service | Purpose | Plan |
|---|---|---|
| **Supabase** | Managed PostgreSQL | Free |
| **Render** | Hosts the Spring Boot backend | Free |
| **GitHub** | Repo, Actions (backups), auto-deploy | Free |
| **Vercel** | Future React web client | Free |

## 3. Secrets you will need

All values come from your password manager (`SECRETS.md`). You will set:

- Supabase: DB password; **transaction** pooler URL (`:6543`); **session** pooler URL
  (`:5432`).
- `APP_API_KEY` — generate once: `openssl rand -hex 32`.
- `APP_UPDATE_REPOSITORY` and `APP_UPDATE_GITHUB_TOKEN` - **optional**, enables in-app
  Android updates (ADR-0016). The token is a GitHub fine-grained PAT with **read-only
  Contents** access to this repository only. Leave both unset and the `/api/v1/app/*`
  endpoints answer 503, which the app treats as "no update to offer"; nothing else
  changes. The token must never be copied into the Android app: an APK is
  distributed, so any secret inside it is a published secret.
- These become environment variables on Render (see `backend/.env.prod.example`) and a
  GitHub Actions secret for backups.

---

## 4. Supabase configuration

1. Create a project; save the DB password to the password manager immediately.
2. **Settings → Database → Connection string**: copy both
   - **Transaction** (`...pooler...:6543`) → runtime,
   - **Session** (`...pooler...:5432`) → Flyway & backups.
   Append `?sslmode=require` to each. Note the **Postgres major version**
   (Settings → Infrastructure) — you need it for the backup workflow's `PG_IMAGE`.
3. Do **not** run migrations by hand — the backend runs Flyway on boot (next step).

## 5. Backend: verify locally against Supabase, then deploy

**5a. Local smoke test (catches pooling issues before Render):**
```bash
cd backend
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL="<transaction-pooler-url>"      # :6543 ?sslmode=require
export SPRING_DATASOURCE_USERNAME="postgres.<ref>"
export SPRING_DATASOURCE_PASSWORD="<db-password>"
export FLYWAY_URL="<session-pooler-url>"                     # :5432 ?sslmode=require
export FLYWAY_USER="postgres.<ref>"
export FLYWAY_PASSWORD="<db-password>"
export APP_API_KEY="<from openssl rand -hex 32>"
export WEB_ALLOWED_ORIGINS=""                               # set when web deploys
mvn -o spring-boot:run
```
Confirm: profile `prod` active, Flyway `Successfully validated N migrations`, Hikari
starts, `Started MyFitnessLogApplication`. Then, from another shell:
```bash
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/health              # 200
curl -s -o /dev/null -w '%{http_code}\n' localhost:8080/api/v1/exercises           # 401
curl -s -o /dev/null -w '%{http_code}\n' -H "X-API-Key: $APP_API_KEY" \
  localhost:8080/api/v1/exercises                                                    # 200
```
Run a create + delete to exercise a write and a tombstone. Confirm **no**
`prepared statement "S_1" does not exist` under repeated calls (proves `prepareThreshold=0`).

**5a-bis. Docker smoke test (matches how Render runs it — ADR-0015):**
```bash
cd backend
docker build -t mfl-backend:local .
# Reuse the SAME env file that scripts/run-prod-local.sh sources — one local source
# of truth for prod secrets, no duplicate copy in the repo. -e PORT=8080 simulates
# Render's injected port. (docker --env-file needs plain KEY=value: no `export`, no
# quotes, no $-expansion — which is exactly this file's format.)
docker run --rm -p 8080:8080 -e PORT=8080 \
  --env-file ~/.config/myfitnesslog/prod.env mfl-backend:local
```
Then re-run the §5a `curl` checks against `localhost:8080`. This proves the image before
it ever reaches Render.

**5b. Deploy to Render:**
1. New **Web Service** from the GitHub repo. **Root directory = `backend/`** (mono-repo —
   a wrong root is the most common first-deploy failure). Runtime = **Docker** (Render no
   longer offers a native Java runtime — ADR-0015); Render builds from `backend/Dockerfile`,
   so the build/start-command fields do not apply — the image's ENTRYPOINT starts the app.
   Enable auto-deploy on `main`.
2. Set env vars from `backend/.env.prod.example` (all of 5a, `SPRING_PROFILES_ACTIVE=prod`).
   **If `APP_API_KEY` is missing the app refuses to start — by design.**
3. Set Render's **health check path** to `/api/v1/health` (liveness — do not use a
   DB-dependent check).
4. After deploy, repeat the `curl` checks against the Render HTTPS URL.

**5c. What a successful first deploy looks like (Docker on Render).**

*Recorded observation vs. expectation:* the log block below is a **verbatim record of
the first production deploy (2026-07-30)** — specific values (startup ~100s, port
`10000`, `7 migrations`) are what that deploy produced, not guarantees. Startup time
depends on free-tier CPU load; the injected port and migration count can differ. What is
*stable* across deploys is the **order** of the milestones.

- **Expectation — build duration:** the *first* build should take several minutes
  because Docker downloads the Maven dependencies inside the build stage; later builds
  should be faster as Render reuses cached Docker layers (unless `pom.xml` changes). A
  build still streaming `Downloading from central: ...` after a few minutes is normal,
  not stalled.
- **Observation — URL pattern:** Render appended a random suffix, producing
  `https://myfitnesslog-kp5o.onrender.com`. Expect the general form
  `https://<service-name>-<suffix>.onrender.com`, **not** a bare
  `https://<service-name>.onrender.com`. Take the exact URL from the deploy log's
  `Available at your primary URL ...` line and use it for Android's `apiBaseUrl`.
- **Observation — startup log sequence from the 2026-07-30 deploy** (the *order* is what
  to expect again; the timings/port are this run's values):
  ```
  ==> Deploying...
  The following 1 profile is active: "prod"
  ==> No open ports detected, continuing to scan...   # benign — app still booting
  Successfully validated 7 migrations                 # Flyway on the :5432 session pooler
  Schema "public" is up to date. No migration necessary.
  HikariPool-1 - Start completed.
  Tomcat started on port 10000 (http)                 # Render's injected $PORT (this run)
  Started MyFitnessLogApplication in 101 seconds      # this run; free-tier CPU is slow
  ==> Your service is live 🎉
  ```
  The `No open ports detected` line appears *before* Tomcat binds and resolves itself
  once the port opens — it is not an error.
- **Post-deployment verification checklist:**
  - [ ] Deploy reached `==> Your service is live 🎉`.
  - [ ] `GET /api/v1/health` → 200 (no key).
  - [ ] Protected endpoint → 401 without a key, 200 with the correct key.
  - [ ] `GET /api/v1/exercises` returns the seeded catalog (proves DB read path).
  - [ ] Android points at the exact `-<suffix>.onrender.com` URL and syncs a workout.

**5d. Android against the live backend.** Only `android/local.properties` changes
(gitignored — not a repository change): set `apiBaseUrl` to the Render URL (with the
`-<suffix>`) and `apiKey` to the same value as `APP_API_KEY`. No app code changes are
required — the release network-security config auto-drops its cleartext exemption once
`apiBaseUrl` is `https://`.

*Verification performed (2026-07-30) and its scope:* the debug app was built, installed,
and launched on an emulator against the live backend, and the sync path was exercised
through the app's **production Retrofit/OkHttp stack** (its real API interfaces, DTOs,
and the live URL + API key). Confirmed: the exercise catalog (313 exercises, 14
categories — this record said 12, which was wrong on the day: V2 seeds 8 and V6 adds
6, and the 313 exercises confirm V7 had run) downloads, and a full create→complete workout round-trip (session → exercise
→ set → complete) persisted to Supabase as a single `COMPLETED` session with no
duplicates (the test row was then removed). **Scope limit:** the full Compose *UI*
workflow (tapping through the workout screens) was **not** driven end-to-end because the
available emulator's performance was too degraded to do so reliably; the network/data
path it depends on was verified instead. A device-driven UI pass is still worth doing
when convenient.

**5e. Known operational characteristics (not defects).** Behaviours a future maintainer
may notice and mistake for problems:

- **Deploys are verified, not assumed.** The `post-deploy` workflow runs on every
  push to main and polls `GET /api/v1/health` until its `commit` field matches the
  pushed SHA, then confirms the instance still reports `disposable: false`. Render
  sets `RENDER_GIT_COMMIT` automatically, so that field is the deployed build's
  identity rather than a guess. If the job times out, the deploy did not happen or
  is still building: check the Render dashboard before assuming the code is wrong.

- **Free-tier cold start.** Render spins the free instance down after inactivity, so the
  first request after a quiet period cold-starts the container (order of ~1-2 min based
  on the 2026-07-30 boot; 100.8s in the 2026-08-05 deploy log, 22s measured 2026-08-10).
  Subsequent requests are fast.

  This paragraph used to end "OkHttp's default 10s timeout can trip on that first call
  ... left unchanged deliberately, no client timeout/retry change until real usage shows
  it is needed." **Real usage showed it on 2026-08-10**: a phone running 1.5.0 reported
  "could not reach the server" and could not see the 1.6.0 release at all, because the
  update check runs once on launch with no retry. Background sync had been hiding the
  same fault, because WorkManager retried and the second attempt found a warm server.

  The client now allows **120s to read and 15s to connect** (`NetworkModule`), pinned by
  `OkHttpTimeoutTest`. Connect stays short so a genuinely dead network still fails fast;
  it is the read that has to be patient, because a sleeping instance accepts the
  connection immediately and only then starts booting. There is deliberately **no
  `callTimeout`**, because the in-app update streams an 8.7 MB APK through the same
  client. A timeout is also now reported as "the server is waking up, try again in a
  moment" rather than as unreachable, since trying again is the entire remedy.
- **First Docker build is slow.** The first build downloads all Maven dependencies;
  later builds reuse Render's cached layers.
- **Generated URL suffix.** The service URL carries a Render-generated suffix (e.g.
  `-kp5o`). Read it from the deploy log; do not assume `https://<service-name>.onrender.com`
  and do not hardcode the URL anywhere except the single `apiBaseUrl` config.

## 6. GitHub configuration (backups)

1. Repo → Settings → Secrets and variables → Actions → add **`SUPABASE_SESSION_URL`**
   (the `:5432` session-pooler string, with password + `?sslmode=require`).
2. In `.github/workflows/backup.yml`, set `PG_IMAGE` to your Supabase Postgres major.
3. Actions → **Database Backup** → **Run workflow**; confirm the `backups` branch appears
   with a dump. Do one **test restore** per `BACKUP.md`.

## 7. Android configuration

1. `android/local.properties` (gitignored) — signing (see `SECRETS.md`), the backend URL
   (`apiBaseUrl`), and the **API key** (`apiKey`). The build injects
   `BuildConfig.API_BASE_URL` and `BuildConfig.API_KEY`; an `ApiKeyInterceptor` then adds
   `X-API-Key` to every request automatically.
   ```properties
   apiBaseUrl=https://<your-backend>.onrender.com/api/v1/
   apiKey=<the same value as Render's APP_API_KEY>
   ```
   Alternatively set the `MFL_API_KEY` environment variable (useful in CI). If `apiKey`
   is blank the header is omitted (fine for a local backend with auth disabled); a
   **release** build with no key **warns**, because the secured backend will 401.
2. Build a **signed release APK** (`assembleRelease`); confirm it is signed (an unsigned
   release warns, it does not fail) and that no "No apiKey configured" warning appeared.
3. Log one full offline workout; confirm it syncs and appears via the backend.

## 8. Vercel (future web client)

Deploy the React app; set its API base URL + API key; add its origin to
`WEB_ALLOWED_ORIGINS` on Render. (Deferred until the web client exists.)

---

## 9. Deployment order (summary)

Supabase → local prod smoke test → Render → GitHub backups → **verify** → Android release
→ (later) Vercel. Android is last because rebuilding an APK is the slowest feedback loop;
validate the API with `curl`/web first.

## 10. Verification checklist

- [ ] `prod` profile boots; Flyway validated; Hikari up.
- [ ] `/api/v1/health` → 200 without a key.
- [ ] Protected endpoint → 401 without / with wrong key, 200 with correct key.
- [ ] Repeated calls: no prepared-statement errors (pooler config correct).
- [ ] Backup workflow produced a dump; **a test restore succeeded**.
- [ ] Signed release APK installs; a real workout syncs end-to-end.

## 11. Rollback considerations

- **Bad backend deploy:** Render keeps prior deploys — roll back to the previous one in
  the dashboard. Auto-deploy is off `main`, so reverting the commit also reverts prod.
- **Bad migration / data damage:** restore the latest dump into a fresh DB and repoint
  `SPRING_DATASOURCE_URL`/`FLYWAY_URL` (see `BACKUP.md`). Flyway is forward-only — never
  edit an applied migration; add a new one.
- **Bad APK:** reinstall the previous signed APK (keep the last known-good one).

## 12. Common mistakes

- Swapping the pooler ports (runtime on `:5432`, Flyway on `:6543`) — appears to work,
  fails intermittently. Runtime = `:6543`, Flyway = `:5432`.
- Dropping `?sslmode=require` from a URL.
- Forgetting `APP_API_KEY` on Render (prod refuses to start — that's the safety net).
- Forgetting `apiKey` in Android `local.properties` (release build warns; sync 401s if ignored — see §7).
- `PG_IMAGE` older than the Supabase server major (backup `pg_dump` version error).
- Pointing Render's health check at a DB-dependent endpoint (crash-loops when Supabase idles).

## 13. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `prepared statement "S_x" does not exist` | server-side prepared stmts under txn pooler | ensure `prepareThreshold=0` (set in `application-prod.yml`); confirm runtime URL is `:6543` |
| App refuses to start, `APP_API_KEY is required` | prod profile, no key | set `APP_API_KEY` |
| Flyway advisory-lock / migration hang | Flyway on the transaction pooler | point `FLYWAY_URL` at `:5432` |
| Android syncs all 401 | app not sending `X-API-Key` | wire `BuildConfig.API_KEY` + interceptor |
| First request after idle fails | connection closed by Supabase idle cutoff | `max-lifetime` already < cutoff; retry succeeds; confirm value |
| Backup `pg_dump: server version mismatch` | `PG_IMAGE` too old | bump to the Supabase major |

## 14. Disaster recovery & related docs

- **Backups / restore / DR tiers:** [`BACKUP.md`](BACKUP.md)
- **Secrets & keystore custody / new-machine recovery:** [`SECRETS.md`](SECRETS.md)
- **Prod config rationale (pooling, health):** [`../internal/2026-07-29-production-configuration.md`](../internal/2026-07-29-production-configuration.md)
- **Docker deployment rationale:** ADR-0015, `../../backend/Dockerfile`
- **Auth boundary:** ADR-0013, [`../internal/2026-07-29-api-key-auth.md`](../internal/2026-07-29-api-key-auth.md)
- **Schema convention:** ADR-0012
- **Android release/signing:** [`../RELEASE_CHECKLIST.md`](../RELEASE_CHECKLIST.md)
- **Cutover plan (bundle status):** [`CLOUD_CUTOVER_PLAN.md`](CLOUD_CUTOVER_PLAN.md)
