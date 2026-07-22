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

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "disposable", disposable
        );
    }
}
