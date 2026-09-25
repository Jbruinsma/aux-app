package com.aux.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux.dto.auth.LoginCredentials;
import com.aux.services.SessionService;
import com.aux.dto.auth.SessionToken;
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
    @ApiResponse(responseCode = "409", description = "Username already exists (code USERNAME_TAKEN)")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegistrationCredentials credentials) {
        // BCrypt only reads the first 72 bytes; @Size counts chars, and emoji are 4 bytes each
        if (credentials.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "password is too long", "password");
        }

        if (users.existsByUsername(credentials.username())) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists", "username");
        }

        // Check-then-insert race: a concurrent duplicate hits a UNIQUE index and AuxErrorHandler returns 409
        String hash = bcrypt.encode(credentials.password());
        UserEntity user = users.save(new UserEntity(UUID.randomUUID().toString(), credentials.username(), hash));

        SessionToken session = sessions.issueSymmetricToken(user.getUserId(), Map.of());
        return new AuthResponse(session.token(), session.expiresAt(), UserSummary.of(user));
    }

    @PostMapping("/login")
    @ApiResponse(responseCode = "401", description = "Wrong username or password (code INVALID_CREDENTIALS)")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginCredentials credentials) {
        UserEntity user = users.findByUsername(credentials.username());

        String hash = user != null ? user.getPasswordHash() : dummyHash;
        if (!bcrypt.matches(credentials.password(), hash) || user == null) {
            throw new AuxException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
        }

        SessionToken session = sessions.issueSymmetricToken(user.getUserId(), Map.of());
        return new AuthResponse(session.token(), session.expiresAt(), UserSummary.of(user));
    }
}
