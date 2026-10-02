package com.smit.taskportal.exception;

/** Caller is authenticated but lacks the required role / ownership. HTTP 403. */
public final class ForbiddenException extends AppException {

    public ForbiddenException(String message) {
        super(message);
    }
}
