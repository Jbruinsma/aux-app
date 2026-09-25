package com.aux.services;
import com.aux.dto.auth.SessionToken;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final SecretKey key;

    // aux.jwt-secret comes from AUX_JWT_SECRET in .env (see application.properties)
    public SessionService(@Value("${aux.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public SessionToken issueSymmetricToken(String userId, Map<String, Object> customClaims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(userId)                                // 'sub' claim
                .issuer("aux")                                    // 'iss' claim
                .issuedAt(Date.from(now))                         // 'iat' claim
                .expiration(Date.from(expiresAt))                  // 'exp' claim (1 hour expiration)
                .claims(customClaims)                             // Inject custom roles/permissions
                .signWith(key)                                    // Cryptographically sign the token
                .compact();                                       // Serialize to a compact, URL-safe string

        return new SessionToken(token, expiresAt);
    }

    // Returns the user id ('sub'). Throws JwtException on a bad signature, wrong issuer, or expired token
    public String verify(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer("aux").build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

}
