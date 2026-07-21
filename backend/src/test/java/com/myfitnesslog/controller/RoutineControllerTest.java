package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for {@link RoutineController} over the full stack (controller
 * → service → repository → PostgreSQL). Transactional: each test rolls back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoutineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private String createBody(UUID id, String name) {
        return """
                {"id":"%s","name":"%s","description":"desc","displayOrder":0}
                """.formatted(id, name);
    }

    private UUID createRoutine(String name) throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(id, name)))
                .andExpect(status().isCreated());
        return id;
    }

    @Test
    void createReturns201ThenListAndGetReturnIt() throws Exception {
        UUID id = createRoutine("Push A");

        mockMvc.perform(get("/api/v1/routines/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Push A"));

        mockMvc.perform(get("/api/v1/routines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')].name").value("Push A"));
    }

    @Test
    void idempotentCreateReturns200OnReplay() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(id, "First")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(id, "Second")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Second"));
    }

    @Test
    void blankNameReturns400WithEnvelope() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(UUID.randomUUID(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Routine name must not be blank.")))
                .andExpect(jsonPath("$.path").value("/api/v1/routines"));
    }

    @Test
    void getMissingReturns404WithEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/routines/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Routine not found."));
    }

    @Test
    void updateReturns200AndMissingReturns404() throws Exception {
        UUID id = createRoutine("Pull");

        mockMvc.perform(put("/api/v1/routines/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Pull B","description":null,"displayOrder":3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Pull B"))
                .andExpect(jsonPath("$.displayOrder").value(3));

        mockMvc.perform(put("/api/v1/routines/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","description":null,"displayOrder":0}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204AndHidesRoutine() throws Exception {
        UUID id = createRoutine("Legs");

        mockMvc.perform(delete("/api/v1/routines/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/routines/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateReturns201WithNewIdAndCopyName() throws Exception {
        UUID id = createRoutine("Full Body");

        mockMvc.perform(post("/api/v1/routines/{id}/duplicate", id))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(id.toString())))
                .andExpect(jsonPath("$.name").value("Full Body (copy)"));
    }
}
