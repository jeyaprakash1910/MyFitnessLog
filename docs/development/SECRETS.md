# Secrets & Keystore Custody

**Goal:** never lose the ability to (a) update the Android app, (b) reach the database,
or (c) redeploy — even years later on a new machine. Not enterprise secret management;
a repeatable manual process built on one password manager plus two offline copies.

## Guiding principle

There are two kinds of secret here, and they need different treatment:

- **Rotatable** — `APP_API_KEY`, database password, GitHub secrets. If lost or leaked,
  generate a new one and update the consumers. Inconvenient, not fatal.
- **Irreplaceable** — the **Android signing keystore**. It *is* the app's identity.
  Android rejects an update signed by a different key. Lose it and you can never update
  the installed app again; you must ship a new app with a new identity and reinstall.
  **This one secret gets the most protection.**

## Where the single source of truth lives

**A password manager** (Bitwarden, 1Password, KeePassXC — any that you control and can
export). Create a vault/folder **`MyFitnessLog`**. Everything below lives there. The
keystore *file* also gets two offline copies (see its section).

Nothing secret is ever committed to git. This is enforced by `.gitignore`
(`*.jks`, `*.keystore`, `keystore.properties`, `.env`, `.env.*` with template
exceptions) and by config that reads secrets only from env vars or `local.properties`.

## The inventory (what to store, and where each is consumed)

| Secret | Stored in | Consumed by | Rotatable? |
|---|---|---|---|
| **Android keystore file** (`.jks`) | Password-manager attachment **+ 2 offline copies** | `assembleRelease` (path via `local.properties`/env) | ❌ No — irreplaceable |
| Keystore store password | Password manager | signing config | ❌ (bound to the keystore) |
| Key alias | Password manager | signing config | ❌ |
| Key password | Password manager | signing config | ❌ |
| `APP_API_KEY` | Password manager | Render env; Android release build | ✅ |
| Supabase DB password | Password manager | Render env; `SUPABASE_SESSION_URL` | ✅ (via Supabase dashboard) |
| Supabase connection strings (txn + session pooler) | Password manager | Render env; GitHub `SUPABASE_SESSION_URL` | ✅ |
| Supabase account login | Password manager | dashboard access | ✅ |
| Render account login | Password manager | dashboard / deploy | ✅ |
| GitHub account + recovery codes | Password manager | repo, Actions, backups branch | ✅ |
| GitHub Actions secret `SUPABASE_SESSION_URL` | GitHub Secrets (mirror in PW manager) | backup workflow | ✅ |

> Store the **values** in the password manager, and note in each entry **which service
> consumes it**, so a new-machine setup is a copy-down, not a re-derivation.

## The Android keystore — the one that matters most

The signing config (`android/app/build.gradle.kts`) reads, from `local.properties` or
environment variables:

| `local.properties` key | Env var | Meaning |
|---|---|---|
| `releaseKeystorePath` | `MFL_KEYSTORE_PATH` | absolute path to the `.jks` |
| `releaseKeystorePassword` | `MFL_KEYSTORE_PASSWORD` | store password |
| `releaseKeyAlias` | `MFL_KEY_ALIAS` | key alias |
| `releaseKeyPassword` | `MFL_KEY_PASSWORD` | key password (defaults to store password) |

**Custody rule — the keystore file lives in at least three places:**
1. Your working machine (referenced by `local.properties`, which is gitignored).
2. Your password manager, as a file **attachment** on the keystore entry.
3. One **offline** copy — an encrypted USB drive or a personal encrypted archive.

Back up the *file* and the *passwords together* (a keystore without its password is as
useless as no keystore). Verify the copy actually opens:
```bash
keytool -list -keystore <path-to-copy>.jks    # prompts for the store password
```
If that lists the alias, the copy is good.

**Creating the keystore (once), for reference** — see `docs/RELEASE_CHECKLIST.md`; the
canonical command is:
```bash
keytool -genkeypair -v -keystore myfitnesslog-release.jks \
  -alias myfitnesslog -keyalg RSA -keysize 4096 -validity 10000
```
`-validity 10000` (~27 years) matters: an expired signing cert cannot sign updates, and
for a 10-year app you do not want to rediscover this in year 3.

## Recovering everything on a new machine (the repeatable process)

1. Sign in to the **password manager**; open the `MyFitnessLog` vault.
2. **Backend/deploy:** log into Render and Supabase; the env-var values are in the vault
   and mirrored by `backend/.env.prod.example`. Nothing to rebuild — Render already
   holds them; you only need them if recreating the service.
3. **Android:** download the keystore attachment to the new machine; create
   `android/local.properties` with the four `release*` keys pointing at it. Run
   `./gradlew assembleRelease` (or `mvn`-equivalent) and confirm a **signed** APK.
4. **Backups:** confirm you can clone the `backups` branch (needs only GitHub login).
5. **Verify** you can build a signed release *before* you ever need to ship one.

## Anticipated future secrets (design for them now)

- **TLS / custom domain** certs — will be provider-managed (Render/Cloudflare); no manual
  custody expected, but record where.
- **Real user auth** (JWT signing key or Supabase Auth service keys) — when V2 adds
  users. Same rule: password manager, rotatable, never in git.
- **Play Store upload key / Play App Signing** — if you ever publish. Play App Signing
  changes the custody model (Google holds the app-signing key; you hold an upload key),
  and is the *recommended* long-term answer to keystore-loss risk. Revisit at publish.
- **Third-party API keys** (analytics, crash reporting) — if ever added.

## What Future Me should remember

- The **keystore is the only truly unrecoverable secret.** Three copies, passwords
  stored with it, and test that a copy opens. Everything else you can rotate.
- Secrets live in the **password manager**, values annotated with their consumer.
- Nothing secret goes in git — the `.gitignore` rules and env-var-only config enforce it;
  don't work around them.
- If you ever publish to Play, adopt **Play App Signing** — it removes the "lose the
  keystore, lose the app" risk entirely.

## Related

- `backend/.env.prod.example` — the exact env-var names Render needs.
- `docs/development/DEPLOYMENT.md` — where each secret is entered during deploy.
- `docs/development/BACKUP.md` — the `SUPABASE_SESSION_URL` backup secret.
- `docs/RELEASE_CHECKLIST.md` — Android release/signing specifics.
