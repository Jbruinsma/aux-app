package com.aux_app.services;

import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OnboardingService {

    private final UserRepository users;
    private final UploadService uploads;
    private final UsernameService usernames;

    public OnboardingService(
            UserRepository users,
            UploadService uploads,
            UsernameService usernames
    ) {
        this.users = users;
        this.uploads = uploads;
        this.usernames = usernames;
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
            case USERNAME -> usernames.change(user, username);
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
}
