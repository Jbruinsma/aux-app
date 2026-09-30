package com.aux_app.controller;

import com.aux_app.dto.users.*;
import com.aux_app.repository.ProfileDetailsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.entity.ProfileDetailsEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import com.aux_app.repository.UserRepository;
import com.aux_app.services.OnboardingService;
import com.aux_app.services.UploadService;
import com.aux_app.services.UsernameService;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository users;
    private final ProfileDetailsRepository profileDetails;
    private final UploadService uploads;
    private final OnboardingService onboarding;
    private final UsernameService usernames;

    public UserController(
            UserRepository users,
            ProfileDetailsRepository profileDetails,
            UploadService uploads,
            OnboardingService onboarding,
            UsernameService usernames
    ) {
        this.users = users;
        this.profileDetails = profileDetails;
        this.uploads = uploads;
        this.onboarding = onboarding;
        this.usernames = usernames;
    }

    @GetMapping("/check-username/{username}")
    @Operation(
            summary = "Check if a username is taken",
            description = "Public. `exists` is true when an account already has this username, ignoring case (`MO` is taken if `mo` exists), or when the username is reserved (e.g. `admin`, `settings`). Does not check the username format."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    public UserExistance checkUsernameExists(@PathVariable String username) {
        return new UserExistance(usernames.isTaken(username));
    }

    @GetMapping("/onboarding/status")
    @Operation(
            summary = "Get the current onboarding step",
            description = "Returns the step the user must complete next. `DONE` means onboarding is finished."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    public OnboardingStep getOnboardingStatus(@CurrentUser UserEntity user) {
        return onboarding.currentStep(user);
    }

    @PostMapping("/onboarding/step")
    @Operation(
            summary = "Complete an onboarding step",
            description = """
                    Steps must be completed in order (USERNAME, PFP). Send the step the user is currently on.
                    - `step=USERNAME`: also send form field `username` (3-16 chars: letters, numbers, underscore). Kept as typed, but
                      unique ignoring case: `MO` is taken if `mo` exists.
                    - `step=PFP`: also send multipart field `file` (same rules as PUT /me/profile-picture).
                    Returns the updated user; `onboardingStep` is the next step, or `DONE` when finished.
                    """
    )
    @ApiResponse(responseCode = "200", description = "Step accepted; body is the updated user")
    @ApiResponse(responseCode = "400", description = "Missing or unknown `step`, wrong step, bad username, or unreadable or too small image (codes REQUEST_FAILED, WRONG_ONBOARDING_STEP, INVALID_USERNAME, INAPPROPRIATE_USERNAME, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "409", description = "Username taken (code USERNAME_TAKEN)")
    @ApiResponse(responseCode = "413", description = "Image over 5MB or 25 megapixels (code IMAGE_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Image is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
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

    @GetMapping("/settings")
    @Operation(
            summary = "Get the caller's settings",
            description = """
                    Everything the settings page needs in one call: `email`, `username`, picture and banner URLs,
                    and `profileDetails`. Every `profileDetails` field is null until the user fills it in.
                    """
    )
    @ApiResponse(responseCode = "200", description = "OK")
    public UserSettings retrieveSettings(
            @CurrentUser UserEntity user
    ) {
        ProfileDetailsEntity saved = this.profileDetails.findByUserId(user.getUserId());
        ProfileDetails details = saved == null
                ? new ProfileDetails(null, null, null, null)
                : new ProfileDetails(saved.getDisplayName(), saved.getCountry(), saved.getWebsite(), saved.getAbout());

        return new UserSettings(
                user.getEmail(),
                user.getUsername(),
                user.getProfilePictureUrl(),
                user.getBannerUrl(),
                details
        );
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
    @ApiResponse(responseCode = "400", description = "Missing, unreadable or too small image (codes INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")

    // TODO: no rate limit yet; each call costs a decode + encode + R2 write
    public ProfilePictureUpdate updateProfilePicture(
            @CurrentUser UserEntity user,
            @RequestParam("file") MultipartFile file
    ) {
        return new ProfilePictureUpdate(uploads.replaceProfilePicture(user, file));
    }

    @PutMapping(value = "/me/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Replace the caller's banner image",
            description = """
                    Multipart field `file`: the ORIGINAL JPEG or PNG (do not crop on the frontend), at most 5MB and
                    25 megapixels, at least 600x200. Plus `cropX`, `cropY`, `cropWidth`, `cropHeight`: the 3:1 area
                    the user framed, in pixels of the original image as displayed (after EXIF rotation).
                    The server crops to that area, resizes down to at most 1500x500 (never upscales), and stores it
                    as WebP. Returns the new public URL.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Missing, unreadable or too small image, or missing or bad crop (codes INVALID_IMAGE, IMAGE_TOO_SMALL, INVALID_CROP, REQUEST_FAILED)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public BannerUpdate updateBanner(
            @CurrentUser UserEntity user,
            @RequestParam("file") MultipartFile file,
            @RequestParam int cropX,
            @RequestParam int cropY,
            @RequestParam int cropWidth,
            @RequestParam int cropHeight
    ) {
        return new BannerUpdate(uploads.replaceBanner(user, file, cropX, cropY, cropWidth, cropHeight));
    }

    @PutMapping("/me/profile-details")
    @Operation(
            summary = "Replace the caller's profile details",
            description = """
                    Full replace. Every field is optional: a missing, null, empty or whitespace-only value clears it.
                    Strings are trimmed. `website` must be an http(s) URL. Returns the saved details.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "A field is too long or too short, `website` is not an http(s) URL, or `country` is unknown (codes INVALID_FIELD, MALFORMED_BODY)")
    public ProfileDetails updateProfileDetails(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ProfileDetailsUpdate update
    ) {
        Optional<ProfileDetailsEntity> existing = profileDetails.findById(user.getUserId());

        ProfileDetailsEntity details;
        details = existing.orElseGet(() -> new ProfileDetailsEntity(user.getUserId()));

        details.setDisplayName(update.displayName());
        details.setCountry(update.country());
        details.setWebsite(update.website());
        details.setAbout(update.about());
        profileDetails.save(details);

        return new ProfileDetails(update.displayName(), update.country(), update.website(), update.about());
    };

    @PutMapping("/me/username")
    @Operation(
            summary = "Change the caller's username",
            description = """
                    JSON body: `username`. Same rules as the onboarding `USERNAME` step: 3-16 letters, numbers or
                    underscores, no blocked words, unique ignoring case, and not a reserved name.
                    A change of case alone of your own name (`mo` -> `Mo`) is allowed.
                    Returns the updated user.
                    """)
    @ApiResponse(responseCode = "200", description = "Username changed; body is the updated user")
    @ApiResponse(responseCode = "400", description = "Bad format, blocked word, or already your exact username (codes INVALID_USERNAME, INAPPROPRIATE_USERNAME, IDENTICAL_USERNAME, MALFORMED_BODY)")
    @ApiResponse(responseCode = "409", description = "Taken by another account, or reserved (code USERNAME_TAKEN)")
    public UserSummary updateUsername(
            @CurrentUser UserEntity user,
            @Valid @RequestBody UsernameUpdateDetails update
    ) {
        String newUsername = update.username();

        if (newUsername != null && newUsername.equals(user.getUsername())) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "IDENTICAL_USERNAME",
                    "Username already in use",
                    "username"
            );
        }

        usernames.change(user, newUsername);
        return UserSummary.of(users.save(user));
    }

    // TODO POST /{username}/update-password              json: old_password, new_password
    // TODO GET  /{username}/get-last-playback
    // TODO POST /{username}/update-last-playback         json: playback data
    // TODO POST /{username}/add-public-playlist          json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-public-playlist       json: playlist_uuid, playlist_owner
    // TODO POST /{username}/remove-added-to-playlist     json: playlist_uuid, playlist_owner
}
