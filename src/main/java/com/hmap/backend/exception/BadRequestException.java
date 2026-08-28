package com.hmap.backend.exception;

/** Se lanza cuando la petición es inválida según las reglas de negocio. */
public class BadRequestException extends RuntimeException implements FieldAware {

    private final String field;

    public BadRequestException(String message) {
        this(message, null);
    }

    /**
     * @param field campo del JSON ({@code snake_case}) al que pertenece el error,
     *              para que el frontend lo ancle al input correspondiente.
     */
    public BadRequestException(String message, String field) {
        super(message);
        this.field = field;
    }

    @Override
    public String getField() {
        return field;
    }
}
