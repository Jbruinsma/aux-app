package com.aux_app.services;

import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OnboardingService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Set<String> RESERVED = loadReserved();

    private final UserRepository users;
    private final UploadService uploads;

    public OnboardingService(
            UserRepository users,
            UploadService uploads
    ) {
        this.users = users;
        this.uploads = uploads;
    }

    public static boolean isReserved(String username) {
        return RESERVED.contains(username.toLowerCase(Locale.ROOT));
    }

    private static Set<String> loadReserved() {
        try (InputStream in = OnboardingService.class.getResourceAsStream("/reserved-usernames.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(l -> l.strip().toLowerCase(Locale.ROOT))
                    .filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // The step the user must do next. A brand-new user has null stored, which means the first step.
    public OnboardingStep currentStep(UserEntity user) {
        OnboardingStep stored = user.getOnboardingStep();
        return stored == null ? OnboardingStep.USERNAME : stored;
    }

    // Accepts only the step the user is on, does that step's work, saves the next step, and returns the updated user.
    // USERNAME needs `username`; PFP needs `file`. The other argument is ignored.
    public UserEntity completeStep(
            UserEntity user,
            OnboardingStep step,
            MultipartFile file,
            String username
    ) {
        OnboardingStep expected = currentStep(user);

        if (step != expected || expected == OnboardingStep.DONE) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "WRONG_ONBOARDING_STEP",
                    "Expected step " + expected
            );
        }

        switch (step) {
            case USERNAME -> setUsername(user, username);
            case PFP -> {
                if (file == null) {
                    throw new AuxException(
                            HttpStatus.BAD_REQUEST,
                            "INVALID_IMAGE",
                            "No file uploaded",
                            "file"
                    );
                }
                uploads.replaceProfilePicture(user, file);
            }
            default -> throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "INVALIDE_ONBOARDING_STEP",
                    "Unhandled onboarding step: " + step,
                    String.valueOf(step)
            );
        }

        user.setOnboardingStep(step.next());
        return users.save(user);
    }

    private void setUsername(UserEntity user, String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_USERNAME",
                    "Username must be 3-16 characters: letters, numbers, underscore", "username");
        }
        // Digits count as word breaks so "fuck99" is caught; usernames are rejected, not masked
        if (ProfanityFilter.contains(username.replaceAll("\\d", " "))) {
            throw new AuxException(HttpStatus.BAD_REQUEST, "INAPPROPRIATE_USERNAME",
                    "Username contains a blocked word", "username");
        }
        // Reserved names look taken, same as check-username reports them
        if (isReserved(username) || users.existsByUsernameIgnoreCase(username)) {
            throw new AuxException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "Username already exists", "username");
        }
        user.setUsername(username);
    }
}
