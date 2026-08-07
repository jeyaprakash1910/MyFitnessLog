package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for the exercise resource.
 *
 * <p>These endpoints had no coverage at all until 2026-08-07, which mattered
 * more than the count suggested: this is the catalogue Android downloads when it
 * rebuilds an empty device (ADR-0017 Stage 1), so a regression here breaks a
 * fresh install and the restore path silently produces a workout log with no
 * exercise names.
 *
 * <p>Assertions are deliberately about contract and filtering rather than exact
 * counts, because the seeded catalogue grows (V3, then V7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseControllerTest {

    /** A stable seeded row: "Barbell Bench Press", from V3. */
    private static final String SEEDED_ID = "20000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void listReturnsTheSeededCatalogue() throws Exception {
        mockMvc.perform(get("/api/v1/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(100))));
    }

    /**
     * The restore path maps these fields straight into Room, so a rename or a
     * dropped field breaks a rebuilt device rather than merely a screen.
     */
    @Test
    void eachExerciseCarriesTheFieldsRestoreDependsOn() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/" + SEEDED_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEEDED_ID))
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.categoryId").exists());
    }

    @Test
    void anUnknownIdIs404() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/00000000-0000-0000-0000-0000000000ff"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMalformedIdIsRejectedRatherThanTreatedAsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/not-a-uuid"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void searchMatchesOnNameCaseInsensitively() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search").param("q", "bench"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$[*].name",
                        everyItem(containsStringIgnoringCase("bench"))));
    }

    /** Upper case must behave identically, or the client has to normalise. */
    @Test
    void searchIgnoresTheCaseOfTheQuery() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search").param("q", "BENCH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    @Test
    void searchSurroundedByWhitespaceStillMatches() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search").param("q", "  bench  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }

    /**
     * An absent or empty query returns everything rather than nothing. The
     * client relies on this for its "no filter typed yet" state.
     */
    @Test
    void anEmptyOrAbsentQueryReturnsEverything() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(100))));

        mockMvc.perform(get("/api/v1/exercises/search").param("q", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(100))));
    }

    @Test
    void searchThatMatchesNothingReturnsAnEmptyListNotAnError() throws Exception {
        mockMvc.perform(get("/api/v1/exercises/search").param("q", "zzzzz-no-such-exercise"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    /**
     * Withdrawn rows are retained rather than deleted (TD-012), so every read
     * path has to filter them. If it did not, a restored device would show
     * exercises the catalogue has retired.
     *
     * <p>The rollback from {@code @Transactional} undoes this update.
     */
    @Test
    void withdrawnExercisesAreHiddenFromEveryReadPath() throws Exception {
        String name = jdbc.queryForObject(
                "SELECT name FROM exercise WHERE id = ?::uuid", String.class, SEEDED_ID);
        jdbc.update("UPDATE exercise SET is_deleted = TRUE WHERE id = ?::uuid", SEEDED_ID);

        mockMvc.perform(get("/api/v1/exercises/" + SEEDED_ID))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/exercises"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(SEEDED_ID))));

        mockMvc.perform(get("/api/v1/exercises/search").param("q", name))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", everyItem(not(SEEDED_ID))));
    }
}
