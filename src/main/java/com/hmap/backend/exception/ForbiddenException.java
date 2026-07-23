package com.hmap.backend.exception;

/**
 * Se lanza cuando el usuario autenticado no tiene permiso sobre el recurso (HTTP 403).
 * Nunca usar 401 para esto: el frontend cierra la sesión ante cualquier 401.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
