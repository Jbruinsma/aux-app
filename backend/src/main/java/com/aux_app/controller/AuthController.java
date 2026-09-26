package com.aux_app.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.dto.auth.LoginCredentials;
import com.aux_app.services.SessionService;
import com.aux_app.dto.auth.SessionToken;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.aux_app.dto.auth.RegistrationCredentials;
import com.aux_app.dto.auth.AuthResponse;
import com.aux_app.dto.users.UserSummary;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;

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

        String registrationUsername = credentials.username();
        String registrationEmail = credentials.email();

        if (users.existsByEmail(registrationEmail)) {
            throw new AuxException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "Email is already associated with an account", "email");
        }

        if (users.existsByUsername(registrationUsername)) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists", "username");
        }

        String newUserIdentifier = UUID.randomUUID().toString();

        try {

            String hash = bcrypt.encode(credentials.password());
            UserEntity user = users.save(
                    new UserEntity(
                            newUserIdentifier,
                            registrationUsername,
                            registrationEmail,
                            hash
                    )
            );

            SessionToken session = sessions.issueSymmetricToken(newUserIdentifier, Map.of());
            return new AuthResponse(session.token(), session.expiresAt(), UserSummary.of(user));

        } catch (Exception accountCreationException) {
            throw new AuxException(
                    HttpStatus.CONFLICT,
                    "USERNAME_TAKEN",
                    "There has been an error creating your account. Please try again.",
                    "username"
            );
        }
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
