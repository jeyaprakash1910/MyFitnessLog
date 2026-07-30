# ADR-0014 — Runtime uses Supabase's transaction pooler; Flyway uses the session pooler

Date: 2026-07-30
Status: Accepted
Related: ADR-0003 (backend is system source of truth), ADR-0012 (snake_case schema),
application-prod.yml, docs/development/DEPLOYMENT.md,
docs/internal/2026-07-29-production-configuration.md

## Context

Production runs the backend on Render against PostgreSQL managed by Supabase. Supabase
exposes the database three ways, and they are not interchangeable:

- **Direct** (`db.<ref>.supabase.co:5432`) — full session, but **IPv6-only**; Render's
  free tier has no IPv6 outbound, so it is unreachable from our host.
- **Supavisor transaction pooler** (`:6543`) — a server connection is returned to the
  pool after *every transaction*. Connection-efficient, which matters on the small
  free-tier connection budget, but it holds **no session state** between transactions.
- **Supavisor session pooler** (`:5432` on the pooler host) — one server connection per
  client connection for the connection's lifetime; full session semantics.

Two facts force a decision rather than a single URL:

1. **The transaction pooler breaks server-side prepared statements.** pgjdbc promotes a
   query to a named server-side prepared statement after a few executions; the next
   execution may land on a different physical connection that never saw the `PREPARE`,
   producing `prepared statement "S_1" does not exist`.
2. **Flyway needs a stable session.** Its migration lock is a session-scoped advisory
   lock; run over the transaction pooler it is not preserved across statements, so
   migration can hang or misbehave.

The application's steady-state workload (short, pooled queries) wants the transaction
pooler; Flyway's start-up workload (locked, multi-statement migration) wants a session.
One connection cannot serve both well.

## Decision

Use **two datasources in production**, both configured only via environment variables
(`application-prod.yml`):

- **Runtime Hikari pool → transaction pooler (`:6543`)**, with server-side prepared
  statements disabled (`prepareThreshold=0`, enforced in config so it cannot be dropped
  when editing the URL), a small pool (`maximum-pool-size: 3`, `minimum-idle: 0`), and
  `max-lifetime` below Supabase's idle cutoff so connections are recycled before Supabase
  closes them.
- **Flyway → session pooler (`:5432`)** via `spring.flyway.url/user/password`, so the
  advisory lock runs on a stable session.

Both URLs carry `?sslmode=require`. The split exists only in the `prod` profile;
development and tests use one ordinary local connection.

## Alternatives considered

**Single connection URL for both runtime and Flyway.** Simplest to configure and the
natural instinct. Rejected: whichever pooler you pick breaks the other workload — the
transaction pooler breaks Flyway's lock and pgjdbc prepared statements, and the session
pooler squanders the scarce free-tier connection budget on long-lived runtime
connections.

**Direct connection for everything.** Rejected: IPv6-only, so unreachable from Render's
free tier. Would also expose the full connection count with no pooling.

**Transaction pooler for both, with prepared statements disabled.** Fixes the prepared
statement problem but not Flyway's advisory-lock requirement, which is the harder of the
two. Rejected.

**Disable prepared statements only via the URL query string.** Works, but is silently
lost the first time someone rewrites the URL. Rejected in favour of enforcing
`prepareThreshold=0` in configuration.

## Consequences

Positive:

- Each workload runs on the connection mode it actually needs; no prepared-statement
  errors and no Flyway lock hazard.
- The free-tier connection budget is respected by the tiny runtime pool.
- The whole arrangement is environment-variable driven; no secret or host is committed.

Negative / accepted:

- Two connection strings must be configured correctly, and **swapping the ports**
  (runtime on `:5432`, Flyway on `:6543`) is a real, silent-until-intermittent failure
  mode. Mitigated by the local prod smoke test (DEPLOYMENT.md §5a) and by calling the
  swap out explicitly in the deployment guide's "common mistakes".
- The split is production-only, so it is exercised for the first time against Supabase
  rather than in local development — hence the mandated §5a smoke test before Render.
- If Supabase's pooler hosts/ports change, both URLs must be re-copied from the
  dashboard; they are not derivable from each other in code.
