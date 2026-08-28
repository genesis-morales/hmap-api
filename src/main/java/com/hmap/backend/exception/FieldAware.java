package com.hmap.backend.exception;

/**
 * Excepción de negocio que puede atribuirse a un campo concreto del formulario.
 *
 * <p>Cuando el campo está presente, {@code GlobalExceptionHandler} añade el
 * mensaje al mapa {@code errors} de la respuesta (además de {@code detail}), y el
 * frontend puede anclarlo debajo del input correspondiente en lugar de mostrar un
 * aviso global. Un error sin campo (un conflicto de estado, por ejemplo) no
 * pertenece a ningún input y se muestra como notificación.
 *
 * <p>El nombre del campo debe ser el del <b>JSON</b> ({@code snake_case}), que es
 * el que conoce el frontend: {@code current_password}, no {@code currentPassword}.
 */
public interface FieldAware {

    /** Campo del JSON al que pertenece el error, o {@code null} si no aplica. */
    String getField();
}
