package com.hmap.backend.exception;

/** Se lanza cuando la petición es inválida según las reglas de negocio. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
