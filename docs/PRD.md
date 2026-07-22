Product Requirements Document (PRD)

Project: MyFitnessLog
Version: 1.0
Status: Approved — delivered in Version 1.0.0 (released 22 July 2026)
Author: Jeyaprakash
Last Updated: July 19, 2026

⸻

1. Introduction

1.1 Purpose

MyFitnessLog is a personal fitness tracking application built to provide a fast, reliable, and distraction-free workout logging experience.

The application is designed primarily for Android, with a web application for viewing workout history. The system will function offline and synchronize data automatically when internet connectivity is available.

Version 1 focuses exclusively on workout tracking. Every feature included in this release directly supports that goal.

⸻

2. Problem Statement

Existing fitness applications have several drawbacks:

* Free plans restrict workout routines.
* Workout history is limited or hidden behind subscriptions.
* Applications include many unnecessary features.
* Users do not fully own their workout data.
* Offline support is often poor or unavailable.

The objective of MyFitnessLog is to solve these problems by providing a lightweight, personal-first workout tracking application.

⸻

3. Product Vision

Build a workout tracking application that is:

* Fast
* Reliable
* Offline-first
* Easy to use during workouts
* Fully owned by the user
* Easily extensible for future fitness features

⸻

4. Goals

Primary Goals

* Log workouts quickly.
* Support unlimited workout routines.
* Store unlimited workout history.
* Work completely offline.
* Automatically synchronize data to the backend.
* View workout history from both Android and the web.

Secondary Goals

* Learn Android development.
* Learn Spring Boot backend development.
* Build a production-quality portfolio project.
* Create a scalable architecture for future expansion.

⸻

5. Target User

Initial Target User

The application is initially built for a single user (the project owner).

Characteristics:

* Uses Android while working out.
* Uses the website to review workout history.
* Exercises multiple times each week.
* Prefers speed and simplicity over unnecessary features.

Although Version 1 is single-user, the architecture should allow future multi-user support without major redesign.

⸻

6. Scope

Included in Version 1

Workout Routines

The user can:

* Create routines
* Edit routines
* Delete routines
* Duplicate routines
* View all routines

⸻

Exercise Library

The user can:

* Browse exercises
* Search exercises
* Add exercises to routines

Version 1 uses a predefined exercise library.

⸻

Workout Session

The user can:

* Start workout
* Finish workout
* Cancel workout

⸻

Workout Logging

For every exercise the user can:

* Add sets
* Edit sets
* Delete sets
* Record weight
* Record repetitions
* Record RPE
* Mark sets as completed

⸻

Timers

The application provides:

* Workout timer
* Rest timer

⸻

Workout History

The user can:

* View previous workouts
* Open workout details
* Review all recorded exercises
* Review all recorded sets

⸻

Offline Support

The application must:

* Work without internet
* Save workouts locally
* Allow uninterrupted workout logging

⸻

Data Synchronization

The application must:

* Synchronize automatically
* Retry failed synchronization
* Never lose workout data

⸻

7. Out of Scope (Version 1)

The following features are intentionally excluded:

* User authentication
* Login
* Registration
* Password management
* JWT
* Spring Security
* Multiple users
* Weight tracking
* Sleep tracking
* Nutrition tracking
* Water intake tracking
* Body measurements
* Progress photos
* Google Fit
* Health Connect
* Apple Health
* Smartwatch integration
* AI recommendations
* Workout suggestions
* Analytics dashboard
* Notifications
* Social features
* Exercise videos
* Cloud backups to third-party services

These features are reserved for future releases.

⸻

8. Functional Requirements

FR-1 Routine Management

The system shall allow the user to:

* Create routines
* Update routines
* Delete routines
* Duplicate routines

⸻

FR-2 Exercise Management

The system shall provide an exercise library.

The user shall be able to:

* Browse exercises
* Search exercises
* Add exercises to routines

⸻

FR-3 Workout Session

The system shall allow the user to:

* Start workout
* Finish workout
* Cancel workout

⸻

FR-4 Workout Logging

The system shall allow recording of:

* Weight
* Repetitions
* RPE
* Completed status

Unlimited sets shall be supported.

⸻

FR-5 Workout History

The system shall maintain complete workout history.

The user shall be able to:

* View previous workouts
* View workout details
* View exercise history

⸻

FR-6 Offline Mode

Workout logging shall continue functioning without internet.

⸻

FR-7 Synchronization

The application shall synchronize local data with the backend whenever internet connectivity is available.

Synchronization failures shall automatically retry.

⸻

9. Non-Functional Requirements

Performance

* Application startup under 2 seconds.
* Smooth navigation.
* Immediate response while logging workouts.

⸻

Reliability

Workout data must never be lost.

⸻

Offline Capability

Internet access must never be required during a workout.

⸻

Maintainability

The project should follow a modular architecture with clear separation of responsibilities.

⸻

Scalability

The architecture should support future modules without major redesign.

⸻

10. User Journey

1. Open the application.
2. View available workout routines.
3. Select a routine.
4. Start a workout.
5. Record each exercise and set.
6. Finish the workout.
7. Data is saved locally.
8. Data synchronizes automatically when internet becomes available.
9. View workout history from Android or the website.

⸻

11. Success Criteria

Version 1 is successful when the user can:

* Create workout routines.
* Perform an entire workout without internet.
* Record every set successfully.
* Finish workouts without data loss.
* Automatically synchronize workouts.
* View synchronized workout history on the website.

⸻

12. Future Roadmap

Version 2

* Authentication
* Multi-user support
* Dashboard
* Weight tracking

⸻

Version 3

* Nutrition tracking
* Water tracking
* Progress photos
* Body measurements

⸻

Version 4

* Health Connect
* Google Fit
* Apple Health
* Wearable integration

⸻

Version 5

* AI insights
* Advanced analytics
* Smart recommendations

⸻

13. Definition of Done

Version 1 is complete when:

* All functional requirements are implemented.
* Offline workout logging works correctly.
* Automatic synchronization is reliable.
* Workout history is accessible on Android and the web.
* Critical bugs are resolved.
* Documentation is complete.
* Source code is committed to GitHub.