package com.hmap.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Traduce las excepciones de la aplicación a respuestas HTTP consistentes.
 *
 * <p>Contrato de errores acordado con el frontend:
 * <ul>
 *   <li>{@code detail} — mensaje legible en español, apto para mostrarse tal cual.</li>
 *   <li>{@code errors} — mapa {@code campo → mensaje} presente cuando el error se
 *       puede atribuir a uno o más campos del formulario. El frontend lo ancla al
 *       input correspondiente; si no viene, muestra {@code detail} como aviso.</li>
 * </ul>
 * Las claves de {@code errors} van en {@code snake_case}, igual que el resto del
 * JSON, para que coincidan con los nombres de los campos del formulario.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Validación declarativa de los DTOs ({@code @NotBlank}, {@code @Size}, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // LinkedHashMap: conserva el orden de declaración de los campos del DTO,
        // así el frontend puede enfocar el primer input con error.
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(toSnakeCase(error.getField()), error.getDefaultMessage()));

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Error de validación");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problemWithField(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problemWithField(HttpStatus.CONFLICT, ex);
    }

    /** Parámetros de query malformados (ej. fechas inválidas en /rooms/availability). */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ProblemDetail handleBadParams(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Parámetros de búsqueda inválidos");
    }

    /**
     * Login de una cuenta desactivada. Spring Security ya lo rechaza vía
     * {@code Auth.isEnabled()}; este manejador solo sustituye el mensaje
     * genérico de credenciales por uno que le dice al usuario qué pasó.
     *
     * <p>Se declara antes del manejador genérico de {@link AuthenticationException}
     * para que Spring lo elija por ser el tipo más concreto.
     *
     * <p>403 y no 401: las credenciales eran correctas, lo que falta es permiso.
     * El frontend no debe cerrar sesión ante este 403 (RNF-001). El motivo
     * registrado por la administración no se expone aquí.
     */
    @ExceptionHandler(DisabledException.class)
    public ProblemDetail handleDisabled(DisabledException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "Estimado usuario, su cuenta se encuentra desactivada. "
                + "En caso de consulta, comuníquese con la administración "
                + "del Hotel Manuel Antonio Park.");
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Credenciales inválidas");
    }

    /**
     * Acceso denegado por rol (@PreAuthorize). Se responde 403 (no 401) para que
     * el frontend no cierre la sesión del usuario (RNF-001).
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "No tienes permiso para esta acción");
    }

    /**
     * Construye la respuesta de una excepción de negocio. Si la excepción declara
     * un campo, el mensaje se repite en {@code errors} para que el frontend lo
     * ancle al input; si no, solo viaja en {@code detail} y se muestra como aviso.
     */
    private ProblemDetail problemWithField(HttpStatusCode status, FieldAware ex) {
        var problem = ProblemDetail.forStatusAndDetail(status, ((RuntimeException) ex).getMessage());
        var field = ex.getField();
        if (field != null && !field.isBlank()) {
            problem.setProperty("errors", Map.of(field, ((RuntimeException) ex).getMessage()));
        }
        return problem;
    }

    /**
     * Convierte el nombre de una propiedad Java al del JSON: {@code lastName} →
     * {@code last_name}. Bean Validation reporta el nombre del campo del record,
     * pero el frontend conoce el del JSON (los DTOs usan {@code @JsonProperty}).
     *
     * <p>Para rutas anidadas convierte cada segmento por separado
     * ({@code guest.lastName} → {@code guest.last_name}).
     */
    private static String toSnakeCase(String field) {
        var result = new StringBuilder(field.length() + 4);
        for (int i = 0; i < field.length(); i++) {
            var c = field.charAt(i);
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
