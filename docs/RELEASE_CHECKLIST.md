# Release Checklist

Project: MyFitnessLog
Version: 1.0
Last Updated: July 22, 2026 (M12 Phase 1)

The authoritative procedure for cutting a MyFitnessLog release. Work through it
in order; every step is here because skipping it has a specific consequence,
which is stated so the step can be judged rather than merely obeyed.

**Version 1 is a *local production release*.** A signed APK, installed by hand,
talking to a backend on the LAN. No public hosting, no TLS, no authentication —
those are Version 2 concerns (ROADMAP §18). The procedure below is written for
that reality and says explicitly where a hosted deployment would differ.

---

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
- [ ] Migrations verified: Room v1→v5 and Flyway V1→V6, from an **empty**
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

Optionally, the device database too — though on a release build `run-as` is
unavailable, which is itself confirmation the build is not debuggable.

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

If the install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the installed
app was signed with a different key (typically a debug build). The only fix is
an uninstall, which destroys local data — **back up first and understand what is
lost.** This is why the first install of a signed release should happen on a
device whose data is expendable, or knowingly.

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
