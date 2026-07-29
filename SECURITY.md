# Security Policy

MyFitnessLog is an offline-first workout tracker released as a **local production**
application: a signed Android app and a read-only web viewer, both talking to a backend
on a private network. This policy describes what security the project provides today,
what it deliberately does not, and how to report a vulnerability.

It distinguishes throughout between the **current project scope** (what the software
assumes and provides now) and **production recommendations** (what a public,
multi-user deployment would additionally require). The current scope makes deliberate
simplifications; they are documented here rather than presented as guarantees.

## Supported versions

This is a single-maintainer project. Security fixes are made against the latest release
and the `main` branch only.

| Version | Supported |
|---|---|
| Latest release (1.0.0) and `main` | ✅ |
| Older tags | ❌ |

## Reporting a vulnerability

Please report security issues **privately** — do not open a public issue for a
suspected vulnerability.

- Preferred: GitHub's private vulnerability reporting ("Report a vulnerability" under
  the repository's Security tab), if enabled.
- Alternatively: email the maintainer at **jeyaprakash1910@gmail.com** with a
  description, reproduction steps, and impact.

Because this is a personal project, there is no formal SLA. Reports are acknowledged and
addressed on a best-effort basis. Please allow a reasonable period for a fix before any
public disclosure (see [Responsible disclosure](#responsible-disclosure)).

## Security scope and deployment assumptions

Understanding the intended deployment is essential to understanding the security model.

### Local-first deployment

The application is designed for **personal or trusted-group use on a private network** —
you install the signed app yourself and run the backend on your own machine or LAN. It
is **not** a public, internet-facing product, and this version is not intended to be
deployed as one.

### The network is the trust boundary

**Current project scope.** The backend enforces an application-level API-key boundary
(ADR-0013): when `APP_API_KEY` is set, every request must present it in an `X-API-Key`
header and the API is default-deny, which makes it safe to expose beyond a trusted
network. This is app-level, not per-user, auth — the backend still attaches a single
default user server-side, so anyone holding the key can read and write the same workout
history. When the key is left unset (local or trusted-network use) the check is
disabled and access is entirely a function of **who can reach the backend on the
network** — treat the network boundary as the security boundary and run the backend
somewhere only trusted devices can reach it.

**Production recommendation.** A public or multi-user deployment must add
authentication and per-user authorization before exposing the backend beyond a trusted
network. Multi-user support is out of scope for this version.

### Transport security

**Current project scope.** The default local setup uses plain HTTP over a private
network. On Android, cleartext traffic is constrained by build type: debug builds permit
cleartext to any host (debug builds are never distributed), while **release** builds
permit cleartext only to the single backend host configured at build time and deny it
everywhere else. Pointing the configured backend URL at an `https://` endpoint removes
the cleartext exemption entirely.

**Production recommendation.** Serve the backend over TLS (`https://`) and configure the
app against that URL, so no cleartext exemption is needed. Terminate TLS at the backend
or a trusted reverse proxy.

### Data durability and secrets

- **Synchronization is one-way** (device → backend). The backend is the durable copy but
  cannot repopulate a device, so a device that loses its local database does not recover
  its history. This is a data-durability characteristic, not an access-control one, but
  it matters when planning backups (see the release checklist).
- **Signing material and machine-specific configuration are never committed.** The
  keystore, its credentials, and local backend URLs live outside the repository
  (enforced by `.gitignore`). The signing key is the app's identity; guard it
  accordingly.

## What this policy does not claim

To avoid overstating guarantees, the current version explicitly does **not** provide:

- user authentication or authorization;
- multi-tenant isolation (there is one shared default user);
- transport encryption by default in the local setup;
- protection against anyone with network access to the backend;
- server-side rate limiting, audit logging, or intrusion detection.

These are recognized as prerequisites for a public deployment, not defects of the
local-first scope.

## Responsible disclosure

We ask that reporters:

- report privately and give a reasonable opportunity to remediate before public
  disclosure;
- avoid accessing, modifying, or destroying data that is not their own while
  investigating;
- act in good faith and avoid privacy violations or service disruption.

Good-faith research conducted under these guidelines is welcome, and credit will be
given to reporters who wish it.

## Related documentation

- [docs/V1_RELEASE_NOTES.md](docs/V1_RELEASE_NOTES.md) — release scope and the
  local-production boundary.
- [docs/SYNC.md](docs/SYNC.md) — one-way synchronization and its data-recovery
  implications.
- [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) — signing, keystore custody,
  and backup procedure.
