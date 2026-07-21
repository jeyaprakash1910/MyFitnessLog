package com.myfitnesslog.exception;

/**
 * Thrown by the service layer when a requested resource does not exist. Mapped to
 * HTTP 404 Not Found by {@link GlobalExceptionHandler}. The message must be
 * client-safe (no internal detail) — it becomes the error envelope's "message".
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
