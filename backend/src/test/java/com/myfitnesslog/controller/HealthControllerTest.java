package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The health endpoint's contract.
 *
 * <p>Three fields, each with a caller that depends on it: {@code status} for
 * liveness probes, {@code disposable} so an automated test can tell a throwaway
 * backend from the system of record (TD-013), and {@code commit} so a deploy can
 * be verified rather than assumed.
 *
 * <p>{@code commit} exists because reachability is not evidence of freshness. On
 * 2026-08-06 a request issued during a deploy reached the previous container and
 * the resulting error was investigated as a code defect until the timestamps
 * showed which container had answered.
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsLivenessAndIsNotDisposableByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // Precious unless a profile explicitly opts in. Nothing opts in by
                // accident, which is the whole point of TD-013's fix.
                .andExpect(jsonPath("$.disposable").value(false));
    }

    /**
     * The field is always present, even off Render where the variable is unset.
     * A caller polling for a specific commit needs to distinguish "not that
     * commit" from "this backend cannot tell me", and an absent field would make
     * those the same.
     */
    @Test
    void reportsAnEmptyCommitWhenNotRunningOnRender() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commit").exists())
                .andExpect(jsonPath("$.commit").value(""));
    }

    /** Health is public, so a probe works without holding the API key. */
    @Test
    void isReachableWithoutAnApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }
}

/**
 * The same endpoint with Render's variable present, which is how it behaves in
 * production and what the post-deploy smoke check reads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "RENDER_GIT_COMMIT=abc123def4567890")
class HealthControllerOnRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsTheCommitItWasBuiltFrom() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commit").value("abc123def4567890"));
    }
}
