package com.hmap.backend.exception;

/** Se lanza cuando la petición choca con el estado actual del recurso (HTTP 409). */
public class ConflictException extends RuntimeException implements FieldAware {

    private final String field;

    public ConflictException(String message) {
        this(message, null);
    }

    /**
     * @param field campo del JSON ({@code snake_case}) al que pertenece el error,
     *              para que el frontend lo ancle al input correspondiente. Los
     *              conflictos de estado (una reserva que ya no se puede cancelar)
     *              no llevan campo: no pertenecen a ningún input.
     */
    public ConflictException(String message, String field) {
        super(message);
        this.field = field;
    }

    @Override
    public String getField() {
        return field;
    }
}
