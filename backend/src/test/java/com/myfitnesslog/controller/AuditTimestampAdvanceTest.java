package com.myfitnesslog.controller;

import com.myfitnesslog.repository.RoutineRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * That {@code updatedAt} actually advances when a row changes.
 *
 * <p>This is the property ADR-0017 Stage 3 depends on, and the one a presence
 * check cannot catch: a field that is always populated but never moves would
 * pass every assertion in {@link AuditTimestampReadContractTest} while making
 * last-write-wins silently pick the same side forever.
 *
 * <p><b>Why this class is not {@code @Transactional}</b>, unlike every other
 * test here. Hibernate fires {@code @PreUpdate}, and therefore Spring Data's
 * {@code @LastModifiedDate}, once per flush. Inside a single test transaction
 * the create and the update collapse into one flush and produce one timestamp,
 * so the assertion below fails against a backend that is behaving perfectly.
 * That was verified rather than assumed: against a running server, where each
 * request is its own transaction, {@code createdAt} held at
 * {@code ...26.501849Z} while {@code updatedAt} moved to {@code ...27.625462Z}.
 *
 * <p>The cost of dropping {@code @Transactional} is that nothing rolls the rows
 * back, so this class deletes what it creates in {@link #cleanUp()}. Only
 * routines are created here, and only by id, so the cleanup cannot touch
 * anything else.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuditTimestampAdvanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoutineRepository routineRepository;

    private UUID createdId;

    @AfterEach
    void cleanUp() {
        if (createdId != null) {
            routineRepository.deleteById(createdId);
            createdId = null;
        }
    }

    @Test
    void updatedAtAdvancesWhenTheRoutineChangesAndCreatedAtDoesNot() throws Exception {
        createdId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","name":"Before","description":"d","displayOrder":0}
                                """.formatted(createdId)))
                .andExpect(status().isCreated());

        MvcResult first = mockMvc.perform(get("/api/v1/routines/{id}", createdId))
                .andExpect(status().isOk()).andReturn();
        Instant createdBefore = readInstant(first, "createdAt");
        Instant updatedBefore = readInstant(first, "updatedAt");

        mockMvc.perform(put("/api/v1/routines/{id}", createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"After","description":"d","displayOrder":0}
                                """))
                .andExpect(status().isOk());

        MvcResult second = mockMvc.perform(get("/api/v1/routines/{id}", createdId))
                .andExpect(status().isOk()).andReturn();

        assertThat(readInstant(second, "updatedAt"))
                .as("updatedAt must advance when the row changes, "
                        + "or last-write-wins has nothing to arbitrate on")
                .isAfter(updatedBefore);

        assertThat(readInstant(second, "createdAt"))
                .as("createdAt must not move when the row changes")
                .isEqualTo(createdBefore);
    }

    private Instant readInstant(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        return Instant.parse(com.jayway.jsonpath.JsonPath.read(json, "$." + field));
    }
}
