package com.aux_app.services;

import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class UsernameService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Set<String> RESERVED = loadReserved();

    private final UserRepository users;

    public UsernameService(UserRepository users) {
        this.users = users;
    }

    private static Set<String> loadReserved() {
        try (InputStream in = UsernameService.class.getResourceAsStream("/reserved-usernames.txt")) {
            assert in != null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(l -> l.strip().toLowerCase(Locale.ROOT))
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isTaken(String username) {
        return RESERVED.contains(username.toLowerCase(Locale.ROOT)) || users.existsByUsernameIgnoreCase(username);
    }

    // Validates and sets the username on `user`. Caller saves.
    public void change(UserEntity user, String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME",
                    "Username must be 3-16 characters: letters, numbers, underscore", "username");
        }
        // Digits count as word breaks so "fuck99" is caught; usernames are rejected, not masked
        if (ProfanityFilter.contains(username.replaceAll("\\d", " "))) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INAPPROPRIATE_USERNAME",
                    "Username contains a blocked word", "username");
        }
        // A case-only change of your own name (mo -> Mo) is allowed
        boolean ownName = username.equalsIgnoreCase(user.getUsername());
        if (ownName ? RESERVED.contains(username.toLowerCase(Locale.ROOT)) : isTaken(username)) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN",
                    "Username already exists", "username");
        }
        user.setUsername(username);
    }
}
