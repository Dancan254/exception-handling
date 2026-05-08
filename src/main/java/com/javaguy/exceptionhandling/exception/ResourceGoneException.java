package com.javaguy.exceptionhandling.exception;

public class ResourceGoneException extends RuntimeException {

    public ResourceGoneException(String resource, Object id) {
        super(String.format("%s with id '%s' has been permanently removed and will not return", resource, id));
    }
}
