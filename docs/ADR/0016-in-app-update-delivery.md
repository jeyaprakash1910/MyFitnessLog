# ADR-0016 - The backend delivers app updates from GitHub Releases

Date: 2026-08-05
Status: Accepted
Related: ADR-0013 (API-key auth boundary), ADR-0015 (Docker deployment on Render),
docs/RELEASE_CHECKLIST.md, docs/API_SPECIFICATION.md,
`backend/src/main/java/com/myfitnesslog/service/GitHubAppUpdateService.java`,
`android/app/src/main/java/com/myfitnesslog/feature/update/`

## Context

MyFitnessLog is distributed as a signed APK outside any app store. Until now
every update reached the phone the same way: build locally, then
`adb install -r` over USB. That has two consequences that get worse over time.

An installed build has **no way to learn that a newer one exists**. Nothing on
the phone knows the project's version, so an old APK stays old silently. The
practical trigger for this decision was exactly that: UI work had shipped to
`main` while the phone still ran the previous build, and the only way to find
out was to remember.

And updating requires **a cable and a laptop**. That is a fine developer
workflow and a poor user one, even when the user is the developer, because it
means updates happen when convenient rather than when released.

Two constraints shape the options:

* **The repository is private.** GitHub release assets on a private repository
  require a credential to download, so the phone cannot simply fetch a release
  URL. Embedding a GitHub token in the APK is not an option: the APK is
  distributed, so any secret in it is a published secret (the same reasoning
  ADR-0013 accepted for the API key, which is scoped to this one app's own data,
  does not extend to a token with repository access).
* **The backend already exists and is already trusted.** It runs on Render
  (ADR-0015), it is reachable from the phone anywhere, and the app already
  authenticates to it on every request (ADR-0013). Any update mechanism that
  reuses it introduces no new host, credential, or account.

## Decision

**The backend tells the app what the latest release is and serves its APK. The
artifact store is GitHub Releases, and the backend is the only party that can
read it.**

Two endpoints, both behind the existing `X-API-Key` boundary:

* `GET /api/v1/app/latest-version` returns `{ versionName, releaseNotes,
  publishedAt, sizeBytes }`, derived from the repository's latest published
  release.
* `GET /api/v1/app/apk` streams that release's `.apk` asset.

`GitHubAppUpdateService` resolves both from the GitHub API using a fine-grained
PAT with **read-only Contents access to this one repository**, supplied as the
`APP_UPDATE_GITHUB_TOKEN` environment variable. The token is a server-side
secret and is never sent to a client. Two details of that API are load-bearing
and are asserted by tests rather than trusted:

1. An asset download must target the asset's *API* URL with
   `Accept: application/octet-stream`; `browser_download_url` 404s for a private
   repository.
2. That request answers 302 to a pre-signed object-storage URL, and the redirect
   must be followed **without** the `Authorization` header, because object
   storage rejects a request bearing two auth mechanisms. Redirects are therefore
   disabled on the HTTP client and the second hop is issued deliberately.

**The version comparison lives on the client.** The backend reports what the
latest release *is*; it never says whether an update is available, and does not
send a `versionCode`. Only the client knows what it is running, and answering it
server-side would put the MAJOR.MINOR.PATCH ordering rule in two codebases.
`AppVersion` on Android parses both sides into ordered integer fields, mirroring
the `versionCode` formula already in `app/build.gradle.kts`.

**The version comes from the release tag.** A tag must match `v?MAJOR.MINOR.PATCH`
or the endpoint reports 503 rather than guessing; `version.properties` stays the
single source of truth and the tag is derived from it at release time. There is
no second place to bump.

**Unconfigured is a valid deployment.** With `app.update.*` unset the endpoints
answer **503** and the app simply never offers an update. Nothing else is
affected, and the existing test suite and local development are unchanged.

**Failure is silent by design.** Every network, parsing, and configuration
problem becomes one quiet `UpdateStatus.Unavailable`, which the UI renders as
"can't check right now" and the banner does not render at all. Deliberately
distinct from `UpToDate`: "I could not ask" and "you are current" are different
claims, and merging them would let the app state the second while meaning the
first. An update check is a background courtesy and must never interrupt a
workout.

On the phone: a dismissible banner above top-level screens is what surfaces an
available update (suppressed during a workout), a Settings > About row shows the
installed version and doubles as the manual entry point, and the update screen
shows the release notes and download progress before handing the file to the
platform installer through a narrowly scoped `FileProvider`. The app requests
`REQUEST_INSTALL_PACKAGES`, which only permits *asking*; the platform still shows
its own confirmation, and the one-time "install unknown apps" toggle cannot be
granted by the app.

## Alternatives considered

**Bake the APK into the backend's Docker image.** Simplest possible hosting, no
GitHub API client. Rejected: it requires committing a ~13 MB binary per release
to Git, permanently, and couples every app release to a backend redeploy. Repo
bloat is unrecoverable, and the coupling is exactly backwards - the app and the
backend version independently.

**Supabase Storage for the APK.** Workable, and the project already uses Supabase
for Postgres. Rejected as a second artifact store for no gain: it adds an upload
step to the release procedure and a bucket to manage, while GitHub Releases
already holds the tag, the notes, and the source for the same build. Remains the
natural fallback if the GitHub dependency ever becomes a problem.

**Firebase App Distribution.** Purpose-built, gives the in-app update prompt and
hosted downloads with almost no code. Rejected: it adds a Google dependency and
account to a stack that has none, moves distribution outside the repo, and
delegates the update UI to an SDK. Reconsider if there are ever real testers
beyond the author.

**A separate public repository for release APKs.** Would let the phone download
directly with no token and no backend involvement. Rejected: it makes every build
world-downloadable to solve a problem the backend already solves, and splits
releases across two repositories.

**Hand the client a short-lived pre-signed GitHub URL instead of proxying bytes.**
Saves the backend the bandwidth. Rejected for V1: it adds URL expiry as a failure
mode, sends the client to a second host, and the traffic is one APK per release
for one user. Worth revisiting only if bandwidth ever matters.

**Full auto-update (download and install without asking).** Not possible without
being a device owner or system app, and not desirable - installing a new build
mid-workout is precisely what the banner design avoids.

## Consequences

Positive:

* An installed build learns about a new release on its own, anywhere, over the
  network it already uses. No cable, no laptop.
* No APK is committed to Git or baked into the deploy image; GitHub Releases
  stays the single home for a release's artifact, tag, and notes.
* No new host, service, account, or client credential. The GitHub token is
  server-side, read-only, and scoped to one repository.
* Releasing is unchanged apart from attaching the APK to the GitHub release,
  which the procedure already creates.
* The endpoints degrade to 503 when unconfigured, so this is inert on any backend
  that does not opt in.

Negative / accepted:

* **The release procedure gains a required step.** An APK must be attached to the
  GitHub release, and the tag must be `vMAJOR.MINOR.PATCH`. Forget either and the
  endpoint reports 503 - visible on the update screen, but only if someone looks.
  RELEASE_CHECKLIST.md §9b covers it.
* **The backend proxies the download**, so a release-sized transfer crosses the
  Render instance. Streamed rather than buffered so it cannot exhaust heap, but
  it is bandwidth the backend did not previously carry.
* **A GitHub API dependency in the backend**, including its rate limit and
  availability. Mitigated by scope (one authenticated call per app launch, out of
  5000/hour) and by every failure being a silent 503. Not cached; a per-instance
  TTL cache is the obvious first move if that ever changes.
* **`REQUEST_INSTALL_PACKAGES` in the manifest**, and a one-time system toggle the
  user must grant. Unavoidable for store-less distribution, and the permission
  only allows requesting an install the platform still confirms.
* **The update check reaches the backend on launch**, which on Render's free tier
  may hit a cold start. Harmless - it fails quietly and the next launch retries -
  but it does mean the first check after an idle period usually reports
  unavailable.
