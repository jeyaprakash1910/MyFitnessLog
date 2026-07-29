# ADR-0013 — A shared API key is the V1 authentication boundary

Date: 2026-07-29
Status: Accepted
Related: ADR-0003 (backend is system source of truth), SYNC.md §17 (security),
docs/development/CLOUD_CUTOVER_PLAN.md §2, docs/internal/2026-07-29-api-key-auth.md

## Context

Until now the API has been unauthenticated — acceptable while it ran only on
`localhost`. The Render deployment makes it publicly reachable, at which point an
unauthenticated API is an open door: anyone who discovers the URL can read, create, or
delete personal workout history. SYNC.md §17 already recorded that the architecture
"should not assume anonymous access beyond Version 1."

The system is, and for V1 remains, **single-user with a single writing client**
(Android). There is no user identity, no login, no per-row ownership beyond the single
seeded `app_user`. Whatever boundary we add must close the open door without inventing a
user-management system the project does not yet need.

## Decision

Authenticate the **application**, not a user, with a single shared secret sent as an
`X-API-Key` request header.

- A stateless Spring Security filter chain (`SecurityConfig`) with CSRF, sessions, and
  form/basic login disabled. A custom `ApiKeyAuthFilter` authenticates a request iff it
  carries the configured key (compared in length-constant time); the authorization
  rules then reject everything unauthenticated with **401**.
- **Open without a key:** `GET /api/v1/health` (Render's liveness probe must never need
  a secret) and CORS preflight `OPTIONS` (it cannot carry custom headers).
- **Key absent ⇒ auth disabled in dev, fatal in prod.** When `app.api-key` is blank —
  the local dev and test default — the filter authenticates every request, so
  development and the existing test suite are unchanged (a WARN is logged). Under the
  **`prod` profile a blank key is fatal**: the application refuses to start with a clear
  error, so an unauthenticated API can never be deployed by omission.
- **Swagger/OpenAPI is disabled in the prod profile**, removing that surface rather than
  reasoning about whether it should sit behind the key.

## Alternatives considered

**No authentication (defer to V2).** Rejected: a public write API to personal history is
not acceptable debt to deploy, and the fix is ~2 small classes.

**Full JWT with a user table, password hashing, refresh tokens.** Rejected as premature:
it builds registration/reset/rotation machinery for a benefit (multiple real users) that
does not exist yet. The API key is structured so this is a later drop-in — replace the
one filter, leave the chain.

**Supabase Auth (verify Supabase-issued JWTs).** Attractive once there are real users,
but it couples identity to Supabase and contradicts "clients talk only to the backend"
unless done carefully. Deferred to the multi-user trigger.

**Per-endpoint keys / HMAC request signing.** Rejected as over-engineering for one
client and one user.

## Consequences

Positive:

- The public URL is closed to anyone without the key; the fix is tiny and self-contained.
- Dev and tests are untouched (key-absent ⇒ auth off), so no test churn.
- The upgrade path to real per-user auth is localized to a single filter.

Negative / accepted:

- A shared static secret authenticates the app, not a person. It does not protect against
  a client that legitimately holds the key.
- **A future public browser web client would ship the key to the browser**, where it is
  extractable. That is acceptable only because the V1 web client is unbuilt and read-only;
  introducing real users is exactly the trigger to move to JWT/Supabase Auth. This is
  recorded so the limitation is a known, deliberate boundary rather than a surprise.
- Forgetting to set `APP_API_KEY` in production is caught at startup: the `prod` profile
  refuses to boot without it, so the failure is a loud deploy error rather than a silent
  open door.
