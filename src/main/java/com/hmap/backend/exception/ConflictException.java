package com.hmap.backend.exception;

/** Se lanza cuando la petición choca con el estado actual del recurso (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
