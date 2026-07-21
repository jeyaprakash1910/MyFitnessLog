package com.myfitnesslog.exception;

/**
 * Thrown by the service layer when a request violates a business rule (for
 * example an illegal workout status transition). Mapped to HTTP 409 Conflict by
 * {@link GlobalExceptionHandler}. The message must be client-safe — it becomes
 * the error envelope's "message".
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
