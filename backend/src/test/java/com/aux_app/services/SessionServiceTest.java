package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aux_app.dto.auth.SessionToken;
import io.jsonwebtoken.JwtException;

class SessionServiceTest {

    private final SessionService sessions = new SessionService("test-secret-that-is-at-least-32-bytes-long");

    @Test
    void expiresAtMatchesIssuedExpiry() {
        SessionToken issued = sessions.issueSymmetricToken("u1", Map.of());

        // 'exp' is stored in whole seconds
        assertEquals(issued.expiresAt().truncatedTo(ChronoUnit.SECONDS), sessions.expiresAt(issued.token()));
    }

    @Test
    void expiresAtRejectsForeignToken() {
        String foreign = new SessionService("another-secret-that-is-at-least-32-bytes").issueSymmetricToken("u1", Map.of()).token();

        assertThrows(JwtException.class, () -> sessions.expiresAt(foreign));
    }
}
