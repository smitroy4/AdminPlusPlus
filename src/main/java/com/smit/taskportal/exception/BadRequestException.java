package com.smit.taskportal.exception;

/** Input failed a business rule or bean validation. Mapped to HTTP 400. */
public final class BadRequestException extends AppException {

    public BadRequestException(String message) {
        super(message);
    }
}
