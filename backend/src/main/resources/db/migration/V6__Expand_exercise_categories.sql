-- V6__Expand_exercise_categories.sql
-- Splits the coarse V2 category set so the exercise-library filter stays useful
-- once the library grows to ~300 entries (V7). "Legs" alone would otherwise
-- hold roughly sixty exercises, which makes the filter a scroll rather than a
-- filter.
--
-- "Legs" is renamed in place to "Quads" rather than soft-deleted: the Android
-- category cache does not filter on isDeleted, so a soft-deleted row would
-- still appear in the picker. Renaming keeps the existing foreign keys valid
-- and avoids orphaning the seven exercises that already point at it.

UPDATE exercise_category
   SET name = 'Quads', display_order = 6
 WHERE id = '10000000-0000-0000-0000-000000000006';

INSERT INTO exercise_category (id, name, display_order, is_deleted) VALUES
    ('10000000-0000-0000-0000-000000000009', 'Hamstrings', 7,  false),
    ('10000000-0000-0000-0000-00000000000a', 'Glutes',     8,  false),
    ('10000000-0000-0000-0000-00000000000b', 'Calves',     9,  false),
    ('10000000-0000-0000-0000-00000000000c', 'Forearms',   12, false),
    ('10000000-0000-0000-0000-00000000000d', 'Full Body',  14, false),
    ('10000000-0000-0000-0000-00000000000e', 'Olympic',    15, false);

-- Core and Cardio keep their existing IDs; their display order is shifted to
-- sit after the new lower-body categories.
UPDATE exercise_category SET display_order = 10 WHERE id = '10000000-0000-0000-0000-000000000007';
UPDATE exercise_category SET display_order = 13 WHERE id = '10000000-0000-0000-0000-000000000008';

-- Reassign the exercises seeded under the old "Legs" category to the specific
-- muscle groups they actually train. Barbell Back Squat (…0024) and Leg Press
-- (…0025) stay under Quads, as does Leg Extension (…0027).
UPDATE exercise SET category_id = '10000000-0000-0000-0000-000000000009'
 WHERE id IN ('20000000-0000-0000-0000-000000000026',   -- Romanian Deadlift
                '20000000-0000-0000-0000-000000000028');  -- Leg Curl

UPDATE exercise SET category_id = '10000000-0000-0000-0000-00000000000a'
 WHERE id = '20000000-0000-0000-0000-000000000029';     -- Walking Lunge

UPDATE exercise SET category_id = '10000000-0000-0000-0000-00000000000b'
 WHERE id = '20000000-0000-0000-0000-000000000030';     -- Standing Calf Raise
