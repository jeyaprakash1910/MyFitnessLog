# ADR-0012 — Database identifiers are unquoted snake_case

Date: 2026-07-28
Status: Accepted
Related: ADR-0003 (backend is system source of truth), ADR-0006 (JPA auditing),
docs/development/CLOUD_CUTOVER_PLAN.md, DATABASE.md, SYNC.md

## Context

The Version 1 schema was authored with **quoted, case-sensitive PascalCase table
names and camelCase column names** — `"WorkoutSet"`, `"workoutExerciseId"`,
`"ExerciseCategory"` — mirroring the JPA entity names one-to-one. `"User"` is also a
**reserved word** in SQL, which is only usable when quoted.

Quoted mixed-case identifiers are permanent friction in PostgreSQL. Every hand-written
query, `psql` session, `pg_dump` inspection, ad-hoc analytics query, and future BI or
reporting tool must reproduce the exact casing *and* the surrounding double quotes, or
the statement fails: `SELECT * FROM WorkoutSet` errors, because the object is really
`"WorkoutSet"`. Over a project intended to run for a decade — and to be queried by
hand for progress analysis — this is an unbounded, easily-forgotten cost paid on every
read path outside the ORM.

The cost is at its lowest right now: the application has never been deployed, Supabase
holds no data, and Android is unaffected because it communicates through REST DTOs and
never references table or column names. After cutover this would become a data
migration under load rather than a rename of empty definitions.

## Decision

All database identifiers are **unquoted `snake_case`**.

- Tables: `app_user` (renamed from the reserved `"User"`), `exercise`,
  `exercise_category`, `routine`, `routine_exercise`, `workout_session`,
  `workout_exercise`, `workout_set`.
- Columns: `workout_exercise_id`, `set_number`, `started_at`, `display_order`, … —
  the `snake_case` form of each former camelCase column.
- Because Supabase is greenfield, this is applied by **rewriting the Flyway baseline**
  (`V1`–`V7`) in place rather than shipping an `ALTER … RENAME` migration. The version
  numbers are unchanged; this is valid only because no environment has the old baseline
  durably applied. Existing local dev/test databases are dropped and rebuilt.
- Entities carry **explicit unquoted `@Table`/`@Column`/`@JoinColumn` names**. The
  physical naming strategy is left as `PhysicalNamingStrategyStandardImpl`, which passes
  the explicit lowercase names through unquoted; PostgreSQL then case-folds them
  consistently. No entity field names change — only the mapped identifiers.

## Alternatives considered

**Keep quoted PascalCase identifiers.** Rejected: it pushes exact-casing-plus-quoting
into every hand-written query for the life of the project, and `"User"` remains a
reserved-word trap. The one-time convenience of entity/table name symmetry is not worth
a decade of read-path friction.

**Ship a `V8 ALTER … RENAME` migration** instead of rewriting the baseline. Rejected
*while greenfield*: it would bake the quoted names permanently into migration history
and add a long, verbose rename migration for a schema no production database has yet
seen. Had any environment held real data, this would have been the correct — and only
safe — approach.

**Switch to `CamelCaseToUnderscoresNamingStrategy` and delete the explicit `@Column`
annotations.** A cleaner end state, but it removes the annotations that also declare
`precision`, `length`, `nullable`, and `updatable`. Keeping explicit names is the
lower-risk change and preserves those declarations verbatim.

## Consequences

Positive:

- Every table and column is queryable by hand without quoting or casing ceremony.
- `"User"` reserved-word hazard is eliminated (`app_user`).
- Production and test databases fold identifiers identically, so tests exercise the
  real casing behaviour.

Negative / accepted:

- Existing local dev/test databases must be dropped and rebuilt once (no production
  data exists, so there is nothing to migrate).
- Constraint and index names retain their original concatenated lowercase form
  (`pk_workoutset`, `idx_workoutsession_status_startedat`); they are unquoted and
  harmless, and were left untouched to minimise churn.
- Two test-side native SQL strings referenced the old quoted names and were updated to
  match; all 92 backend tests pass against the rebuilt schema.
</content>
