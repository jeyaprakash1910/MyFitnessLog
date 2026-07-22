# MyFitnessLog — Version 1.0.0 Release Notes

**Released:** 22 July 2026
**Tag:** `v1.0.0`
**Scope:** local production release — a signed Android app and a read-only web
viewer, both talking to a backend on your own network.

---

## 1. Overview

MyFitnessLog is an offline-first workout tracker. You plan routines, log sets
during a session with no network at all, and the device uploads them when a
backend becomes reachable. A read-only web client renders the same history in a
browser.

Version 1 is deliberately a **local production release**: personal or
trusted-group use on a private backend. It is not a public product. There is no
authentication — the backend attaches a single default user server-side — so
anyone who can reach the backend reads and writes the same history. Multi-user
support is Version 2.

What that means practically: install the signed APK yourself, run the backend on
your own machine or LAN, and treat the network boundary as the security boundary.

## 2. Major capabilities

**Routines.** Create, rename, duplicate and delete routines; add exercises with
target sets, a rep range and rest time; reorder and remove them.

**Workouts.** Start from scratch or from a routine, add exercises mid-session,
log sets with weight, reps, RPE/RIR and a set category (warmup, working, top
set), run a rest timer, then complete or discard. The elapsed clock is derived
from the start timestamp, so it survives process death rather than counting the
time the app happened to be alive.

**Exercise library.** 313 exercises across 14 categories, downloaded from the
backend and cached locally. Search and filter by category, in a curated order the
backend controls rather than alphabetical.

**History.** Every completed workout is an immutable snapshot — the exercise
name and targets as they were on the day, not as they are now. Renaming or
withdrawing an exercise never rewrites what you did.

**Synchronization.** One-way, Android → backend, in the background. Everything
works offline; writes queue locally and upload when a backend is reachable.

**Web viewer.** Read-only history list and full workout detail, responsive from
mobile to desktop, with zero axe WCAG 2.1 A/AA violations. Decimal weights are
preserved from PostgreSQL `NUMERIC` to rendered text rather than being lost to
JavaScript floats.

## 3. Architecture summary

Three components, with one rule that decides most questions: **the backend is
the system of record, and Room is the device's own source of truth** (ADR-0002,
ADR-0003).

| | Stack |
|---|---|
| Android | Kotlin, Jetpack Compose, Room, Retrofit, Hilt, WorkManager |
| Backend | Java 21, Spring Boot 3.4, PostgreSQL, Flyway, Spring Data JPA |
| Web | React 18, TypeScript, Vite, TanStack Query, Tailwind |

### Offline-first, in practice

The app never waits on the network to let you train. Every write lands in Room
first and is marked `PENDING`. A WorkManager job claims pending rows, uploads
them in dependency order (routine before its exercises, session before its sets),
and marks them `SYNCED`. Failures become `FAILED` and are retried; a claim that
is interrupted mid-flight is recovered rather than stranded.

Uploads are idempotent — the client generates UUIDs, so replaying an upload
cannot create duplicates.

### Synchronization is one-way, and that matters

Data flows device → backend only. The backend is the durable copy, but it cannot
repopulate a device. **A phone that loses its database does not get its history
back**, even though the backend still has it. This is a deliberate V1
simplification, and the single most important thing to understand before
uninstalling anything.

### History is immutable

Workout history stores snapshots, not references (ADR-0001, ADR-0004). Deleting
an exercise from the catalogue does not remove it from the workouts that used
it; RESTRICT foreign keys enforce that at the database level.

## 4. Testing

**567 automated tests.** By default 558 run and 9 skip.

| Suite | Count |
|---|---|
| Android | 342 (336 JVM/Robolectric + 6 instrumented) |
| Backend | 92 (JUnit 5 / MockMvc against real PostgreSQL) |
| Web | 133 (Vitest + React Testing Library) |

Backend tests run against a real PostgreSQL rather than an in-memory substitute,
so Flyway migrations, quoted identifiers, CHECK constraints and `NUMERIC`
precision are all exercised faithfully.

The nine live tests drive the real stack against a running backend. They skip
unless a target is named explicitly, and **fail** rather than skip if that target
does not declare itself disposable — so no test can write to the system of
record.

Verified on real hardware: OnePlus CPH2717, Android 16, against a live backend
and PostgreSQL.

## 5. Engineering improvements from M11 and M12

Version 1's last two milestones added almost no features. They changed how the
project verifies itself, and that is what made it releasable.

* **Routine deletions actually propagate.** They never had. The pending-sync
  query excluded soft-deleted rows, so a deletion was discarded rather than
  deferred. No tombstone mechanism was needed — the fix was to let the uploader
  see the row it already had.
* **Reference data reconciles.** Withdrawn exercises are removed from devices
  instead of lingering forever.
* **Destructive Gradle tasks refuse to run on physical devices.** After an
  `uninstall` wiped the daily-use phone's database, the rule became structural
  instead of documentary. Detection uses device properties, not adb serials.
* **The release build is signed and verified as an artifact** — `apksigner`,
  packaged resources and `run-as` refusal — rather than trusted because Gradle
  was configured correctly.
* **`versionCode` is derived** from a single `versionName`, so it cannot be
  forgotten.
* **Cleartext is scoped to one host** in release builds and disappears entirely
  once the backend URL becomes HTTPS.
* **Automated tests can no longer reach the production backend.** Live tests
  require an explicitly named target that declares itself disposable.
* **Backups are verified by restoring them**, and device backups copy the WAL —
  the Room database file was 4 KB while its write-ahead log held 272 KB.

## 6. Known limitations

* **No authentication.** Single default user attached server-side. Anyone who can
  reach the backend has full access.
* **Synchronization is one-way.** A new or wiped device starts empty; it cannot
  pull history back.
* **No hosted deployment or TLS.** Release builds permit cleartext to exactly one
  configured host. Deployment is documented but not executed.
* **History is not paginated** (TD-010). Every completed session is returned in
  one response. Invisible at present scale; it will not stay that way.
* **The web client is read-only** by design.
* **R8/minification is off.** Room, Hilt, Retrofit and kotlinx.serialization all
  rely on generated code or reflection; the app is small enough that shrinking
  buys nothing worth the risk.
* **Not verified:** Firefox and Safari, a real screen-reader pass, and long-idle
  background behaviour on ColorOS.
* **The production database contains historical test data** — 71 of 76 routines
  are artifacts of a test that used to write there (TD-013, fixed in M12). They
  are identifiable by a `Live ` name prefix and were deliberately left in place
  rather than deleted without the owner's decision.

## 7. Deferred to Version 2 and beyond

**V2:** authentication, multi-user support, user profiles, a dashboard, and
pull/bidirectional synchronization.
**V3:** weight, sleep and water tracking; progress photos.
**V4:** Health Connect, Apple Health, wearables, cloud backup.
**V5:** nutrition, analytics, AI insights.

Carried debt: TD-001 (405 returned as 500), TD-002, TD-009, TD-010 (pagination),
TD-012. All are note-only or deliberately deferred; none blocks V1.

## 8. Upgrade notes

Version 1.0.0 is the first release, so there is nothing to upgrade from.

For future releases:

* `versionName` in `android/version.properties` is the only version anyone edits.
* Updates must be signed with the same keystore. **Android refuses an update
  signed by a different key**, and the only remedy is uninstalling — which
  destroys the local database that one-way sync cannot restore.
* Room migrations are additive and tested from an empty database and from real
  data. Never use destructive migration.
* Follow `docs/RELEASE_CHECKLIST.md`, including the release gate in §9b.

## 9. Lessons that shaped this release

Recorded because they were expensive, and because they generalise:

1. **A confidently-worded document can be wrong.** ADR-0007 and TD-004 both
   asserted routine deletions propagated. Both were reviewed and approved. Nobody
   ran the query underneath the sentence.
2. **A test can be the reason a bug survives.** `soft-deleted routines are
   excluded from pending work` passed for months while encoding the defect.
3. **A rule you have to remember is a rule you will break.** Written device-safety
   procedure did not prevent the data loss; a build guard did.
4. **A safeguard encodes an assumption, and assumptions expire silently.** The
   live-test gate was correct until dogfooding put a backend on localhost
   permanently. Nothing failed at that moment — it just stopped protecting
   anything.
5. **Ask the question you actually mean.** "Is a backend reachable?" is not "is
   this backend disposable?"
6. **Verify the artifact, not the configuration.** The signing block claimed three
   signature schemes; the APK had two.
7. **Inspect before you warn.** A predicted catastrophic uninstall turned out to
   cost 313 re-downloadable rows — the facts had changed since the prediction.
8. **A backup is a claim until it is restored.**
9. **Documentation drift is immediate.** A Phase 3 change left stale references
   in two documents within a day, written by the person who made the change.
10. **Clone the repository to audit the repository.** A correct-looking
    `.gitignore` rule made the web client unconfigurable from a fresh clone.
