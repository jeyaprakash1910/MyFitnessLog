-- Supports the workout-history query: WHERE "status" = 'COMPLETED' ORDER BY "startedAt" DESC.
--
-- V1 indexed "startedAt" alone and did not index "status" at all, so the history
-- query could use an index for the ordering or the filter but never both. A
-- composite in the query's own column order and direction serves the whole
-- statement, and lets a future LIMIT stop scanning after the first page.
CREATE INDEX idx_workoutsession_status_startedat
    ON workout_session (status, started_at DESC);
