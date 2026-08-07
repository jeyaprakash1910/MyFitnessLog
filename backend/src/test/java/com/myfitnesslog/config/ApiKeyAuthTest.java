package com.myfitnesslog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the X-API-Key boundary with a key actually configured.
 *
 * <p>This is the only thing standing between a public Render URL and someone
 * else's training history, and until 2026-08-07 nothing tested it. The rest of
 * the suite runs with {@code app.api-key} blank, which is the development mode
 * where {@link ApiKeyAuthFilter} authenticates everything, so no other test can
 * observe a rejection.
 *
 * <p>The key is set here through {@code @TestPropertySource}, which gives this
 * class its own application context. That is the point: the enabled and disabled
 * behaviours cannot be exercised in one context.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.api-key=test-secret-key",
        // src/test/resources/application.yml shadows the main one, so CORS is off
        // unless a test asks for it. The preflight case below needs an allowed
        // origin, or CorsConfig rejects the request before the security chain is
        // reached and the test would pass or fail for the wrong reason.
        "app.cors.allowed-origins=http://localhost:5173",
})
class ApiKeyAuthTest {

    private static final String HEADER = "X-API-Key";
    private static final String KEY = "test-secret-key";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aRequestCarryingTheKeyIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, KEY))
                .andExpect(status().isOk());
    }

    @Test
    void aRequestWithNoKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRequestWithTheWrongKeyIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, "not-the-key"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * A near miss must fail. Guards against a comparison that is accidentally a
     * prefix or case-insensitive match rather than an equality check.
     */
    @Test
    void aKeyThatIsMerelyCloseIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, KEY + "x"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, "TEST-SECRET-KEY"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, "test-secret"))
                .andExpect(status().isUnauthorized());
    }

    /** The filter trims, so ordinary whitespace from a client config still works. */
    @Test
    void surroundingWhitespaceInTheKeyIsTolerated() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions").header(HEADER, "  " + KEY + "  "))
                .andExpect(status().isOk());
    }

    /**
     * Writes are protected too, not only reads. A deletion reaching an
     * unauthenticated caller would be worse than a read.
     */
    @Test
    void writesAreRejectedWithoutTheKey() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType("application/json")
                        .content("{\"name\":\"Should not be created\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Health is deliberately public so Render's probe works without holding the
     * secret. It must stay public, and must not leak anything else.
     */
    @Test
    void healthStaysPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    /**
     * Preflight carries no custom headers, so it cannot present the key. It is
     * permitted, and the real request that follows is still checked. Origin and
     * request-method headers are required or Spring does not treat it as CORS
     * preflight at all.
     */
    @Test
    void corsPreflightIsPermittedWithoutTheKey() throws Exception {
        mockMvc.perform(options("/api/v1/workout-sessions")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }
}
