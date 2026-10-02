package com.smit.taskportal.exception;

/** Unique constraint violation (duplicate username / email). Mapped to HTTP 409. */
public final class DuplicateResourceException extends AppException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
