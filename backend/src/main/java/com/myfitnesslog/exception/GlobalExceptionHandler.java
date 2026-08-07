package com.myfitnesslog.exception;

import com.myfitnesslog.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centralized exception handling for the REST API.
 * Produces the error response format documented in docs/API_SPECIFICATION.md.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Validation failed.";
        }
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON request.", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Updates could not be resolved (not configured, or the artifact store is
     * unreachable). Logged at WARN, not ERROR: the caller did nothing wrong and
     * the sync API is unaffected, so this must not read as a backend fault.
     */
    @ExceptionHandler(AppUpdateUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAppUpdateUnavailable(
            AppUpdateUnavailableException ex, HttpServletRequest request) {
        log.warn("App update unavailable: {}", ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid request.", request);
    }

    /**
     * A path variable or query parameter that cannot be converted to its
     * declared type, most often a malformed UUID such as
     * {@code GET /api/v1/exercises/not-a-uuid}.
     *
     * <p>Without this the request reached the catch-all below and became a 500
     * logged at ERROR as "Unexpected error". That is wrong twice over: the
     * caller is told the server broke when it was their request that was
     * malformed, and a client typo shows up in monitoring as a backend fault.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for '" + ex.getName() + "'.", request);
    }

    /**
     * An unsupported method on a mapped path, for example {@code POST} to the
     * GET-only health endpoint. This was TD-001, open since Milestone 1: it fell
     * through to the catch-all and returned 500 instead of 405.
     *
     * <p>The {@code Allow} header is part of the contract for a 405, so it is
     * set from what the mapping actually supports rather than omitted.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response = build(
                HttpStatus.METHOD_NOT_ALLOWED,
                "Method " + ex.getMethod() + " is not supported for this endpoint.",
                request);
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported == null || supported.isEmpty()) {
            return response;
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(supported.toArray(HttpMethod[]::new))
                .body(response.getBody());
    }

    /**
     * No handler is mapped to the path at all.
     *
     * <p>This is the case that actually cost debugging time. On 2026-08-06 a
     * request to {@code GET /api/v1/app/latest-version} issued during a Render
     * deploy reached the previous container, where the route did not yet exist.
     * The 500 read as "the new code is broken", and the deploy was investigated
     * as a code defect until timestamps showed the old container had served it.
     * A 404 says "this route does not exist here" and points at the deploy
     * window immediately.
     *
     * <p>Logged at WARN rather than ERROR: an unknown path is a normal thing for
     * a public endpoint to be asked about, including by scanners.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("No handler for {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.NOT_FOUND, "No endpoint for this path.", request);
    }

    /**
     * A request for a media type the endpoint cannot produce or consume. Same
     * failure mode as the two above: a client-side mistake that was being
     * reported as a server fault.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type.", request);
    }

    /**
     * A required query parameter was not supplied. Note that this does not apply
     * to the exercise search, whose {@code q} is optional by design.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing.", request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
