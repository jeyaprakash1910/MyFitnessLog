/**
 * TypeScript models mirroring the backend response DTOs
 * (`com.myfitnesslog.dto.response.*`), which are frozen by API_SPECIFICATION.md.
 *
 * There is no shared codegen between Android and web — at this size a
 * hand-written mirror is cheaper than a pipeline (MILESTONE_10_PLAN §1). The
 * frozen API contract is what keeps the two aligned; these types are validated
 * against it in tests.
 *
 * ## Type-mapping decisions
 *
 * - **UUID → `string`.** There is no richer JS type; the backend emits the
 *   canonical hyphenated form and the web client never parses it, only passes it
 *   back in a path.
 * - **`Instant` → `string` (ISO-8601, UTC, e.g. `2026-07-21T09:00:00Z`).** Spring
 *   serializes instants as ISO strings, and keeping them as strings avoids the
 *   `Date`-constructor timezone traps. Parsing to a `Date` is a formatting
 *   concern deferred to Phase 2, done explicitly at the display edge.
 * - **`BigDecimal` → `DecimalString` (a branded `string`).** This is the one
 *   real trap: `weight`, `rpe` and `rir` are Postgres `NUMERIC`. If parsed as JS
 *   numbers, `102.50` collapses to `102.5` and the scale M9 preserved end-to-end
 *   is lost. They are read as strings straight from the raw JSON text (see
 *   `decimal.ts`) and are display/format-only — never used in arithmetic here.
 */

/** A UUID in canonical hyphenated string form. */
export type Uuid = string;

/** An ISO-8601 instant in UTC, e.g. `2026-07-21T09:00:00Z`. */
export type IsoInstant = string;

/**
 * A decimal value carried as a string so its scale survives (e.g. `"102.50"`).
 * Branded so a plain string cannot be passed where scale-preservation matters,
 * and so arithmetic on it is a visible mistake at the type level.
 */
export type DecimalString = string & { readonly __brand: 'DecimalString' };

/** Workout lifecycle status. Mirrors the backend `WorkoutStatus` enum. */
export type WorkoutStatus = 'IN_PROGRESS' | 'COMPLETED' | 'DISCARDED';

/** Set classification. Mirrors the backend `SetCategory` enum. */
export type SetCategory = 'WARMUP' | 'WORKING' | 'TOP_SET' | 'BACKOFF';

/**
 * A workout history summary — one row of `GET /workout-sessions`.
 *
 * The list endpoint returns COMPLETED sessions only, newest first
 * (API_SPECIFICATION §7). Note what is absent: no exercise count and no
 * duration. Duration is derived (`endedAt − startedAt`), matching Android.
 */
export interface WorkoutSessionSummary {
  id: Uuid;
  /** `null` for a manual/ad-hoc workout that started from no routine. */
  routineId: Uuid | null;
  status: WorkoutStatus;
  startedAt: IsoInstant;
  /** Always present for a COMPLETED workout; `null` only while IN_PROGRESS. */
  endedAt: IsoInstant | null;
  notes: string | null;
}

/** One performed set within a workout exercise — `WorkoutSetResponse`. */
export interface WorkoutSet {
  id: Uuid;
  workoutExerciseId: Uuid;
  setNumber: number;
  weight: DecimalString;
  repetitions: number;
  setCategory: SetCategory;
  startedAt: IsoInstant | null;
  finishedAt: IsoInstant | null;
  rpe: DecimalString | null;
  rir: DecimalString | null;
  isCompleted: boolean;
}

/** One snapshotted exercise within a workout, with its sets — `WorkoutExerciseDetailResponse`. */
export interface WorkoutExerciseDetail {
  id: Uuid;
  exerciseId: Uuid;
  exerciseName: string;
  exerciseOrder: number;
  targetSets: number;
  minTargetReps: number;
  maxTargetReps: number;
  targetRestSeconds: number | null;
  notes: string | null;
  sets: WorkoutSet[];
}

/** The full immutable workout snapshot — `WorkoutSessionDetailResponse`. */
export interface WorkoutSessionDetail {
  id: Uuid;
  routineId: Uuid | null;
  status: WorkoutStatus;
  startedAt: IsoInstant;
  endedAt: IsoInstant | null;
  notes: string | null;
  exercises: WorkoutExerciseDetail[];
}

/** The backend's standard error envelope — `ErrorResponse`. */
export interface ApiErrorBody {
  timestamp: IsoInstant;
  status: number;
  error: string;
  message: string;
  path: string;
}
