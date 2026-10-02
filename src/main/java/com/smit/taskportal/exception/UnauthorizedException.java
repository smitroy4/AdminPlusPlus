package com.smit.taskportal.exception;

/** Caller is not authenticated (or credentials are wrong). Mapped to HTTP 401. */
public final class UnauthorizedException extends AppException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
