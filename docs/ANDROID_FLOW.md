Android Application Flow

Project: MyFitnessLog
Version: 1.0
Status: Approved — as built and released in Version 1.0.0 (22 July 2026)
Last Updated: July 22, 2026

⸻

1. Purpose

This document defines the Android application’s navigation flow, screen hierarchy, user journeys, and UI responsibilities.

The Android application is the primary client for MyFitnessLog.

The design prioritizes:

* Fast workout logging
* Minimal user interaction during workouts
* Offline-first behavior
* Consistent navigation
* Simple, predictable user experience

⸻

1a. Implementation Status (as of July 22, 2026)

This document describes the full Version 1 flow. Status of each screen today:

| Screen | Status |
| --- | --- |
| Home (routine list) | ✅ Implemented |
| Routine Details | ✅ Implemented (Edit + Start Workout; no per-set history yet) |
| Edit Routine | ✅ Implemented (name, add/remove/reorder exercises, edit targets) |
| Exercise Library (browse) | ✅ Implemented as the "Exercises" tab (list, search, category filter). Routed in M9.5 — the screen existed from M4 but was unreachable. |
| Exercise Search / Picker | ✅ Implemented as the "add exercise to routine / workout" picker; downloads the library on open |
| Workout (active logging) | ✅ Implemented (sets add/edit/delete/toggle, complete, discard) |
| Manual (ad-hoc) workout | ✅ Implemented (start with no routine; add exercises via the picker) |
| Rest Timer + Workout timer | ✅ Implemented (elapsed derived; rest countdown transient) |
| History | ✅ Implemented (read-only list of completed workouts) |
| Workout Details | ✅ Implemented (read-only snapshot: metadata, exercises, sets) |
| Settings | ⬜ Placeholder |

Notable flow specifics as built:

* Home shows routines; tapping one opens Routine Details; "Start Workout" starts
  (or resumes) a workout and opens the Workout screen.
* The Workout tab resumes the active workout; when none is active it offers
  "Start empty workout" (a manual, routine-less workout). During any active
  workout, "Add exercise" opens the shared exercise picker.
* Only ONE active workout may exist at a time; starting again resumes it.
* Completing a workout navigates to History; discarding returns to Home.
* A completed/discarded workout renders read-only.
* History (a top-level tab) lists completed workouts only (DISCARDED and
  IN_PROGRESS are hidden), newest first; selecting one opens the read-only
  Workout Details drill-down (metadata + snapshotted exercises and sets). History
  is strictly read-only — it exposes no edit/delete/add/reorder actions.
* The standalone exercise-library browse screen from earlier milestones is no
  longer a top-level destination; exercise selection happens through the shared
  picker (routine editing and manual workouts).

⸻

2. Design Principles

The Android application follows these principles.

* Minimize taps during workouts.
* Keep frequently used actions easily accessible.
* Display information progressively.
* Never interrupt workout logging because of network connectivity.
* Read all UI data from the local Room database.

⸻

3. Navigation Structure

The application uses Navigation Compose.

Top-level navigation:

Home
│
├── Routine Details
│       ├── Edit Routine
│       └── Start Workout
│
├── Workout
│       ├── Exercise
│       ├── Rest Timer
│       └── Finish Workout
│
├── History
│       └── Workout Details
│
└── Settings

⸻

4. Application Start Flow

Application launch:

App Launch
      │
      ▼
Initialize Database
      │
      ▼
Initialize Sync
      │
      ▼
Load Home Screen

No login screen exists in Version 1.

⸻

5. Screen Overview

Version 1 contains the following screens.

Screen	Purpose
Home	Display workout routines
Routine Details	Display exercises in a routine
Edit Routine	Create or modify routines
Exercise Search	Select exercises
Workout	Active workout logging
Rest Timer	Countdown between sets
History	Workout history
Workout Details	View completed workout
Settings	Application settings

⸻

6. Home Screen

Purpose:

Display available workout routines.

Features:

* List routines
* Search routines (future)
* Create routine
* Edit routine
* Delete routine
* Duplicate routine
* Start workout

Primary action:

Start Workout

⸻

7. Routine Details Screen

Displays:

* Routine name
* Exercise list
* Exercise order
* Target sets
* Target repetitions
* Planned rest time
* Exercise notes

Actions:

* Edit routine
* Reorder exercises
* Add exercise
* Remove exercise
* Start workout

⸻

8. Edit Routine Screen

Purpose:

Create or modify workout templates.

Supported actions:

* Rename routine
* Add exercise
* Remove exercise
* Reorder exercises
* Modify target sets
* Modify target repetitions
* Modify target rest duration
* Edit notes

Changes affect future workouts only.

⸻

9. Exercise Search Screen

Purpose:

Browse and select exercises.

Features:

* Search by name
* Browse by category
* Select exercise

The exercise library is predefined in Version 1.

⸻

10. Workout Screen

This is the primary screen of the application.

Displays:

* Workout timer
* Current exercise
* Sets
* Weight
* Repetitions
* RPE
* Completion checkbox

Actions:

* Edit set
* Add set
* Delete set
* Move to next exercise
* Finish workout
* Discard workout

The workout screen must remain responsive regardless of internet connectivity.

⸻

11. Rest Timer

Purpose:

Track rest periods between sets.

Features:

* Countdown timer
* Skip timer
* Restart timer

The timer operates independently of synchronization.

⸻

12. History Screen

Displays:

Completed workout sessions.

Information shown:

* Workout date
* Routine name
* Workout duration
* Number of exercises

Selecting a workout opens Workout Details.

⸻

13. Workout Details Screen

Displays a completed workout.

Information includes:

* Workout summary
* Exercise list
* Sets
* Weight
* Repetitions
* RPE
* Rest durations (computed)

Historical workouts are read-only during normal application usage.

Future versions may introduce a dedicated workout correction flow.

⸻

14. Settings Screen

Version 1 settings are intentionally minimal.

Examples:

* Application version
* Sync status
* Manual sync (future)
* About

Authentication settings are excluded from Version 1.

⸻

15. Navigation Principles

Navigation should always be predictable.

Guidelines:

* Back returns to the previous screen.
* Starting a workout enters the Workout screen.
* Finishing a workout returns to History.
* Discarding a workout returns to Home.

⸻

16. User Journey

Routine workout:

Home
      │
      ▼
Routine Details
      │
      ▼
Start Workout
      │
      ▼
Workout
      │
      ▼
Finish Workout
      │
      ▼
History
      │
      ▼
Workout Details

⸻

17. Offline Behavior

Every screen must function without internet.

The UI never depends on network availability.

Data displayed on every screen is loaded from Room.

Synchronization occurs independently in the background.

⸻

18. UI State

Each screen has a dedicated ViewModel.

The ViewModel:

* Exposes immutable UI state.
* Observes Room using Kotlin Flow.
* Delegates user actions to the Repository.

The UI never communicates directly with Retrofit.

⸻

19. Error Handling

The application should provide clear, non-blocking feedback.

Examples:

* Validation errors
* Failed save operations
* Synchronization status

Workout logging must continue even if synchronization fails.

⸻

20. Future Screens

Potential future additions:

* Dashboard
* Weight Tracking
* Sleep Tracking
* Nutrition
* Water Intake
* Progress Photos
* Personal Records
* Analytics

These features are intentionally excluded from Version 1.

⸻

21. Definition of Done

The Android application flow is considered complete when:

* Navigation is fully defined.
* Every Version 1 screen has a clear responsibility.
* User journeys are documented.
* Offline behavior is specified.
* UI responsibilities are separated from business logic.
* The design supports future expansion without major restructuring.