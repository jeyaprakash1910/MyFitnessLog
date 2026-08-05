# Release Checklist

Project: MyFitnessLog
Version: 1.0
Last Updated: July 22, 2026 (M12 Phase 5 — v1.0.0 released)

The authoritative procedure for cutting a MyFitnessLog release. Work through it
in order; every step is here because skipping it has a specific consequence,
which is stated so the step can be judged rather than merely obeyed.

**Version 1 is a *local production release*.** A signed APK, installed by hand,
talking to a backend on the LAN. No public hosting, no TLS, no authentication —
those are Version 2 concerns (ROADMAP §18). The procedure below is written for
that reality and says explicitly where a hosted deployment would differ.

---

> **v1.0.0 released 22 July 2026 — every gate in §9b now passed.** The keystore
> backup was outstanding at tag time and was closed immediately after: three
> copies (local, external SSD, encrypted archive in cloud storage), with the SSD
> copy verified byte-identical *and* its fingerprint matched against the signer
> of the released APK — proof the backup can actually sign an update, not merely
> that a file was copied. Details in
> `docs/internal/MILESTONE_12_PHASE_05_IMPLEMENTATION.md`.

## 0. Prerequisites (one time)

### 0.1 The signing keystore

The keystore **is** the app's identity. Android refuses an update signed by a
different key, so losing it means never being able to update an installed app
again — the only remedy is uninstalling, which destroys the local database.
There is no rotation and no recovery.

Created once, with:

```bash
keytool -genkeypair -keystore ~/.keystores/myfitnesslog-release.jks \
  -alias myfitnesslog -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=MyFitnessLog, OU=Personal, O=MyFitnessLog, L=…, ST=…, C=…"
```

* Lives **outside the repository** (`~/.keystores/`, mode `600`).
* `.gitignore` refuses `*.jks`, `*.keystore` and `keystore.properties` as a
  second line of defence — but the first line is not putting it there.
* 30-year validity: a key that expires mid-life is a key that strands the app.

**Back up the keystore and its password**, to somewhere that survives this
machine dying. This is the single most irreplaceable file in the project — more
so than the source, which is on GitHub.

### 0.2 Credentials

In `android/local.properties` (gitignored), or the equivalent environment
variables for a CI machine:

| `local.properties` | Environment | Required |
|---|---|---|
| `releaseKeystorePath` | `MFL_KEYSTORE_PATH` | yes |
| `releaseKeystorePassword` | `MFL_KEYSTORE_PASSWORD` | yes |
| `releaseKeyAlias` | `MFL_KEY_ALIAS` | yes |
| `releaseKeyPassword` | `MFL_KEY_PASSWORD` | no — defaults to the store password |

If `releaseKeystorePath` is absent the build still succeeds and produces an
**unsigned** APK, warning as it goes. If it is present but the rest is not, the
build fails loudly rather than silently producing something uninstallable.

---

## 1. Decide the version

`android/version.properties` holds `versionName` and nothing else. Edit it, and
only it:

* **PATCH** — fixes only.
* **MINOR** — new user-visible capability, same data and sync contract.
* **MAJOR** — changed data or sync contract, or a version boundary.

`versionCode` is **derived** (`MAJOR * 10000 + MINOR * 100 + PATCH`) in
`app/build.gradle.kts`. Never set it by hand. Android rejects an upgrade whose
versionCode did not increase, and that presents as a mysterious install failure
rather than as the forgotten edit it actually is.

- [ ] `versionName` updated in `android/version.properties`
- [ ] The increment matches what actually changed

---

## 2. Verify the repository

- [ ] Working tree clean (`git status`)
- [ ] On `main`, up to date with `origin/main`
- [ ] Documentation consistent with the implementation — **checked against
      behaviour, not read for plausibility.** A confidently-worded document has
      twice been wrong in this project (ADR-0007, TD-004); both were reviewed
      and approved before anyone ran the query underneath them.

---

## 3. Verify the code

- [ ] `./gradlew :app:testDebugUnitTest` — green
- [ ] `mvn test` (backend) — green

- [ ] `npm run test` (web) — green

Live tests skip by default and cannot reach the production backend (TD-013,
resolved in M12 Phase 3). To exercise them, start the disposable backend and
name it explicitly:

```bash
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=livetest   # :8081
MFL_LIVE_TEST_BASE_URL=http://localhost:8081/api/v1/ \
  ./gradlew :app:testDebugUnitTest --tests '*LiveBackendSyncTest' --rerun-tasks
VITE_LIVE_TEST_BASE_URL=http://localhost:8081/api/v1 npm run test
```

- [ ] Live tests run against port **8081**, never 8080

> `--rerun-tasks` matters: Gradle does not treat environment variables as task
> inputs, so changing `MFL_LIVE_TEST_BASE_URL` alone leaves the test task
> UP-TO-DATE and it silently does not re-run.
- [ ] Migrations verified: Room v1→v5 and Flyway V1→V7, from an **empty**
      database and from real data. A new install exercises the empty path, and
      it is the one least often run.
- [ ] No test weakened or skipped to make a release-build difference disappear.
      If a test breaks under the release variant, that is the release process
      doing its job.

---

## 4. Back up the data

**Do this before installing anything.** ADR-0003 makes the backend the system of
record, and synchronization is one-way: PostgreSQL holds the only copy of
history that the phone cannot rebuild.

```bash
pg_dump -Fc myfitnesslog > myfitnesslog-$(date +%Y%m%d).dump
```

- [ ] Dump taken
- [ ] Dump **restored into a scratch database and row counts compared.** An
      unverified backup is a belief, not a backup.

### 4.1 The device database, if the build there is still debuggable

```bash
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db     > myfitnesslog.db
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db-wal > myfitnesslog.db-wal
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db-shm > myfitnesslog.db-shm
sqlite3 myfitnesslog.db "PRAGMA wal_checkpoint(TRUNCATE); PRAGMA integrity_check;"
```

- [ ] **All three files copied, not just `.db`.** Room runs SQLite in WAL mode.
      Measured on 2026-07-22: the database file was 4 KB and the write-ahead log
      was 272 KB. Copying `myfitnesslog.db` alone yields a file that opens
      without error and contains almost nothing — the worst kind of backup,
      because it looks like one.
- [ ] Checkpointed and `integrity_check` → `ok`

On a **release** build `run-as` is refused ("package not debuggable"), so this is
not possible — which is itself confirmation the build is a real release. Plan
device backups before installing a release build, not after.

- [ ] Row counts recorded, so a later restore can be checked against something

---

## 5. Build

```bash
cd android
./gradlew :app:assembleRelease
```

- [ ] Build succeeded
- [ ] The log did **not** warn about a missing signing configuration
- [ ] Signature verified:

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Expect `Verifies`, with `v2: true` and `v3: true`. **`v1: false` is correct** —
JAR signing is only needed below API 24 and `minSdk` is 26.

- [ ] Version confirmed: `aapt2 dump badging …` shows the expected
      `versionCode` and `versionName`

---

## 6. Check the network configuration

The release build permits cleartext to **exactly one host** — whatever
`apiBaseUrl` names — and forbids it everywhere else. The config is generated at
build time by `:app:generateReleaseNetworkSecurityConfig`, so it cannot drift
from the URL the app is actually pointed at.

- [ ] The build logged the expected host
- [ ] That host is the intended backend

For a hosted deployment, set an `https://` `apiBaseUrl`; the generator then
emits a config permitting no cleartext at all, and the exemption removes itself
rather than needing to be remembered.

---

## 7. Install

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

- [ ] Installed with `-r` (**install only**)

> **Never run `uninstall`, `connectedAndroidTest`, or any device-lifecycle
> Gradle task against the phone.** Uninstalling deletes the app's database, and
> because sync is one-way the phone cannot get its history back — this happened
> on 2026-07-22 and cost two routines and four sessions. The Gradle guard in
> `app/build.gradle.kts` now refuses these tasks on physical devices; do not
> reach for `-PallowPhysicalDeviceTests=true` to get around it.

If the install fails with:

```
Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.myfitnesslog
signatures do not match newer version; ignoring!]
```

the installed app was signed with a different key — typically a debug build.
Nothing has been damaged; Android refused the install and left the app alone, so
this is safe to discover by trying.

The only remedy is an uninstall, which destroys local data. Before doing it,
**find out what that data actually is** rather than assuming:

```bash
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db > /tmp/d.db
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db-wal > /tmp/d.db-wal
sqlite3 /tmp/d.db "select (select count(*) from routine), (select count(*) from workout_session);"
```

A phone holding zero routines and zero sessions loses only the exercise
catalogue, which re-downloads on first launch. A phone holding unsynced training
history loses it permanently, because sync is one-way. **Identical command,
completely different cost** — and the only way to know which you are facing is to
look. This is exactly the distinction the M11 incident turned on.

Note also that delaying does not make this safer: once real workouts are logged
on the device, the same uninstall becomes genuinely destructive.

---

## 8. Verify the running release build

Debug builds have different network policy, no logging and different manifest
flags. "It worked in debug" proves nothing about the artifact being shipped.

- [ ] App launches without crashing
- [ ] Exercise library loads (proves the network path and the cleartext scope)
- [ ] A routine can be created
- [ ] A workout can be logged: session, exercise, set
- [ ] The workout completes
- [ ] The data reaches PostgreSQL — **verified in the backend**, not inferred
      from the app not complaining
- [ ] History renders it

---

## 9. Tag and publish

- [ ] Commit any release-related changes
- [ ] Annotated tag matching the established style — a subject line, then what
      the release contains and what is verified:

```bash
git tag -a v1.0.0 -F - <<'EOF'
Milestone 12 — Version 1
…
EOF
git push origin main --follow-tags
```

- [ ] Push verified on the remote (`git ls-remote --tags origin`)
- [ ] Keystore backup still current
- [ ] GitHub Release published against the **existing** tag:

      ```bash
      gh release create vX.Y.Z --title "…" --notes-file docs/VX_RELEASE_NOTES.md --verify-tag
      ```

      Always pass `--verify-tag`. Without it `gh` creates a missing tag rather
      than failing, so a typo publishes a release pointing at whatever `main`
      happens to be.
- [ ] **The signed release APK attached to the GitHub release** (ADR-0016):

      ```bash
      gh release upload vX.Y.Z app/build/outputs/apk/release/app-release.apk
      ```

      This is what in-app updates actually serve. The backend resolves the
      repository's latest release, finds its `.apk` asset, and streams it to
      installed builds. Skip this and `GET /api/v1/app/latest-version` reports
      503: no phone is offered the update, and nothing announces the omission.

      Two things must hold or the release is invisible to installed builds:
      the **tag is `vMAJOR.MINOR.PATCH`** (anything else is refused rather than
      guessed at), and **exactly one `.apk` asset** is attached. The asset is
      matched by extension, so mapping files and checksums alongside it are fine.

      The release notes become the "What's new" text on the update screen, so
      write the body for the person reading it on a phone.
- [ ] Release verified: not a draft, and the asset list is what you intended
- [ ] **In-app update verified from the previously installed build**: open the
      app on the device that still runs the *old* version, confirm the banner
      offers the new one, and install through it. This exercises the whole path
      end to end - resolution, download, and the installer handoff - which no
      earlier step covers, and it is the only step that proves an already-installed
      copy can actually reach this release.

---

## 9b. Release gate

A release does not go out unless every one of these is true. Unlike the steps
above, which are procedure, these are the *properties* the release must have —
each one is here because its absence has already cost this project something.

- [ ] **Live tests executed only against the disposable backend** (port 8081,
      `disposable: true`). Never 8080.
- [ ] **Production row counts unchanged by the test run.** Check, do not assume:
      `psql -d myfitnesslog -At -c 'select count(*) from "Routine";'` before and
      after. This is the property TD-013 violated for months while every test
      passed.
- [ ] **Production database backup taken _and restored_**, with row counts
      compared. An unverified dump is a belief.
- [ ] **Test databases isolated** — backend tests on `myfitnesslog_test`, live
      tests on `myfitnesslog_livetest`; neither on `myfitnesslog`.
- [ ] **Keystore backup verified** in at least one location that survives this
      machine. Losing it makes every installed copy permanently un-updatable.
      Verify the *key*, not the file: its SHA-256 fingerprint must match the
      signer of the shipped APK.

      ```bash
      keytool -list -v -keystore <backup>.jks -storepass "$(cat <backup>.password)" | grep SHA256:
      apksigner verify --print-certs app-release.apk | grep 'SHA-256 digest'
      ```

      A copied file proves a copy exists. Matching fingerprints prove the backup
      can still sign an update to what was released.
- [ ] **Release APK verified as the artifact**, not as build configuration:
      `apksigner verify` passes, `aapt2 dump badging` shows the intended version,
      and `run-as` is refused.
- [ ] **The full workflow run on the physical device from the release build**,
      with the resulting data confirmed in PostgreSQL.
- [ ] **Documentation claims sampled against behaviour.** Every M12 audit found
      stale numbers in documents that read as correct.

---

## 10. Record it

- [ ] `TECH_DEBT.md` updated for anything the release surfaced or resolved
- [ ] `docs/internal/REVIEW_HISTORY.md` records the decisions made
- [ ] Implementation journal written in `docs/internal/`

---

## What this checklist deliberately does not cover

* **Play Store distribution** — V1 attaches a single default user server-side,
  so public distribution would give every installer the same account. That needs
  authentication (V2), not a checklist entry.
* **App bundles (`.aab`)** — only useful for store delivery.
* **R8 / minification** — off for V1. Room, Hilt, Retrofit and
  kotlinx.serialization all rely on generated code or reflection, so enabling it
  is a real risk for no benefit at this size. `proguardFiles` stays configured
  so turning it on later is a deliberate one-line change.
* **Hosted deployment and TLS** — see `DEPLOYMENT.md` (written, not executed).
