package com.myfitnesslog.exception;

/**
 * Thrown when the app-update feature cannot answer - not because the caller did
 * anything wrong, but because the backend cannot reach or has not been given an
 * artifact store (see {@code app.update.*}).
 *
 * <p>Mapped to <b>503 Service Unavailable</b>, which is the honest status: the
 * request was valid and may succeed later. The distinction matters to the client,
 * which treats "cannot check right now" as a silent non-event rather than as an
 * error worth interrupting a workout for.
 */
public class AppUpdateUnavailableException extends RuntimeException {

    public AppUpdateUnavailableException(String message) {
        super(message);
    }

    public AppUpdateUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
