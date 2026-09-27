package com.aux_app.controller;

import com.aux_app.dto.users.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.aux_app.repository.UserRepository;
import com.aux_app.services.OnboardingService;
import com.aux_app.services.UploadService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final UploadService uploads;
    private final OnboardingService onboarding;

    public UserController(
            UserRepository users,
            UploadService uploads,
            OnboardingService onboarding
    ) {
        this.users = users;
        this.uploads = uploads;
        this.onboarding = onboarding;
    }

    @GetMapping("/check-username/{username}")
    public UserExistance checkUsernameExists(@PathVariable String username) {
        return new UserExistance(users.existsByUsername(username));
    }

    @GetMapping("/onboarding/status")
    @Operation(
            summary = "Get the current onboarding step",
            description = "Returns the step the user must complete next. `DONE` means onboarding is finished."
    )
    public OnboardingStep getOnboardingStatus(@CurrentUser UserEntity user) {
        return onboarding.currentStep(user);
    }

    @PostMapping("/onboarding/step")
    @Operation(
            summary = "Complete an onboarding step",
            description = """
                    Steps must be completed in order (USERNAME, PFP). Send the step the user is currently on.
                    - `step=USERNAME`: also send form field `username` (3-16 chars: letters, numbers, underscore).
                    - `step=PFP`: also send multipart field `file` (same rules as PUT /me/profile-picture).
                    Returns the updated user; `onboardingStep` is the next step, or `DONE` when finished.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Step accepted; body is the updated user")
    @ApiResponse(responseCode = "400", description = "Wrong step, bad username or bad image (codes WRONG_ONBOARDING_STEP, INVALID_USERNAME, INVALID_IMAGE)")
    @ApiResponse(responseCode = "409", description = "Username taken (code USERNAME_TAKEN)")
    public UserSummary completeOnboardingStep(
            @CurrentUser UserEntity user,
            @RequestParam OnboardingStep step,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(required = false) String username
    ) {
        return UserSummary.of(onboarding.completeStep(user, step, file, username));
    }

    @GetMapping("/profile/{username}")
    @Operation(
            summary = "Get a user's profile",
            description = """
                    This will be a public endpoint. The playlists returned depend on who is asking:
                    - If the person who makes the request is the profile owner: returns public AND private playlists.
                    - If anyone else makes the request, or there is no token: returns public playlists only.
                    `isMe` in the response is true when the caller is the profile owner.
                    Maybe add a `public view` switch in the frontend so the owner can toggle between views (do not make separate API calls).
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "User not found (code USER_NOT_FOUND)")
    public UserProfile retrieveProfile(
            @PathVariable String username,
            @OptionalCurrentUser UserEntity user
    ) {
        String currentUserId = (user != null) ? user.getUserId() : null;

        UserProfile userProfile = users.findProfile(
                username,
                currentUserId
        );

        if (userProfile == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found",
                    "username"
            );
        }

        return userProfile;
    }

    @PutMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Replace the caller's profile picture",
            description = """
                    Multipart field `file`: a JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256.
                    Send the original image; do NOT crop it on the frontend. The server applies EXIF rotation,
                    center-crops to a square, resizes to at most 1024x1024, and stores it as WebP.
                    Returns the new public URL.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Unreadable or too small image (code INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "401", description = "Missing or invalid token (code INVALID_TOKEN)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")

    // TODO: no rate limit yet; each call costs a decode + encode + R2 write
    public ProfilePictureUpdate updateProfilePicture(
            @CurrentUser UserEntity user,
            @RequestParam("file") MultipartFile file
    ) {
        return new ProfilePictureUpdate(uploads.replaceProfilePicture(user, file));
    }

    // TODO POST /{username}/update-username/{new_username}
    // TODO POST /{username}/update-password              json: old_password, new_password
    // TODO GET  /{username}/get-last-playback
    // TODO POST /{username}/update-last-playback         json: playback data
    // TODO POST /{username}/add-public-playlist          json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-public-playlist       json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-added-to-playlist     json: playlist_uuid, playlist_owner
}
