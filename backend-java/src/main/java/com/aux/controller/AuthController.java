package com.aux.controller;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.aux.dto.auth.LoginCredentials;
import com.aux.services.SessionService;
import com.aux.services.SessionToken;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aux.dto.auth.RegistrationCredentials;
import com.aux.dto.auth.AuthResponse;
import com.aux.dto.users.UserSummary;
import com.aux.entity.UserEntity;
import com.aux.error.AuxException;
import com.aux.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final SessionService sessions;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final String dummyHash = bcrypt.encode("dummy-password");

    public AuthController(UserRepository users, SessionService sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegistrationCredentials credentials) {
        String email = credentials.email().trim().toLowerCase(Locale.ROOT);

        // BCrypt only reads the first 72 bytes; @Size counts chars, and emoji are 4 bytes each
        if (credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "password is too long", "password");
        }

        if (users.existsByUsername(credentials.username())) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists", "username");
        }

        if (users.existsByEmail(email)) {
            throw new AuxException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already registered", "email");
        }
        // Check-then-insert race: a concurrent duplicate hits a UNIQUE index and AuxErrorHandler returns 409
        byte[] hash = bcrypt.encode(credentials.password()).getBytes(StandardCharsets.UTF_8);
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(), credentials.username(), email, hash));

        SessionToken session = sessions.issueSymmetricToken(user.getId(), Map.of());
        return new AuthResponse(session.token(), session.expiresAt(), UserSummary.of(user));
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginCredentials credentials) {
        UserEntity user = users.findByUsername(credentials.username());

        String hash = user != null ? new String(user.getPasswordHash(), StandardCharsets.UTF_8) : dummyHash;
        if (!bcrypt.matches(credentials.password(), hash) || user == null) {
            throw new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }

        SessionToken session = sessions.issueSymmetricToken(user.getId(), Map.of());
        return new AuthResponse(session.token(), session.expiresAt(), UserSummary.of(user));
    }
}
