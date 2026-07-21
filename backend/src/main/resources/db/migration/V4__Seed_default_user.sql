-- V4__Seed_default_user.sql
-- Seeds the single Version 1 application user.
-- Version 1 is single-user with no authentication (see PRD.md / DATABASE.md).
-- The Android client does not send a userId; the backend attaches this user to
-- every routine and workout session (see DefaultUserProvider). The fixed id must
-- match DefaultUserProvider.DEFAULT_USER_ID.
-- ON CONFLICT keeps the statement itself idempotent if ever replayed.

INSERT INTO "User" ("id", "createdAt", "updatedAt", "isDeleted")
VALUES ('00000000-0000-0000-0000-000000000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
ON CONFLICT ("id") DO NOTHING;
