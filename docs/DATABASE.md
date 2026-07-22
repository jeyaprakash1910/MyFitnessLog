# Database Design

## Overview

This document defines the complete relational database design for **MyFitnessLog Version 1**.

It serves as the single source of truth for the application's persistent data model and is used as the foundation for:

- PostgreSQL database schema
- Flyway database migrations
- Spring Boot JPA entities
- Android Room entities
- Repository interfaces
- REST API contracts
- Future database migrations

The database is designed to support an **offline-first workout tracking application** where all workout history is permanently stored and synchronized between Android devices and the backend server.

Version 1 intentionally supports a **single-user application**. However, the database schema is designed to be **multi-user ready** by including a `User` entity from the beginning. Authentication is intentionally excluded from Version 1 and will be introduced in a future release without requiring structural database changes.

The database stores only **historical facts** about completed workouts.

Application features such as:

- Progressive overload recommendations
- Warm-up calculations
- Personal records
- Training analytics
- Workout suggestions

are **not persisted** in the database. These values are calculated dynamically by the application using historical workout data.

This approach ensures:

- Historical data remains immutable.
- Business logic can evolve without database migrations.
- The database remains normalized and easy to maintain.
- Workout history is always the single source of truth.

---

# Database Design Principles

The database follows the principles below.

## 1. Historical Accuracy

Completed workouts should not change during normal application usage.

Corrections to completed workouts are permitted only through the dedicated workout edit flow to fix logging mistakes while preserving historical accuracy.

If a user edits:

- an exercise name,
- a routine,
- target repetitions,
- target rest time,

previous workout history must remain exactly as it was when the workout was performed.

Workout-specific information is therefore stored as snapshots inside completed workout records whenever necessary.

---

## 2. Offline-First Design

The Android application is the primary data entry point.

The database design supports:

- locally created records,
- delayed synchronization,
- conflict-free UUID identifiers,
- future synchronization strategies.

No table relies on auto-incrementing identifiers.

---

## 3. Normalization

The schema is normalized to eliminate unnecessary duplication while preserving historical workout accuracy.

Reference data such as exercises and categories are normalized.

Historical workout data intentionally duplicates selected fields where snapshot preservation is required.

---

## 4. Future Scalability

Although Version 1 is designed for a single user, the schema supports future expansion including:

- authentication,
- multiple users,
- cloud synchronization,
- shared exercise libraries,
- workout templates,
- AI coaching.

These features can be added without redesigning the database.

---

## 5. Separation of Responsibilities

The database stores only persistent facts.

Business logic is intentionally excluded from the database.

Examples include:

- progressive overload calculations,
- warm-up generation,
- recommendation engines,
- statistics,
- charts,
- analytics.

These responsibilities belong to the application layer.

---

## 6. Immutable Workout History

Workout history represents completed events.

Historical records are protected from automatic modification caused by changes to routines, exercises or templates.

Corrections to completed workouts are permitted only through the dedicated workout edit flow to fix logging mistakes while preserving historical accuracy.

---

## 7. Readability

The schema prioritizes readability over excessive optimization.

Table names, column names and relationships should be understandable without additional documentation.

The design favors long-term maintainability over minimizing storage usage.

# Naming Conventions

To maintain consistency across the backend, Android application, database, and REST APIs, the following naming conventions must be used throughout the project.

## Tables

- Table names use **PascalCase**.
- Table names are singular nouns.
- Each table represents one entity.

Examples:

- User
- Exercise
- ExerciseCategory
- Routine
- RoutineExercise
- WorkoutSession
- WorkoutExercise
- WorkoutSet

---

## Columns

Column names use **camelCase**.

Examples:

- exerciseId
- routineId
- workoutSessionId
- targetRestSeconds
- createdAt
- updatedAt

---

## Primary Keys

Every table contains a single primary key.

Column name:

```text
id
```

All primary keys use the UUID data type.

---

## Foreign Keys

Foreign keys are named using the referenced entity followed by `Id`.

Examples:

```text
userId
exerciseId
routineId
categoryId
workoutSessionId
workoutExerciseId
```

---

## Boolean Columns

Boolean fields should clearly describe a true/false state.

Examples:

```text
isDeleted
isCompleted
isArchived
isFavorite
```

Avoid ambiguous names such as:

```text
deleted
completed
favorite
```

---

## Timestamp Columns

Timestamp columns use descriptive camelCase names.

Standard timestamp columns:

```text
createdAt
updatedAt
```

Optional timestamp columns:

```text
startedAt
endedAt
deletedAt
completedAt
```

---

## Enum Columns

Columns representing a fixed set of values should use descriptive names ending with `Type` or `Status`.

Examples:

```text
exerciseType
setCategory
equipmentType
workoutStatus
```

---

## Index Names

Indexes follow the convention:

```text
idx_<table>_<column>
```

Examples:

```text
idx_exercise_name
idx_workoutsession_startedat
idx_workoutset_workoutexerciseid
```

---

## Foreign Key Constraints

Foreign key constraints follow the convention:

```text
fk_<childtable>_<parenttable>
```

Examples:

```text
fk_routine_user
fk_workoutexercise_workoutsession
fk_workoutset_workoutexercise
```

---

## Unique Constraints

Unique constraints follow the convention:

```text
uk_<table>_<column>
```

Examples:

```text
uk_user_email
uk_exercise_name
```

---

# UUID Strategy

All primary keys use UUIDs instead of auto-incrementing integers.

## Reasoning

UUIDs provide several advantages for an offline-first application.

### Offline Record Creation

Android devices can generate identifiers locally without requiring communication with the backend.

This allows users to create:

- routines
- workout sessions
- workout sets

while offline.

---

### Conflict-Free Synchronization

Two devices can independently create records without generating conflicting identifiers.

No server-side ID allocation is required.

---

### Future Multi-Device Support

When cloud synchronization is introduced, UUIDs allow records to be merged without primary key collisions.

---

### Stable References

UUIDs remain stable across imports, exports, backups and synchronization.

---

## UUID Version

The application should use **UUID Version 4 (Random UUID)**.

UUID generation occurs within the application layer.

The database does not generate UUID values.

---

# Timestamp Strategy

Every persistent entity includes auditing timestamps.

Required columns:

```text
createdAt
updatedAt
```

Definitions:

### createdAt

Stores the timestamp when the record was first created.

Never changes.

---

### updatedAt

Stores the timestamp of the most recent modification.

Updated automatically whenever mutable fields change.

---

## Time Zone

All timestamps are stored in **UTC**.

Applications convert UTC timestamps to the user's local timezone for display.

This avoids ambiguity and simplifies synchronization.

---

## Timestamp Precision

All timestamps use millisecond precision.

---

# Soft Delete Strategy

Version 1 uses soft deletes for user-managed data.

Instead of permanently removing records, they are marked as deleted.

Required column:

```text
isDeleted
```

Optional column (future):

```text
deletedAt
```

---

## Why Soft Deletes?

Soft deletes provide several benefits.

### Historical Integrity

Workout history should never reference records that no longer exist.

---

### Synchronization

Deletion events can be synchronized between devices.

---

### Recovery

Accidentally deleted routines or exercises can be restored.

---

### Auditing

Future versions may display recently deleted items or maintain deletion history.

---

## Which Tables Use Soft Deletes?

Soft delete should be enabled for:

- User
- Exercise
- ExerciseCategory
- Routine

Workout history tables should **never** be soft deleted because they represent completed historical events.

These include:

- WorkoutSession
- WorkoutExercise
- WorkoutSet

Workout history is considered immutable once completed.

### Consequence: deleting a set while a workout is in progress

Because `WorkoutSet` is never soft-deleted, removing a set during an in-progress
workout is a genuine `DELETE`, and nothing remains for the synchronization engine
to upload. Android therefore records the deletion in a separate outbox table,
`workout_set_tombstone` (Room v4), and clears the row once the backend has been
told (ADR-0007).

That table is local to Android and is deliberately *not* part of the history
schema: it has no foreign key into the workout graph, and no history query reads
it. The alternative — an `isDeleted` flag on `WorkoutSet` — was rejected because
it would put a filter into every history read path permanently, which is exactly
the immutability/simplicity property this section protects.

---

## Database Philosophy Summary

The database is designed around the following principles:

- UUID-based identifiers
- UTC timestamps
- Immutable workout history
- Soft deletes for user-managed data
- Snapshot preservation for completed workouts
- Offline-first synchronization
- Readable and maintainable schema

# Enumerations

The database uses enumerated values for fields that have a predefined and controlled set of possible values.

Using enumerations improves data consistency, prevents invalid values, and simplifies application logic.

---

## WorkoutStatus

Represents the lifecycle state of a workout session.

| Value | Description |
|-------|-------------|
| IN_PROGRESS | Workout has started and is currently active. |
| COMPLETED | Workout has been successfully completed. |
| DISCARDED | Workout was abandoned or intentionally discarded before completion. |

Used By:

- WorkoutSession.status

---

## SetCategory

Represents the role of a set within an exercise.

| Value | Description |
|-------|-------------|
| WARMUP | Warm-up set performed before working sets. |
| WORKING | Standard working set. |
| TOP_SET | Highest intensity or heaviest working set. |
| BACKOFF | Reduced weight set performed after the top set. |

Used By:

- WorkoutSet.setCategory

## Future Enumerations

Additional enumerations may be introduced in future versions as new features are added.

Examples include:

- ExerciseType
- EquipmentType
- MuscleGroup
- WorkoutVisibility
- MeasurementUnit

Only introduce new enumerations when they represent a fixed and controlled set of values used across the application.

# Entity Relationship Diagram (ERD)

The database consists of eight core entities.

These entities are divided into three logical groups:

1. Master Data
2. Workout Templates
3. Workout History

The overall data model is shown below.

```text
                                   User
                                     │
                     ┌───────────────┴────────────────┐
                     │                                │
                     ▼                                ▼
                 Routine                    WorkoutSession
                     │                                │
                     ▼                                ▼
             RoutineExercise                WorkoutExercise
                     │                                │
                     │                                │
                     ▼                                ▼
                 Exercise ─────────────► ExerciseCategory
                     ▲
                     │
                     └───────────────────────────────┐
                                                     ▼
                                                WorkoutExercise
                                                     │
                                                     ▼
                                                WorkoutSet
```

---

# Entity Overview

## Master Data

Master data changes infrequently and serves as reusable reference information throughout the application.

### User

Represents the owner of all application data.

Version 1 contains exactly one user.

The database is designed to support multiple users in future versions without requiring schema changes.

---

### ExerciseCategory

Groups exercises into logical categories.

Examples:

- Chest
- Back
- Shoulders
- Biceps
- Triceps
- Legs
- Core
- Cardio

Each exercise belongs to exactly one category.

---

### Exercise

Represents the master exercise library.

Examples:

- Bench Press
- Squat
- Deadlift
- Lat Pulldown
- Shoulder Press

Exercises are reusable across multiple workout routines.

Exercises are never tied directly to a specific workout.

Completed workouts store snapshot information separately.

---

# Workout Templates

Workout templates define how future workouts should be performed.

Templates may change over time without affecting historical workout data.

---

### Routine

Represents a workout template.

Examples:

- Push A
- Pull A
- Legs A
- Upper Body
- Full Body

A routine contains one or more exercises.

---

### RoutineExercise

Defines the exercises contained within a routine.

This entity also stores workout planning information.

Examples include:

- exercise order
- target sets
- target repetition range
- target rest duration
- exercise notes

A single exercise can belong to many routines.

---

# Workout History

Workout history records what actually happened during training.

Unlike workout templates, historical workout data is immutable.

Editing a routine must never modify completed workout history.

---

### WorkoutSession

Represents one completed gym visit.

Examples:

- Push workout on Monday
- Leg workout on Thursday
- Full body workout on Saturday

A workout session contains one or more workout exercises.

---

### WorkoutExercise

Represents one exercise performed during a workout session.

This entity acts as a historical snapshot.

It stores information copied from the routine at the moment the workout begins.

Examples:

- exercise name
- exercise order
- target sets
- target repetition range
- target rest duration
- exercise notes

This ensures completed workouts remain historically accurate even if routines are modified later.

A workout session contains multiple workout exercises.

Each workout exercise contains multiple workout sets.

---

### WorkoutSet

Represents one performed set.

Examples:

```text
80 kg × 8 reps

80 kg × 8 reps

75 kg × 10 reps
```

Each set stores the actual workout performance.

Examples include:

- weight
- repetitions
- set category
- started time
- finished time
- RPE
- RIR
- completion status

Workout sets represent the smallest unit of workout history.

---

# Entity Responsibilities

Each entity has exactly one primary responsibility.

| Entity | Responsibility |
|----------|---------------|
| User | Owns all application data |
| ExerciseCategory | Groups exercises |
| Exercise | Master exercise library |
| Routine | Workout template |
| RoutineExercise | Defines exercises within a routine |
| WorkoutSession | Represents one completed workout |
| WorkoutExercise | Snapshot of one exercise during a workout |
| WorkoutSet | Stores actual performance for one set |

This separation of responsibilities keeps the database normalized, maintainable and easy to extend.

---

# Design Decisions

The following architectural decisions influenced the data model.

## Workout Templates are Independent of Workout History

Workout templates are planning tools.

Workout history represents completed events.

Editing a workout template must never modify historical records.

---

## WorkoutExercise Exists to Preserve History

WorkoutExercise intentionally duplicates selected information from the routine.

This allows completed workouts to remain historically accurate even if:

- exercises are renamed,
- routines change,
- target repetitions change,
- target rest duration changes.

---

## Workout History is the Source of Truth

The database stores only completed workout history.

Features such as:

- progressive overload
- warm-up recommendations
- personal records
- workout suggestions
- analytics

are calculated dynamically by the application.

No recommendation data is permanently stored in the database.

# Master Data

Master data represents reusable reference information used throughout the application.

These entities change infrequently and are shared across workout templates and workout history.

Master data consists of:

- User
- ExerciseCategory
- Exercise

---

# User

## Purpose

Represents the owner of all application data.

Version 1 supports exactly one user.

The table exists to make the database schema future-ready for multi-user support without requiring structural changes.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| User → Routine | One-to-Many |
| User → WorkoutSession | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |
| isDeleted | BOOLEAN | No | false | Soft delete flag |

---

## Constraints

Primary Key

- id

---

## Indexes

Primary Key Index

- id

---

## Notes

- Version 1 contains exactly one record.
- Authentication is intentionally excluded from Version 1.
- Additional profile information will be introduced in future versions.

---

# ExerciseCategory

## Purpose

Groups exercises into logical muscle groups.

Categories simplify browsing, searching and filtering exercises.

---

## Examples

- Chest
- Back
- Shoulders
- Biceps
- Triceps
- Legs
- Core
- Cardio

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| ExerciseCategory → Exercise | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| name | VARCHAR(100) | No | - | Category name |
| displayOrder | INTEGER | No | 0 | Sort order |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |
| isDeleted | BOOLEAN | No | false | Soft delete flag |

---

## Constraints

Primary Key

- id

Unique

- name

---

## Indexes

Primary Key Index

- id

Unique Index

- name

Index

- displayOrder

---

## Notes

- Categories should rarely change.
- Categories are managed by the application.
- Display order controls the order shown in the UI.

---

# Exercise

## Purpose

Represents the master exercise library.

Exercises are reusable across routines and workout history.

Exercises describe *what* is performed, not *when* or *how much*.

---

## Examples

- Bench Press
- Squat
- Deadlift
- Lat Pulldown
- Shoulder Press

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| ExerciseCategory → Exercise | Many-to-One |
| Exercise → RoutineExercise | One-to-Many |
| Exercise → WorkoutExercise | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| categoryId | UUID | No | - | Reference to ExerciseCategory |
| name | VARCHAR(150) | No | - | Exercise name |
| description | TEXT | Yes | NULL | Exercise description |
| instructions | TEXT | Yes | NULL | How to perform the exercise |
| equipment | VARCHAR(100) | Yes | NULL | Required equipment |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |
| isDeleted | BOOLEAN | No | false | Soft delete flag |

---

## Constraints

Primary Key

- id

Foreign Key

- categoryId → ExerciseCategory.id

Unique

- name

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- categoryId

Unique Index

- name

---

## Notes

- Exercises represent reusable master data.
- Exercises may be renamed in future.
- Historical workout data is protected by WorkoutExercise snapshots.
- Deleting an exercise should never remove completed workout history.

# Workout Templates

Workout templates define how future workouts should be performed.

Templates are reusable workout blueprints that can be modified over time without affecting completed workout history.

Workout templates consist of:

- Routine
- RoutineExercise

---

# Routine

## Purpose

Represents a reusable workout template.

A routine groups multiple exercises into a planned workout.

Examples include:

- Push A
- Pull A
- Legs A
- Upper Body
- Full Body

A routine does not store workout performance.

It only defines how a workout should be performed.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| User → Routine | Many-to-One |
| Routine → RoutineExercise | One-to-Many |
| Routine → WorkoutSession | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| userId | UUID | No | - | Owner of the routine |
| name | VARCHAR(150) | No | - | Routine name |
| description | TEXT | Yes | NULL | Optional routine description |
| displayOrder | INTEGER | No | 0 | Order displayed in the UI |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |
| isDeleted | BOOLEAN | No | false | Soft delete flag |

---

## Constraints

Primary Key

- id

Foreign Key

- userId → User.id

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- userId

Index

- displayOrder

---

## Notes

- A routine may contain any number of exercises.
- Routine names do not need to be globally unique.
- Deleting a routine must never delete completed workout history.
- Editing a routine only affects future workouts.

---

# RoutineExercise

## Purpose

Represents one planned exercise within a routine.

This entity defines the workout instructions for a specific exercise.

Examples include:

- Exercise order
- Target sets
- Target repetition range
- Target rest duration
- Notes

RoutineExercise exists only during workout planning.

When a workout begins, its data is copied into WorkoutExercise to create a historical snapshot.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| Routine → RoutineExercise | Many-to-One |
| Exercise → RoutineExercise | Many-to-One |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| routineId | UUID | No | - | Parent routine |
| exerciseId | UUID | No | - | Master exercise |
| exerciseOrder | INTEGER | No | - | Position within the routine |
| targetSets | INTEGER | No | - | Planned number of sets |
| minTargetReps | INTEGER | No | - | Minimum target repetitions |
| maxTargetReps | INTEGER | No | - | Maximum target repetitions |
| targetRestSeconds | INTEGER | Yes | NULL | Planned rest duration |
| notes | TEXT | Yes | NULL | Exercise-specific notes |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |

---

## Constraints

Primary Key

- id

Foreign Key

- routineId → Routine.id

Foreign Key

- exerciseId → Exercise.id

Check Constraint

- targetSets > 0
- minTargetReps > 0
- maxTargetReps >= minTargetReps

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- routineId

Foreign Key Index

- exerciseId

Composite Index

- (routineId, exerciseOrder)

---

## Notes

- Exercise order determines the sequence displayed during a workout.
- The same exercise may appear multiple times within a routine.
- Changes to a RoutineExercise affect only future workouts.
- Workout history always uses WorkoutExercise snapshots.
- Notes are copied to WorkoutExercise when a workout starts.

---

# Design Decisions

## Templates are Editable

Workout templates are intended to evolve over time.

Users may freely:

- Add exercises
- Remove exercises
- Reorder exercises
- Change target sets
- Change repetition ranges
- Modify rest durations
- Update notes

These changes affect only future workouts.

---

## RoutineExercise Stores Planning Data

RoutineExercise contains planning information only.

Examples include:

- Exercise order
- Target sets
- Target repetition range
- Planned rest
- Notes

Actual workout performance is never stored here.

---

## Historical Data is Created at Workout Start

When a workout session begins:

Routine
        ↓
RoutineExercise
        ↓
WorkoutExercise (Snapshot)
        ↓
WorkoutSet

This ensures completed workouts remain historically accurate even if workout templates are modified later.
# Workout History

Workout history records what actually happened during a workout.

Unlike workout templates, workout history is immutable.

Completed workouts represent historical snapshots and must never be modified by changes made to workout templates.

Workout history consists of:

- WorkoutSession
- WorkoutExercise
- WorkoutSet

---

# WorkoutSession

## Purpose

Represents one workout performed by the user.

A workout session begins when the user starts a workout and ends when the workout is completed or discarded.

WorkoutSession stores information that applies to the workout as a whole rather than individual exercises or sets.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| User → WorkoutSession | Many-to-One |
| Routine → WorkoutSession | Many-to-One |
| WorkoutSession → WorkoutExercise | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| userId | UUID | No | - | Owner of the workout |
| routineId | UUID | Yes | NULL | Routine used to start the workout. NULL indicates a manually created workout. |
| status | WorkoutStatus | No | IN_PROGRESS | Current workout status (IN_PROGRESS, COMPLETED, DISCARDED) |
| startedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Workout start time |
| endedAt | TIMESTAMP WITH TIME ZONE | Yes | NULL | Workout completion time |
| notes | TEXT | Yes | NULL | Workout level notes |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |

---

## Constraints

Primary Key

- id

Foreign Key

- userId → User.id

Foreign Key

- routineId → Routine.id

Check Constraint

- status IN (IN_PROGRESS, COMPLETED, DISCARDED)

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- userId

Foreign Key Index

- routineId

Index

- startedAt

---

## Notes

- A workout may originate from a routine or be created manually.
- A NULL routineId indicates a manually created workout.
- Workout status tracks whether the workout is currently in progress, completed or discarded.
- Deleting a routine must never delete workout sessions.
- WorkoutSession contains no exercise performance data.
- Once completed, workout sessions become historical records.
- Completed workouts are not modified during normal application usage.
- Corrections to completed workouts are permitted only through the dedicated workout edit flow.

---

# WorkoutExercise

## Purpose

Represents one exercise performed during a workout session.

WorkoutExercise is a historical snapshot of the planned exercise at the moment the workout begins.

It preserves workout history even if the corresponding routine or exercise is modified later.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| WorkoutSession → WorkoutExercise | Many-to-One |
| Exercise → WorkoutExercise | Many-to-One |
| WorkoutExercise → WorkoutSet | One-to-Many |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| workoutSessionId | UUID | No | - | Parent workout session |
| exerciseId | UUID | No | - | Reference to master exercise |
| exerciseName | VARCHAR(150) | No | - | Snapshot of exercise name |
| exerciseOrder | INTEGER | No | - | Order performed during workout |
| targetSets | INTEGER | No | - | Snapshot of planned sets |
| minTargetReps | INTEGER | No | - | Snapshot of minimum target reps |
| maxTargetReps | INTEGER | No | - | Snapshot of maximum target reps |
| targetRestSeconds | INTEGER | Yes | NULL | Snapshot of planned rest |
| notes | TEXT | Yes | NULL | Snapshot of exercise notes |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |

---

## Constraints

Primary Key

- id

Foreign Key

- workoutSessionId → WorkoutSession.id

Foreign Key

- exerciseId → Exercise.id

Check Constraint

- targetSets > 0
- minTargetReps > 0
- maxTargetReps >= minTargetReps

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- workoutSessionId

Foreign Key Index

- exerciseId

Composite Index

- (workoutSessionId, exerciseOrder)

---

## Notes

- Stores snapshot data copied from RoutineExercise.
- Snapshot data is preserved after workout completion.
- Corrections to completed workouts are permitted only through the dedicated workout edit flow.
- The same exercise may appear multiple times within a workout.
- Each WorkoutExercise contains one or more WorkoutSets.

---

# WorkoutSet

## Purpose

Represents one performed set.

WorkoutSet stores the actual performance recorded during the workout.

It is the smallest unit of workout history.

Analytics, progression, personal records and recommendations are derived from WorkoutSet data.

---

## Relationships

| Relationship | Cardinality |
|--------------|-------------|
| WorkoutExercise → WorkoutSet | Many-to-One |

---

## Columns

| Column | Type | Nullable | Default | Description |
|----------|------|----------|----------|-------------|
| id | UUID | No | - | Primary key |
| workoutExerciseId | UUID | No | - | Parent workout exercise |
| setNumber | INTEGER | No | - | Sequential set number |
| weight | DECIMAL(6,2) | No | - | Weight lifted |
| repetitions | INTEGER | No | - | Completed repetitions |
| setCategory | SetCategory | No | WORKING | WARMUP / WORKING / TOP_SET / BACKOFF |
| startedAt | TIMESTAMP WITH TIME ZONE | Yes | NULL | Time when the user started the set |
| finishedAt | TIMESTAMP WITH TIME ZONE | Yes | NULL | Time when the user completed/logged the set |
| rpe | DECIMAL(3,1) | Yes | NULL | Rate of Perceived Exertion |
| rir | DECIMAL(3,1) | Yes | NULL | Reps in Reserve |
| isCompleted | BOOLEAN | No | true | Indicates whether the set was completed |
| createdAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Record creation timestamp |
| updatedAt | TIMESTAMP WITH TIME ZONE | No | Current UTC Time | Last modification timestamp |

---

## Constraints

Primary Key

- id

Foreign Key

- workoutExerciseId → WorkoutExercise.id

Check Constraint

- setNumber > 0
- repetitions >= 0
- weight >= 0
- rpe BETWEEN 1 AND 10 (when provided)
- rir >= 0 (when provided)
- setCategory IN (WARMUP, WORKING, TOP_SET, BACKOFF)

---

## Indexes

Primary Key Index

- id

Foreign Key Index

- workoutExerciseId

Composite Index

- (workoutExerciseId, setNumber)

---

## Notes

- Stores actual workout performance.
- Supports warm-up, working, top and back-off sets using setCategory.
- Supports multiple sets for the same exercise.
- startedAt and finishedAt preserve the exact timing of each performed set.
- Rest duration, set duration and workout analytics are computed dynamically from timestamps.
- WorkoutSet records should not be modified after workout completion except through the dedicated workout edit flow.

---

# Design Decisions

## Workout History is Immutable

Completed workouts represent historical records and are not modified during normal application usage.

Changes made to:

- Routines
- Routine exercises
- Exercise metadata

must never automatically modify completed workout history.

Corrections to completed workouts are permitted only through the dedicated workout edit flow to fix logging mistakes while preserving the integrity of workout history.

---

## WorkoutExercise Stores Snapshots

WorkoutExercise intentionally duplicates selected planning information from RoutineExercise.

This preserves historical accuracy if:

- Exercise names change
- Exercise order changes
- Target sets change
- Target repetition ranges change
- Rest durations change
- Notes change

---

## WorkoutSet Stores Facts Only

WorkoutSet records only measurable workout data.

Examples include:

- Weight
- Repetitions
- Set category
- Started time
- Finished time
- RPE
- RIR

No calculated values are permanently stored.

Metrics such as:

- Rest duration
- Set duration
- Average rest time
- Total active lifting time

are computed dynamically from stored timestamps.

---

## Manual Workouts

Workout sessions may be created without selecting a routine.

In this case:

- routineId is NULL.
- WorkoutExercise records are created manually as exercises are added during the workout.

This allows users to perform ad-hoc workouts without maintaining workout templates.

---

## Progression is Computed

The database stores workout history only.

Features such as:

- Progressive overload
- Warm-up recommendations
- Personal records
- Volume calculations
- One Rep Max estimates
- Training analytics

are computed dynamically from WorkoutSet history by the application.

The database remains the source of truth while business logic remains in the application layer.

# Relationships & Referential Integrity

This section defines how entities are related to one another and the rules that maintain data consistency throughout the database.

The database follows a normalized relational model where each entity has a clearly defined responsibility.

Foreign keys are used to enforce referential integrity and prevent orphaned records.

---

# Entity Relationships

| Parent Entity | Child Entity | Relationship |
|---------------|--------------|--------------|
| User | Routine | One-to-Many |
| User | WorkoutSession | One-to-Many |
| ExerciseCategory | Exercise | One-to-Many |
| Routine | RoutineExercise | One-to-Many |
| Exercise | RoutineExercise | One-to-Many |
| Routine | WorkoutSession | One-to-Many (Optional) |
| WorkoutSession | WorkoutExercise | One-to-Many |
| Exercise | WorkoutExercise | One-to-Many |
| WorkoutExercise | WorkoutSet | One-to-Many |

---

# Relationship Diagram

```text
User
├── Routine
│     └── RoutineExercise
│            └── Exercise
│
└── WorkoutSession
      └── WorkoutExercise
             ├── Exercise
             └── WorkoutSet

ExerciseCategory
└── Exercise
```

---

# Foreign Key Rules

## Routine

| Foreign Key | References |
|-------------|------------|
| userId | User.id |

---

## Exercise

| Foreign Key | References |
|-------------|------------|
| categoryId | ExerciseCategory.id |

---

## RoutineExercise

| Foreign Key | References |
|-------------|------------|
| routineId | Routine.id |
| exerciseId | Exercise.id |

---

## WorkoutSession

| Foreign Key | References |
|-------------|------------|
| userId | User.id |
| routineId | Routine.id (Nullable) |

---

## WorkoutExercise

| Foreign Key | References |
|-------------|------------|
| workoutSessionId | WorkoutSession.id |
| exerciseId | Exercise.id |

---

## WorkoutSet

| Foreign Key | References |
|-------------|------------|
| workoutExerciseId | WorkoutExercise.id |

---

# Cascade Rules

Referential actions define what happens when parent records are updated or deleted.

---

## User

Deleting a user is outside the scope of Version 1.

If user deletion is supported in the future, it should be implemented as a soft delete.

Cascade delete must never be used.

---

## ExerciseCategory

Deleting an exercise category must not automatically delete exercises.

Instead:

- Prevent deletion while exercises exist.
- Soft delete the category if necessary.

Recommended Action:

- ON DELETE RESTRICT

---

## Exercise

Exercises represent master data.

Deleting an exercise must never remove:

- RoutineExercise
- WorkoutExercise
- WorkoutSet

Historical workout data must always remain intact.

Recommended Action:

- ON DELETE RESTRICT

---

## Routine

Deleting a routine should not affect completed workouts.

RoutineExercise records may be safely removed because they only belong to the template.

WorkoutSession history must remain unchanged.

Recommended Actions:

- Routine → RoutineExercise
  - ON DELETE CASCADE

- Routine → WorkoutSession
  - ON DELETE SET NULL

---

## WorkoutSession

WorkoutSession owns WorkoutExercise.

Deleting a discarded or incomplete workout may remove its child records.

Completed workouts should normally never be deleted.

Recommended Action:

- WorkoutSession → WorkoutExercise
  - ON DELETE CASCADE

---

## WorkoutExercise

WorkoutExercise owns WorkoutSet.

If a WorkoutExercise is deleted, all associated WorkoutSet records should also be removed.

Recommended Action:

- WorkoutExercise → WorkoutSet
  - ON DELETE CASCADE

---

# Referential Integrity Principles

## Workout History Always Wins

Workout history is the source of truth.

Historical records must never be modified or removed because of changes made to templates or master data.

---

## Templates are Independent

Routine templates exist only to plan future workouts.

They are not responsible for preserving workout history.

---

## Snapshot Architecture

When a workout begins:

Routine
        ↓
RoutineExercise
        ↓
WorkoutExercise (Snapshot)
        ↓
WorkoutSet

Changes made to templates after the workout starts never affect historical workout records.

---

## No Orphan Records

Every child record must reference a valid parent.

Examples:

- Every Routine belongs to one User.
- Every RoutineExercise belongs to one Routine.
- Every WorkoutExercise belongs to one WorkoutSession.
- Every WorkoutSet belongs to one WorkoutExercise.

---

## Master Data is Shared

Exercises and ExerciseCategories are reusable master data shared across routines and workouts.

Historical workout records store snapshots where necessary to preserve historical accuracy.

---

# Design Decisions

## Historical Data is Immutable

Completed workouts should remain unchanged except through the dedicated workout edit flow.

Workout history must never be modified automatically because of changes elsewhere in the database.

---

## Manual Workouts are First-Class Citizens

Workout sessions are not required to originate from a routine.

A manually created workout is represented by:

- routineId = NULL

This allows users to perform ad-hoc workouts without maintaining workout templates.

---

## Foreign Keys Enforce Consistency

All relationships are protected by foreign key constraints.

This guarantees:

- No invalid references.
- No orphaned child records.
- Consistent database state.
- Reliable query behavior.

---

## Cascade Operations are Used Carefully

Cascade operations are permitted only where child entities have no meaning without their parent.

Examples include:

- RoutineExercise
- WorkoutExercise
- WorkoutSet

Historical workout data is never deleted through cascading operations from master data or workout templates.

# Future Database Enhancements

The Version 1 database intentionally focuses on workout tracking fundamentals.

The following enhancements are intentionally excluded from Version 1 and may be introduced in future releases without requiring major architectural changes.

## Exercise Metadata

Examples:

- ExerciseType
- EquipmentType
- PrimaryMuscle
- SecondaryMuscles
- MovementPattern

---

## Advanced Training Techniques

Examples:

- Supersets
- Giant Sets
- Drop Sets
- Cluster Sets
- Rest-Pause Sets

---

## Media Attachments

Examples:

- Progress Photos
- Exercise Videos
- Workout Attachments

---

## Social Features

Examples:

- Shared Workouts
- Public Routines
- Workout Likes
- Comments

---

## Cloud Features

Examples:

- User Authentication
- Multi-device Synchronization
- Workout Sharing
- Team Workspaces

---

## Analytics

The database intentionally stores only historical workout facts.

Future analytics such as:

- Progressive Overload
- Personal Records
- One Rep Max
- Volume Trends
- Recovery Metrics
- AI Coaching

will continue to be computed dynamically from workout history rather than stored as persistent data.