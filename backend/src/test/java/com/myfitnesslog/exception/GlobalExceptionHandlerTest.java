package com.myfitnesslog.exception;

import com.myfitnesslog.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for the 404/409 handlers added in Milestone 8 Phase 1. No
 * Spring context needed — the handler is exercised directly with a stubbed
 * request, asserting the documented error envelope (API_SPECIFICATION.md §9).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest requestFor(String uri) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        return request;
    }

    @Test
    void notFoundProducesA404Envelope() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(
                new ResourceNotFoundException("Routine not found."),
                requestFor("/api/v1/routines/123"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.message()).isEqualTo("Routine not found.");
        assertThat(body.path()).isEqualTo("/api/v1/routines/123");
        assertThat(body.timestamp()).isNotNull();
    }

    @Test
    void businessRuleProducesA409Envelope() {
        ResponseEntity<ErrorResponse> response = handler.handleBusinessRule(
                new BusinessRuleException("Workout is already completed."),
                requestFor("/api/v1/workout-sessions/abc/complete"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(409);
        assertThat(body.error()).isEqualTo("Conflict");
        assertThat(body.message()).isEqualTo("Workout is already completed.");
        assertThat(body.path()).isEqualTo("/api/v1/workout-sessions/abc/complete");
    }
}
