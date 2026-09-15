package dev.springbootdocs.examples.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        String tampered = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'a' ? 'b' : 'a');

        assertFalse(jwtService.isValid(tampered));
    }
}
