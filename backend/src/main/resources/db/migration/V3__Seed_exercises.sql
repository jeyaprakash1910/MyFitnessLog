-- V3__Seed_exercises.sql
-- Seeds the predefined Version 1 exercise library.
-- Fixed UUIDs ensure the seed data is deterministic across all installations.
-- categoryId values reference the categories seeded in V2.
-- Audit columns (createdAt, updatedAt) rely on the schema defaults.

INSERT INTO "Exercise" ("id", "categoryId", "name", "description", "instructions", "equipment", "isDeleted") VALUES
    -- Chest
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'Barbell Bench Press', 'Compound chest press with a barbell.', 'Lie on a flat bench and press the barbell from your chest to full extension.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'Incline Dumbbell Press', 'Upper-chest press on an incline bench.', 'Press two dumbbells upward while seated on an inclined bench.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'Chest Press Machine', 'Guided chest press suitable for beginners.', 'Sit at the machine and press the handles forward until your arms extend.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'Cable Chest Fly', 'Chest isolation using cables.', 'Bring both cable handles together in front of your chest with a slight elbow bend.', 'Cable', false),
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'Push-Up', 'Bodyweight chest exercise.', 'Lower your chest to the floor and press back up while keeping your body straight.', 'Bodyweight', false),
    -- Back
    ('20000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000002', 'Lat Pulldown', 'Vertical pull for the lats.', 'Pull the bar down to your upper chest while seated, then control it back up.', 'Cable', false),
    ('20000000-0000-0000-0000-000000000007', '10000000-0000-0000-0000-000000000002', 'Seated Cable Row', 'Horizontal pull for the mid-back.', 'Pull the handle to your torso while keeping your back straight.', 'Cable', false),
    ('20000000-0000-0000-0000-000000000008', '10000000-0000-0000-0000-000000000002', 'Bent-Over Barbell Row', 'Compound back row with a barbell.', 'Hinge at the hips and pull the barbell to your lower ribs.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000009', '10000000-0000-0000-0000-000000000002', 'Single-Arm Dumbbell Row', 'Unilateral back row.', 'Support one hand on a bench and row the dumbbell to your hip.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000010', '10000000-0000-0000-0000-000000000002', 'Deadlift', 'Full-body posterior-chain lift.', 'Lift the barbell from the floor by extending your hips and knees together.', 'Barbell', false),
    -- Shoulders
    ('20000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000003', 'Overhead Barbell Press', 'Compound shoulder press.', 'Press the barbell overhead from shoulder height until your arms extend.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000003', 'Dumbbell Shoulder Press', 'Shoulder press with dumbbells.', 'Press two dumbbells overhead while seated or standing.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000013', '10000000-0000-0000-0000-000000000003', 'Dumbbell Lateral Raise', 'Side-delt isolation.', 'Raise the dumbbells out to your sides until they are level with your shoulders.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000014', '10000000-0000-0000-0000-000000000003', 'Front Raise', 'Front-delt isolation.', 'Raise the dumbbells straight in front of you to shoulder height.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000015', '10000000-0000-0000-0000-000000000003', 'Rear Delt Fly', 'Rear-delt isolation.', 'Bend forward and raise the dumbbells out to your sides.', 'Dumbbell', false),
    -- Biceps
    ('20000000-0000-0000-0000-000000000016', '10000000-0000-0000-0000-000000000004', 'Barbell Curl', 'Compound biceps curl.', 'Curl the barbell from your thighs to your shoulders while keeping your elbows still.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000017', '10000000-0000-0000-0000-000000000004', 'Dumbbell Bicep Curl', 'Standard biceps curl.', 'Curl the dumbbells upward while keeping your elbows at your sides.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000018', '10000000-0000-0000-0000-000000000004', 'Hammer Curl', 'Neutral-grip biceps curl.', 'Curl the dumbbells with your palms facing each other.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000019', '10000000-0000-0000-0000-000000000004', 'Cable Curl', 'Constant-tension biceps curl.', 'Curl the cable handle upward while keeping your elbows fixed.', 'Cable', false),
    -- Triceps
    ('20000000-0000-0000-0000-000000000020', '10000000-0000-0000-0000-000000000005', 'Tricep Pushdown', 'Triceps isolation with a cable.', 'Push the cable attachment down until your arms fully extend.', 'Cable', false),
    ('20000000-0000-0000-0000-000000000021', '10000000-0000-0000-0000-000000000005', 'Overhead Tricep Extension', 'Triceps stretch and extension.', 'Lower the dumbbell behind your head, then extend your arms overhead.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000022', '10000000-0000-0000-0000-000000000005', 'Close-Grip Bench Press', 'Compound triceps press.', 'Press a barbell with a narrow grip while focusing on the triceps.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000023', '10000000-0000-0000-0000-000000000005', 'Bodyweight Bench Dip', 'Bodyweight triceps exercise.', 'Lower your body off a bench by bending your elbows, then press back up.', 'Bodyweight', false),
    -- Legs
    ('20000000-0000-0000-0000-000000000024', '10000000-0000-0000-0000-000000000006', 'Barbell Back Squat', 'Compound lower-body squat.', 'Squat down with the barbell on your upper back, then stand back up.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000025', '10000000-0000-0000-0000-000000000006', 'Leg Press', 'Guided compound leg press.', 'Press the platform away by extending your knees and hips.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000026', '10000000-0000-0000-0000-000000000006', 'Romanian Deadlift', 'Hamstring and glute hip hinge.', 'Lower the barbell along your legs by hinging at the hips, then stand up.', 'Barbell', false),
    ('20000000-0000-0000-0000-000000000027', '10000000-0000-0000-0000-000000000006', 'Leg Extension', 'Quadriceps isolation.', 'Extend your knees to raise the pad, then lower it under control.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000028', '10000000-0000-0000-0000-000000000006', 'Leg Curl', 'Hamstring isolation.', 'Curl the pad toward your glutes, then lower it slowly.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000029', '10000000-0000-0000-0000-000000000006', 'Walking Lunge', 'Unilateral leg exercise.', 'Step forward into a lunge and alternate legs as you walk.', 'Dumbbell', false),
    ('20000000-0000-0000-0000-000000000030', '10000000-0000-0000-0000-000000000006', 'Standing Calf Raise', 'Calf isolation.', 'Raise your heels as high as possible, then lower them under control.', 'Machine', false),
    -- Core
    ('20000000-0000-0000-0000-000000000031', '10000000-0000-0000-0000-000000000007', 'Plank', 'Core stability hold.', 'Hold a straight-body position on your forearms and toes.', 'Bodyweight', false),
    ('20000000-0000-0000-0000-000000000032', '10000000-0000-0000-0000-000000000007', 'Abdominal Crunch', 'Abdominal flexion exercise.', 'Curl your shoulders toward your hips, then lower back down.', 'Bodyweight', false),
    ('20000000-0000-0000-0000-000000000033', '10000000-0000-0000-0000-000000000007', 'Hanging Knee Raise', 'Lower-ab exercise.', 'Hang from a bar and raise your knees toward your chest.', 'Bodyweight', false),
    ('20000000-0000-0000-0000-000000000034', '10000000-0000-0000-0000-000000000007', 'Russian Twist', 'Rotational core exercise.', 'Rotate your torso side to side while seated with your feet raised.', 'Bodyweight', false),
    ('20000000-0000-0000-0000-000000000035', '10000000-0000-0000-0000-000000000007', 'Cable Woodchopper', 'Rotational core exercise with a cable.', 'Pull the cable diagonally across your body in a chopping motion.', 'Cable', false),
    -- Cardio
    ('20000000-0000-0000-0000-000000000036', '10000000-0000-0000-0000-000000000008', 'Treadmill Run', 'Running cardio on a treadmill.', 'Run at a steady pace for your chosen duration.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000037', '10000000-0000-0000-0000-000000000008', 'Exercise Bike', 'Cycling cardio.', 'Pedal at a steady resistance for your chosen duration.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000038', '10000000-0000-0000-0000-000000000008', 'Rowing Machine', 'Full-body cardio.', 'Drive with your legs, then pull the handle to your torso repeatedly.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000039', '10000000-0000-0000-0000-000000000008', 'Elliptical Trainer', 'Low-impact cardio.', 'Stride on the pedals while moving the handles for your chosen duration.', 'Machine', false),
    ('20000000-0000-0000-0000-000000000040', '10000000-0000-0000-0000-000000000008', 'Jump Rope', 'Bodyweight cardio.', 'Jump over the rope with a steady rhythm.', 'Bodyweight', false);
