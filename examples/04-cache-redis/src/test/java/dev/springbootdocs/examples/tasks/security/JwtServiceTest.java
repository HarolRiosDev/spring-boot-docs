package dev.springbootdocs.examples.tasks.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.springbootdocs.examples.tasks.model.Role;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef0123456789";

    @Test
    void generateAndValidate_roundTrip() {
        JwtService jwtService = new JwtService(SECRET, 60_000);

        String token = jwtService.generateToken("alice", Role.USER);

        assertTrue(jwtService.isValid(token));
        assertEquals("alice", jwtService.extractUsername(token));
    }

    @Test
    void isValid_returnsFalse_whenTokenExpired() {
        JwtService jwtService = new JwtService(SECRET, -1_000);

        String token = jwtService.generateToken("alice", Role.USER);

        assertFalse(jwtService.isValid(token));
    }

    @Test
    void isValid_returnsFalse_whenSignatureTampered() {
        JwtService jwtService = new JwtService(SECRET, 60_000);
        String token = jwtService.generateToken("alice", Role.USER);
        // Se altera el penúltimo carácter, no el último. Una firma HS256 ocupa 32 bytes,
        // que en base64url son 43 caracteres, y solo el ÚLTIMO lleva bits de relleno sin
        // usar (2 de sus 6): cambiarlo puede, para ~6% de las firmas posibles, dar otro
        // carácter que decodifica a los mismos bytes, y el token "alterado" seguiría
        // siendo válido. Todos los demás caracteres codifican 6 bits completos sin esa
        // ambigüedad, así que alterar cualquiera de ellos siempre cambia la firma.
        int tamperIndex = token.length() - 2;
        char original = token.charAt(tamperIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);

        assertFalse(jwtService.isValid(tampered));
    }
}
