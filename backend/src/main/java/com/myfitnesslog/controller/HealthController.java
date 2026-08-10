package com.myfitnesslog.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Liveness endpoint used to confirm the service is running.
 * Performs no database access and contains no business logic.
 */
@RestController
public class HealthController {

    /**
     * Whether this backend's data is disposable — true only under the
     * {@code livetest} profile.
     *
     * This exists so an automated test can tell a throwaway backend from the
     * system of record. Before M12 Phase 3 it could not: the Android live sync
     * test probed {@code localhost:8080}, took a reply as permission to write,
     * and put 71 test routines into the production database (TD-013).
     *
     * Reachability was never evidence of disposability. The two only coincided
     * while no backend ran locally; M9.5 dogfooding made one permanently
     * reachable and the assumption silently became false.
     *
     * The default is {@code false}, so a backend is treated as precious unless it
     * explicitly declares otherwise. A profile can opt in; nothing opts in by
     * accident, and no client-side guessing is involved.
     */
    @Value("${app.test-environment.disposable:false}")
    private boolean disposable;

    /**
     * The commit this instance was built from, so a deploy can be verified rather
     * than assumed.
     *
     * Render sets {@code RENDER_GIT_COMMIT} on every service automatically, so
     * this needs no build configuration and is empty anywhere else, including
     * locally and in tests.
     *
     * It exists because reachability is not evidence of freshness. On 2026-08-06 a
     * request issued during a deploy reached the *previous* container, where the
     * route did not yet exist, and the resulting error was investigated as a code
     * defect until the timestamps showed which container had answered. Nothing
     * could distinguish "the new build is live" from "something is answering", and
     * this is that distinction.
     */
    @Value("${RENDER_GIT_COMMIT:}")
    private String commit;

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "disposable", disposable,
                // Never null: Map.of rejects nulls, and an empty string is the
                // honest answer off Render rather than an invented one.
                "commit", commit
        );
    }
}
