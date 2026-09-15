package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    /**
     * This handler is a global safety net for ANY database constraint violation that
     * slips past application-level validation (e.g. the TOCTOU race in
     * AuthController#register, where two concurrent registrations with the same
     * username can both pass the existsByUsername check before either saves). It
     * verifies the handler maps DataIntegrityViolationException to 409 Conflict with a
     * generic message, instead of an unhandled 500 or a message specific to one caller.
     */
    @Test
    void handleDataIntegrityViolation_returnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violation");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().message())
                .isEqualTo("Conflicto de datos: la operación viola una restricción de la base de datos");
    }
}
