package dev.springbootdocs.examples.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    /**
     * Regression test for the TOCTOU race in AuthController#register: two concurrent
     * registrations with the same username can both pass the existsByUsername check
     * before either saves, so the second save throws DataIntegrityViolationException
     * when it hits the database's unique constraint. This verifies the safety-net
     * handler maps that exception to 409 Conflict instead of an unhandled 500.
     */
    @Test
    void handleDataIntegrityViolation_returnsConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        DataIntegrityViolationException ex = new DataIntegrityViolationException("unique constraint violation");

        ResponseEntity<ApiError> response = handler.handleDataIntegrityViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getBody().message()).isEqualTo("Ya existe un usuario con ese nombre");
    }
}
