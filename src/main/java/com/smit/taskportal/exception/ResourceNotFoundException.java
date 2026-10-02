package com.smit.taskportal.exception;

/** Requested entity does not exist. Mapped to HTTP 404. */
public final class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String entity, Object id) {
        return new ResourceNotFoundException("%s not found with id %s".formatted(entity, id));
    }
}
