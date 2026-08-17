---
name: run-on-device
description: Launch and drive the MyFitnessLog Android app on an emulator or physical device - install it, tap through screens, take screenshots, inspect the Room database, and verify a change in the real app rather than only in tests. Use whenever asked to run the app, screenshot it, reproduce a bug end to end, or confirm UI behaviour on hardware. Read this BEFORE the first adb command, to confirm which backend the build is pointed at.
---

# Running MyFitnessLog on a device

## Read this first: confirm which backend you are pointed at

Since TD-018 was resolved (2026-08-17) the debug build defaults to a **local**
backend and cannot inherit production: `debugApiBaseUrl` is resolved separately
from `apiBaseUrl`, and a build configuring both to the same URL fails outright.

Verify rather than assume, because the value is compiled in. Regenerate before
reading it — `BuildConfig.java` is a build output, absent before the first build and
stale after any `local.properties` edit, so grepping it blind can report the
*previous* build's backend:

```bash
cd android && ./gradlew -q :app:generateDebugBuildConfig
grep API_BASE_URL app/build/generated/source/buildConfig/debug/com/myfitnesslog/BuildConfig.java
```

`installDebug` also prints `[debug] backend: <url>` on every build, which is the
same value from the same function.

A `localhost`/`10.0.2.2` URL, or the livetest backend on `:8081`, is disposable and
needs no special care. **If it shows the Render URL, someone set `debugApiBaseUrl`
deliberately — stop and use the offline write protocol below.** That backend is the
**system of record** (ADR-0003) and the only permanent copy of the owner's workout
history, because sync is one-way from the phone. On launch the app pulls real data
down (ADR-0017 Stage 2), every UI write is queued and uploaded by `SyncWorker`, and
deleting a set writes a tombstone (ADR-0007) that removes the row from the backend
too, with no undo and no other copy.

The local backend needs to be running and reachable, or the app simply queues
everything as `PENDING` — which is valid offline-first behaviour and looks like a
sync bug if you were not expecting it:

```bash
cd backend && mvn spring-boot:run          # default profile, local Postgres, no API key
adb reverse tcp:8080 tcp:8080              # makes "localhost" mean this machine
```

`adb reverse` works on an emulator and a physical device alike, and does **not**
survive a reconnect or an emulator restart — re-run it.

## Setup

`adb` and `emulator` are not on `PATH`:

```bash
export PATH=$PATH:~/Library/Android/sdk/platform-tools:~/Library/Android/sdk/emulator
```

Available AVDs (verified 2026-08-12: only `Pixel_7` exists; the `mfl_test` AVD
named in several docs is gone):

```bash
emulator -list-avds
emulator -avd Pixel_7 -no-snapshot-load   # run in background; boot takes ~1 min
adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
```

## Build and install

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-r` reinstalls in place and **preserves the database**. Never `adb uninstall`, and
never `connectedAndroidTest`: it uninstalls both APKs afterwards and an uninstall
deletes the Room database. That destroyed real training data on 2026-07-22
(CODING_STANDARDS §20c). The build refuses it against a non-emulator target, which
is a safety net, not permission.

To run instrumented tests on hardware, build and install both APKs and invoke the
runner directly - this performs no uninstall:

```bash
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.myfitnesslog.test/com.myfitnesslog.HiltTestRunner
```

The test APK is not produced by `assembleDebug`, so the assemble step above is
required - `CODING_STANDARDS` §20c omits it and the install fails without it.

Back the database up first regardless:

```bash
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db > backup.db
```

The database uses WAL, so the `.db` file alone can be nearly empty while the real
content sits in `.db-wal`. Copy both (M12 Phase 2 found a "backup" that was 4 KB
against a 272 KB WAL).

## Launch and drive

```bash
adb shell monkey -p com.myfitnesslog -c android.intent.category.LAUNCHER 1
adb shell input tap <x> <y>
adb shell input text "72.5"
adb exec-out screencap -p > shot.png     # then read the image
```

**Look at every screenshot.** A blank frame is a failed launch, not a pass.

Coordinates: the screen is 1080x2400. Screenshots are usually presented scaled to
900x2000, so **multiply the coordinates you read off an image by 1.2** to get the
tap target. Getting this wrong silently taps the wrong control - a text field can
receive input meant for the one below it, and the result looks like an app bug.

The soft keyboard shifts dialog content upward. Re-screenshot after it opens
rather than reusing coordinates measured before it appeared.

Bottom navigation sits at y=2224 with five evenly spaced tabs, so their centres
are x = 108, 324, 540, 756, 972 for Home, Workout, Exercises, History and
Settings. History (756, 2224) is confirmed by use; the rest are derived from the
spacing, so screenshot after tapping rather than trusting them blind.

## Writing safely against a production-pointed build

Only needed when the check at the top of this file shows the Render URL — that is
no longer the default, so this should be rare and deliberate. Use it whenever such
a run has to *change* something, not merely look at it.

1. Launch **online** and let the app download real data, so the screen shows a
   realistic state. This is read-only: only GETs are issued.
2. Go offline **before the first write**:

   ```bash
   adb shell cmd connectivity airplane-mode enable
   adb shell settings get global airplane_mode_on   # expect 1
   ```

3. Do the writes. They land in Room and queue as `PENDING`; nothing can upload.
4. Verify against the database, not only the screen (see below).
5. Destroy the outbox **while still offline**, so the queued writes can never
   reach the backend:

   ```bash
   adb shell pm clear com.myfitnesslog
   ```

6. Restore the network and confirm the backend is untouched:

   ```bash
   adb shell cmd connectivity airplane-mode disable
   adb logcat -d | grep -cE 'OkHttpClient: --> (PUT|POST|DELETE)'   # expect 0
   ```

   Then re-read the affected session with `curl` and check the values are what
   they were. The API key lives in `android/local.properties` as `apiKey`; pass it
   as `X-API-Key` (ADR-0013). Never paste the key into a committed file.

Step 6's zero is the check that matters. Anything above zero means a write escaped
and the backend needs looking at.

## Inspecting the database

```bash
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db > m.db
adb exec-out run-as com.myfitnesslog cat databases/myfitnesslog.db-wal > m.db-wal
sqlite3 m.db "select setNumber, weight, repetitions, syncStatus from workout_set;"
sqlite3 m.db "select count(*) from workout_set_tombstone;"
```

Pull both files. `sqlite3` on the device is unreliable to quote through
`adb shell`; pulling and querying locally avoids it. Delete the copies afterwards
- they contain real training data.

Useful things to assert: `syncStatus = 'PENDING'` proves a write was queued for
upload, and a row in `workout_set_tombstone` proves a deletion will propagate. A
delete without a tombstone is the bug ADR-0007 exists to prevent.

## Finishing

```bash
adb emu kill
```

Remove any pulled `.db` files. If you cleared app data, say so in your report:
the emulator is left empty and re-downloads on next launch.

## Reporting

State which target you ran on (emulator or hardware, and which), what you drove,
and what the screenshots showed. If the run wrote anything, say explicitly whether
it reached the backend and how you know.

Note what is verified versus assumed. A physical-device pass and an emulator pass
are not the same claim: manufacturer background-execution policy, doze, and real
network behaviour only appear on hardware (TD-005).
