package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The app-update endpoints on a backend that has no artifact store configured
 * (API_SPECIFICATION.md §7, ADR-0016).
 *
 * <p>This is the default state of every test run and of any deployment that does
 * not set {@code app.update.*}, which makes it the case most likely to be hit and
 * the one worth pinning down. Two properties matter:
 *
 * <ul>
 *   <li><b>503, not 500.</b> The request was valid; the backend simply cannot
 *       answer it yet. A 500 would tell the client the server is broken, and the
 *       Android client would still stay quiet — but the distinction is what stops
 *       a missing configuration from looking like a defect in the logs.
 *   <li><b>The standard error envelope</b> (§9), like every other error. Nothing
 *       about this endpoint justifies a bespoke error shape.
 * </ul>
 *
 * <p>Unlike {@code GitHubAppUpdateServiceTest}, which proves the service throws,
 * this proves the exception reaches the client as the documented HTTP contract.
 * Those are different claims: the mapping lives in GlobalExceptionHandler and can
 * be removed without any service test noticing.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AppUpdateUnavailableContractTest {

    @Autowired
    private MockMvc mockMvc;

    /** Asserts the full envelope shape (mirrors ErrorContractIntegrationTest). */
    private void assertEnvelope(ResultActions result, String path) throws Exception {
        result
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value(path));
    }

    @Test
    void latestVersionReportsServiceUnavailableWhenUpdatesAreNotConfigured() throws Exception {
        String path = "/api/v1/app/latest-version";
        ResultActions result = mockMvc.perform(get(path))
                .andExpect(status().isServiceUnavailable());
        assertEnvelope(result, path);
    }

    @Test
    void apkDownloadReportsServiceUnavailableWhenUpdatesAreNotConfigured() throws Exception {
        // The download must fail the same way as the check. A client that saw a
        // check succeed and a download 500 would have no way to tell "not set up"
        // from "the file is broken".
        String path = "/api/v1/app/apk";
        ResultActions result = mockMvc.perform(get(path))
                .andExpect(status().isServiceUnavailable());
        assertEnvelope(result, path);
    }
}
