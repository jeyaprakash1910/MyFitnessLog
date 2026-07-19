package com.myfitnesslog.dto.response;

import java.time.Instant;

/**
 * Standard error response body returned by the API for all error responses.
 * Matches the format defined in docs/API_SPECIFICATION.md section 9.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
