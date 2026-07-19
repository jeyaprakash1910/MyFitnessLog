Android Application Flow

Project: MyFitnessLog
Version: 1.0
Status: Approved
Last Updated: July 20, 2026

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