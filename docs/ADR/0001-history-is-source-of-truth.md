# ADR-0001: Workout History is the Source of Truth

## Status

Accepted

## Context

The application needs to recommend future workouts based on previous performance.

## Decision

The database will store only historical workout data.

Recommendations such as:

- Warm-up weights
- Working weights
- Progressive overload
- Deload suggestions
- PR detection

will be calculated dynamically by the application.

No recommendation data will be persisted.

## Consequences

Benefits:

- Single source of truth
- Simpler schema
- Easier algorithm improvements
- No stale recommendation data
- No database migrations when recommendation logic changes