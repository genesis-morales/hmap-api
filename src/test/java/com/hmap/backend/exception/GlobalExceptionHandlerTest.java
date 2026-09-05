package com.hmap.backend.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Contrato de errores acordado con el frontend: mensaje legible en {@code detail}
 * y, cuando el error pertenece a un campo, el mismo texto en {@code errors} con la
 * clave en {@code snake_case}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @SuppressWarnings("unchecked")
    private Map<String, String> errorsOf(org.springframework.http.ProblemDetail problem) {
        var properties = problem.getProperties();
        return properties == null ? null : (Map<String, String>) properties.get("errors");
    }

    // === Validación declarativa de DTOs ===

    @Test
    void handleValidation_traduceLasClavesASnakeCase() throws Exception {
        var problem = handler.handleValidation(validationError(
                new FieldError("request", "lastName", "El apellido es obligatorio"),
                new FieldError("request", "email", "El correo no tiene un formato válido")));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Error de validación");
        assertThat(errorsOf(problem))
                .containsEntry("last_name", "El apellido es obligatorio")
                .containsEntry("email", "El correo no tiene un formato válido");
    }

    @Test
    void handleValidation_traduceCadaSegmentoDeUnaRutaAnidada() throws Exception {
        var problem = handler.handleValidation(validationError(
                new FieldError("request", "guest.lastName", "El apellido es obligatorio")));

        assertThat(errorsOf(problem)).containsKey("guest.last_name");
    }

    @Test
    void handleValidation_conservaElPrimerMensajePorCampo() throws Exception {
        var problem = handler.handleValidation(validationError(
                new FieldError("request", "password", "La contraseña es obligatoria"),
                new FieldError("request", "password", "Debe tener al menos 8 caracteres")));

        assertThat(errorsOf(problem)).containsEntry("password", "La contraseña es obligatoria");
    }

    // === Errores de negocio con campo ===

    @Test
    void handleBadRequest_conCampo_loAncla() {
        var problem = handler.handleBadRequest(
                new BadRequestException("La contraseña actual no es correcta", "current_password"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("La contraseña actual no es correcta");
        assertThat(errorsOf(problem))
                .containsEntry("current_password", "La contraseña actual no es correcta");
    }

    @Test
    void handleConflict_conCampo_loAncla() {
        var problem = handler.handleConflict(
                new ConflictException("Ya existe una cuenta con ese correo", "email"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(errorsOf(problem)).containsEntry("email", "Ya existe una cuenta con ese correo");
    }

    // === Errores de negocio sin campo (avisos globales) ===

    @Test
    void handleConflict_sinCampo_noAgregaErrors() {
        var problem = handler.handleConflict(new ConflictException("La reserva ya no puede cancelarse"));

        assertThat(problem.getDetail()).isEqualTo("La reserva ya no puede cancelarse");
        assertThat(errorsOf(problem)).isNull();
    }

    @Test
    void handleBadRequest_sinCampo_noAgregaErrors() {
        var problem = handler.handleBadRequest(new BadRequestException("Token inválido"));

        assertThat(errorsOf(problem)).isNull();
    }

    /** El login no revela qué credencial falló: evita enumerar usuarios. */
    @Test
    void handleAuthentication_noAtribuyeElErrorAUnCampo() {
        var problem = handler.handleAuthentication(
                new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getDetail()).isEqualTo("Credenciales inválidas");
        assertThat(errorsOf(problem)).isNull();
    }

    /**
     * Cuenta desactivada: 403 con un mensaje propio, no el 401 genérico, para
     * que el usuario no crea que erró la contraseña. El motivo interno de la
     * suspensión no viaja en la respuesta.
     */
    @Test
    void handleDisabled_respondeForbiddenConMensajePropio() {
        var problem = handler.handleDisabled(
                new org.springframework.security.authentication.DisabledException("User is disabled"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getDetail())
                .contains("su cuenta se encuentra desactivada")
                .contains("Hotel Manuel Antonio Park");
        assertThat(errorsOf(problem)).isNull();
    }

    private static MethodArgumentNotValidException validationError(FieldError... fieldErrors) throws Exception {
        var bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        List.of(fieldErrors).forEach(bindingResult::addError);

        // MethodArgumentNotValidException exige un MethodParameter real; cualquier
        // método sirve porque el handler solo consulta el BindingResult.
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class);
        var parameter = new org.springframework.core.MethodParameter(method, 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // Solo existe para construir el MethodParameter del test.
    }
}
