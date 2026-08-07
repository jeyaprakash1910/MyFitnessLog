package com.myfitnesslog.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the exceptions Spring itself raises before a controller is reached.
 *
 * <p>These all used to fall through to the catch-all {@code Exception} handler
 * and come back as <b>500, logged at ERROR as "Unexpected error"</b>. That is
 * wrong twice over: the caller is told the server broke when their request was
 * malformed, and ordinary client mistakes appear in monitoring as backend
 * faults, which is exactly the noise that hides a real incident.
 *
 * <p>The 405 case is TD-001, open since Milestone 1. It was found again on
 * 2026-08-07 when the first test of {@code GET /api/v1/exercises/{id}} sent a
 * malformed UUID and got a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FrameworkErrorContractTest {

    @Autowired
    private MockMvc mockMvc;

    /** A path variable that is not a UUID is the caller's mistake, so 400. */
    @Test
    void aMalformedUuidPathVariableIs400() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(containsString("id")))
                .andExpect(jsonPath("$.path").value("/api/v1/exercises/not-a-uuid"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void aMalformedUuidOnAnotherResourceBehavesTheSame() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions/still-not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * TD-001. Health is GET-only, so a POST must be 405 with an {@code Allow}
     * header naming what is permitted, not 500.
     */
    @Test
    void anUnsupportedMethodIs405WithAnAllowHeader() throws Exception {
        mockMvc.perform(post("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                .andExpect(jsonPath("$.message").value(containsString("POST")))
                .andExpect(jsonPath("$.path").value("/api/v1/health"));
    }

    @Test
    void anUnsupportedMethodOnAResourceCollectionIs405() throws Exception {
        mockMvc.perform(post("/api/v1/exercises"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    /**
     * TD-001's expensive case. An unmapped path must be 404, not 500.
     *
     * <p>On 2026-08-06 a request during a Render deploy hit the previous
     * container, where the route did not exist. The 500 sent the investigation
     * after a non-existent code defect for as long as it took to check the
     * timestamps.
     */
    @Test
    void anUnmappedPathIs404() throws Exception {
        mockMvc.perform(get("/api/v1/no-such-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/v1/no-such-endpoint"));
    }

    /** The specific shape that caused it: a route added in a later deploy. */
    @Test
    void aRouteMissingFromAnOlderDeployIs404() throws Exception {
        mockMvc.perform(get("/api/v1/app/some-future-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    /** A body the endpoint cannot consume is 415, not 500. */
    @Test
    void anUnsupportedMediaTypeIs415() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("name=Legs"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    /** Malformed JSON was already handled; asserted here so it stays that way. */
    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    /**
     * The search parameter is optional by design, so its absence must stay a
     * 200 rather than becoming a 400 when the missing-parameter handler was
     * added.
     */
    @Test
    void theOptionalSearchParameterIsStillOptional() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search"))
                .andExpect(status().isOk());
    }
}
