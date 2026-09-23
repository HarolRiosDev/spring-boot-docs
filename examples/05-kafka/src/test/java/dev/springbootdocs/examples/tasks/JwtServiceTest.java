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
        // Tamper the second-to-last character, not the last one. An HS256 signature is
        // 32 bytes, which base64url-encodes to 43 characters where only the FINAL
        // character carries unused padding bits (2 of its 6) — swapping it can, for
        // ~6% of possible signatures, land on a different character that decodes to
        // the same bytes, leaving the "tampered" token still valid. Every other
        // character encodes a full 6 bits with no such ambiguity, so tampering one of
        // those always changes the decoded signature.
        int tamperIndex = token.length() - 2;
        char original = token.charAt(tamperIndex);
        char replacement = original == 'a' ? 'b' : 'a';
        String tampered = token.substring(0, tamperIndex) + replacement + token.substring(tamperIndex + 1);

        assertFalse(jwtService.isValid(tampered));
    }
}
