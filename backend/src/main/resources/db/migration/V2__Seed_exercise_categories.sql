-- V2__Seed_exercise_categories.sql
-- Seeds the predefined Version 1 exercise categories.
-- Fixed UUIDs ensure the seed data is deterministic across all installations.
-- Audit columns (createdAt, updatedAt) rely on the schema defaults.

INSERT INTO "ExerciseCategory" ("id", "name", "displayOrder", "isDeleted") VALUES
    ('10000000-0000-0000-0000-000000000001', 'Chest',     1, false),
    ('10000000-0000-0000-0000-000000000002', 'Back',      2, false),
    ('10000000-0000-0000-0000-000000000003', 'Shoulders', 3, false),
    ('10000000-0000-0000-0000-000000000004', 'Biceps',    4, false),
    ('10000000-0000-0000-0000-000000000005', 'Triceps',   5, false),
    ('10000000-0000-0000-0000-000000000006', 'Legs',      6, false),
    ('10000000-0000-0000-0000-000000000007', 'Core',      7, false),
    ('10000000-0000-0000-0000-000000000008', 'Cardio',    8, false);
