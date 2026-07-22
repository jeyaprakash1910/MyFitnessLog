package com.myfitnesslog.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP-level tests for the exercise-category resource.
 *
 * The ordering contract matters to more than presentation: Android caches
 * categories locally, so it can only reproduce the catalogue's intended grouping
 * if the position is part of the response. It previously was not, and the client
 * silently fell back to alphabetical (M11 Phase 1, defect D-2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExerciseCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void categoriesExposeDisplayOrderAndAreReturnedInIt() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-categories"))
                .andExpect(status().isOk())
                // Every row carries the field...
                .andExpect(jsonPath("$[0].displayOrder").exists())
                // ...and the list is already sorted by it, so a client that
                // preserves order sees the curated grouping without re-sorting.
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[0].name").value("Chest"));
    }

    @Test
    void categoriesExposeIdAndName() throws Exception {
        mockMvc.perform(get("/api/v1/exercise-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].name").exists());
    }
}
