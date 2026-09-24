package dev.springbootdocs.examples.tasks.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    /**
     * El manejador es una red de seguridad global para CUALQUIER violación de una
     * restricción de la base de datos que se escape de la validación de la aplicación
     * (por ejemplo, la carrera en AuthServiceImpl#register: dos registros simultáneos con
     * el mismo username pueden pasar ambos la comprobación existsByUsername antes de que
     * ninguno guarde). Comprueba que DataIntegrityViolationException se traduce a un 409
     * Conflict con un mensaje genérico, en vez de un 500 sin manejar o un mensaje propio
     * de un único caso.
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
