package com.myfitnesslog.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the CORS policy a browser client depends on. Preflight is the part
 * that actually blocks the web app, so it is asserted directly rather than
 * inferred from the simple GET.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsConfigTest {

    private static final String ALLOWED = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromTheAllowedOriginPermitsGet() throws Exception {
        mockMvc.perform(options("/api/v1/workout-sessions")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET"))
                // No credentials: V1 has no authentication and sends no cookies.
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void simpleGetFromTheAllowedOriginIsReadable() throws Exception {
        mockMvc.perform(get("/api/v1/workout-sessions").header("Origin", ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    void preflightForAWriteVerbIsRejected() throws Exception {
        // The web client is read-only; Android is not a browser and never
        // preflights, so permitting POST here would widen the surface for no caller.
        mockMvc.perform(options("/api/v1/workout-sessions")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/workout-sessions")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
