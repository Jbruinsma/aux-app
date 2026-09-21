package com.aux.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aux.dto.auth.RegistrationCredentials;
import com.aux.dto.base.Message;
import com.aux.entity.UserEntity;
import com.aux.error.AuxException;
import com.aux.repository.UserRepository;

// Port of backend/routes/auth.py
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public AuthController(UserRepository users) {
        this.users = users;
    }

    @PostMapping("/register")
    public Message register(@Valid @RequestBody RegistrationCredentials credentials) {
        if (users.existsByUsername(credentials.username())) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists", "username");
        }
        // ponytail: check-then-insert race; a concurrent duplicate hits the UNIQUE constraint and returns 500
        byte[] hash = bcrypt.encode(credentials.password()).getBytes(StandardCharsets.UTF_8);
        users.save(new UserEntity(UUID.randomUUID().toString(), credentials.username(), hash));
        return new Message("Registration successful");
    }

    // TODO POST /login      json: username, password
}
