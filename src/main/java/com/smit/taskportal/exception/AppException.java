package com.smit.taskportal.exception;

/**
 * Root of the application's expected-failure hierarchy. Being {@code sealed}
 * guarantees that {@code GlobalExceptionHandler} can exhaustively cover every
 * application error.
 */
public sealed abstract class AppException extends RuntimeException
        permits ResourceNotFoundException, UnauthorizedException, ForbiddenException,
        BadRequestException, DuplicateResourceException {

    protected AppException(String message) {
        super(message);
    }

    protected AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
