package com.aux_app.controller;

import com.aux_app.dto.playlist.LibraryPlaylist;
import com.aux_app.dto.users.*;
import com.aux_app.repository.ProfileDetailsRepository;
import com.aux_app.repository.UserPlayPreferencesRepository;
import com.aux_app.services.LiveEventService;
import com.aux_app.services.PlaybackEventService;
import com.aux_app.services.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OnboardingUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.entity.ProfileDetailsEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.entity.UserPlayPreferencesEntity;
import com.aux_app.error.AuxException;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
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
    private final UserPlayPreferencesRepository playPreferences;
    private final UploadService uploads;
    private final OnboardingService onboarding;
    private final UsernameService usernames;
    private final PlaybackEventService playbackEventService;
    private final SessionService sessions;
    private final LiveEventService liveEvents;

    public UserController(
            UserRepository users,
            ProfileDetailsRepository profileDetails,
            UserPlayPreferencesRepository playPreferences,
            UploadService uploads,
            OnboardingService onboarding,
            UsernameService usernames,
            PlaybackEventService playbackEventService,
            SessionService sessions,
            LiveEventService liveEvents
    ) {
        this.users = users;
        this.profileDetails = profileDetails;
        this.playPreferences = playPreferences;
        this.uploads = uploads;
        this.onboarding = onboarding;
        this.usernames = usernames;
        this.playbackEventService = playbackEventService;
        this.sessions = sessions;
        this.liveEvents = liveEvents;
    }

    @GetMapping("/check-username/{username}")
    @Operation(
            summary = "Check if a username is taken",
            description = "Public. `exists` is true when an account already has this username, ignoring case (`MO` is taken if `mo` exists), or when the username is reserved (e.g. `admin`, `settings`). Does not check the username format."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    public UserExistance checkUsernameExists(@Parameter(description = "Username to check, case-insensitive", example = "justin") @PathVariable String username) {
        return new UserExistance(usernames.isTaken(username));
    }

    @GetMapping("/onboarding/status")
    @Operation(
            summary = "Get the current onboarding step",
            description = "Returns the step the user must complete next. `DONE` means onboarding is finished."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    public OnboardingStep getOnboardingStatus(@OnboardingUser UserEntity user) {
        return onboarding.currentStep(user);
    }

    @PostMapping(value = "/onboarding/step", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    @ApiResponse(responseCode = "400", description = "Missing or unknown `step`, wrong step, bad username, or unreadable or too small image (codes REQUEST_FAILED, WRONG_ONBOARDING_STEP, INVALID_ONBOARDING_STEP, INVALID_USERNAME, INAPPROPRIATE_USERNAME, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "409", description = "Username taken (code USERNAME_TAKEN)")
    @ApiResponse(responseCode = "413", description = "Image over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Image is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public UserSummary completeOnboardingStep(
            @OnboardingUser UserEntity user,
            @Parameter(description = "The step the user is currently on") @RequestParam OnboardingStep step,
            @Parameter(description = "Picture for the PFP step: JPEG or PNG; ignored for USERNAME") @RequestParam(value = "file", required = false) MultipartFile file,
            @Parameter(description = "Username for the USERNAME step; ignored for PFP", example = "justin") @RequestParam(required = false) String username
    ) {
        return UserSummary.of(onboarding.completeStep(user, step, file, username));
    }

    @GetMapping("/recent-listening")
    @Operation(
            summary = "Get the caller's recently played music pieces",
            description = """
                    Up to 4 distinct pieces from plays recorded with `POST /api/music-pieces/{musicPieceId}/plays`,
                    most recently played first. A piece played several times appears once, with the `playlistId` of its
                    latest play. Same rules as `GET /me/last-playback`: plays of pieces made private since are skipped,
                    and `playlistId` is null when the piece wasn't played from a playlist or the caller can no longer see it.
                    `recents` is empty when the caller has played nothing they can still play.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public RecentListeningResponse getRecentListening(@CurrentUser UserEntity user) {
        return new RecentListeningResponse(playbackEventService.retrieveRecentPlaybacks(user.getUserId()));
    }

    @GetMapping("/top-artists")
    @Operation(
            summary = "Get the caller's most played artists",
            description = """
                    Up to 4 artists ranked by how many plays the caller has recorded of their pieces, all time.
                    Ties go to the artist played most recently. `artists` is empty when the caller has played nothing.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public TopArtistsResponse getTopArtists(@CurrentUser UserEntity user) {
        return new TopArtistsResponse(users.findTopArtists(user.getUserId()));
    }

    @GetMapping("/library")
    @Operation(
            summary = "Get the caller's playlist library",
            description = """
                    Playlists the caller owns (public and private), playlists they're an accepted listener or editor of
                    (public and private), plus playlists they saved from others (public only).
                    `isSaved` is true for saved playlists. Saved ones come first, most recently saved first; the rest follow, newest created first.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public List<LibraryPlaylist> retrieveLibrary(@CurrentUser UserEntity user) {
        return users.findLibrary(user.getUserId());
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
            @Parameter(description = "Username of the profile owner", example = "justin") @PathVariable String username,
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
    @ApiResponse(responseCode = "400", description = "Missing `file` part (code REQUEST_FAILED), or empty, unreadable or too small image (codes INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public ProfilePictureUpdate updateProfilePicture(
            @CurrentUser UserEntity user,
            @Parameter(description = "Image file, JPEG or PNG") @RequestParam("file") MultipartFile file
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
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public BannerUpdate updateBanner(
            @CurrentUser UserEntity user,
            @Parameter(description = "Image file, JPEG or PNG") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Left edge of the crop area, in pixels of the original image") @RequestParam int cropX,
            @Parameter(description = "Top edge of the crop area, in pixels of the original image") @RequestParam int cropY,
            @Parameter(description = "Width of the crop area, in pixels") @RequestParam int cropWidth,
            @Parameter(description = "Height of the crop area, in pixels") @RequestParam int cropHeight
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

    @GetMapping("/me/last-playback")
    @Operation(
            summary = "Get the caller's last played music piece and play preferences",
            description = """
                    Everything the player needs on load. `musicPiece` is the most recent play recorded with
                    `POST /api/music-pieces/{musicPieceId}/plays`; plays of pieces made private since are skipped, and
                    it is null when the caller has never played anything they can still play. `playlistId` is null
                    when the piece wasn't played from a playlist, or the caller can no longer see that playlist.
                    `playPreferences` is shuffle and repeat, kept across devices; the defaults (shuffle off, repeat
                    `OFF`) until `PUT /me/play-preferences` is called. Volume is not stored; it stays on the device.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public LastPlaybackResponse getLastPlayback(
            @CurrentUser UserEntity user
    ) {
        LastPlayback lastPlayback = playbackEventService.retrieveMostRecentPlayback(user.getUserId());
        UserPlayPreferencesEntity preferences = findPlayPreferences(user.getUserId());

        return new LastPlaybackResponse(
                lastPlayback == null ? null : lastPlayback.musicPiece(),
                lastPlayback == null ? null : lastPlayback.playlistId(),
                new PlayPreferences(preferences.isShuffleEnabled(), preferences.getRepeatPreference())
        );
    }

    @PutMapping("/me/play-preferences")
    @Operation(
            summary = "Replace the caller's play preferences",
            description = """
                    Full replace. JSON body: `shuffleEnabled`, `repeatPreference` (`PLAYLIST`, `PIECE` or `OFF`).
                    Both are required. Returns the saved preferences. Read them back with `GET /me/last-playback`.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "A field is missing or `repeatPreference` is unknown (codes INVALID_FIELD, MALFORMED_BODY)")
    public PlayPreferences updatePlayPreferences(
            @CurrentUser UserEntity user,
            @Valid @RequestBody PlayPreferences update
    ) {
        UserPlayPreferencesEntity preferences = findPlayPreferences(user.getUserId());

        preferences.setShuffleEnabled(update.shuffleEnabled());
        preferences.setRepeatPreference(update.repeatPreference());
        playPreferences.save(preferences);

        return update;
    }

    // No row until the first PUT: a new entity carries the defaults, and save() inserts it
    private UserPlayPreferencesEntity findPlayPreferences(String userId) {
        return playPreferences.findById(userId).orElseGet(() -> new UserPlayPreferencesEntity(userId));
    }

    @GetMapping(value = "/me/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream the caller's live updates",
            description = """
                    Server-sent events. Read it with `fetch` and the usual `Authorization: Bearer` header, never with
                    `EventSource`: it can't send headers, and the token must not go in the URL.

                    **On connect** the server sends every event below once, with the current data. **After that** it
                    sends an event again, whole, each time its data changes; replace what you have, there are no diffs.
                    Each `data` is one line of JSON, the same body the listed endpoint returns:

                    | `event` | `data` | Same as |
                    |---|---|---|
                    | `invites` | `PendingPlaylistInvites` | `GET /api/playlists/invites` |
                    | `recents` | `RecentListeningResponse` | `GET /api/users/recent-listening` |
                    | `top-artists` | `TopArtistsResponse` | `GET /api/users/top-artists` |
                    | `library` | array of `LibraryPlaylist` | `GET /api/users/library` |
                    | `uploads` | `UploadedMusicPiecesResponse` | `GET /api/music-pieces` |
                    | `notifications` | `NotificationPage`, first page | `GET /api/notifications` |

                    **Wire format.** Each event is an `event:` line, a `data:` line, then a blank line. There is no space
                    after the colon (the SSE format allows one, so strip one if present). No `id:` or `retry:` lines.
                    A `:ping` comment line arrives every 25 seconds so proxies keep the connection open; ignore it.

                    **End of stream.** The server ends the response normally when the token expires (tokens last
                    1 hour). Reconnect with a current token, backing off on repeated failures; the first events after
                    a reconnect are a full snapshot, so nothing is missed. A 401 on reconnect means the session ended.

                    **Errors** come back as JSON before the stream starts, whatever `Accept` header was sent.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "Stream open. Stays open until the token expires or the client disconnects",
            headers = @Header(name = "X-Accel-Buffering", description = "Always `no`, so nginx-style proxies pass events through unbuffered", schema = @Schema(type = "string")),
            content = @Content(
                    mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                    schema = @Schema(type = "string", description = "A stream of events in the wire format above"),
                    examples = @ExampleObject(name = "Snapshot, then a ping and an update", value = """
                            event:invites
                            data:{"invites":[]}

                            event:recents
                            data:{"recents":[]}

                            event:top-artists
                            data:{"artists":[]}

                            event:library
                            data:[{"playlistId":"p_8F1eB5","playlistName":"Alice's Mix","playlistCoverUrl":null,"ownerUsername":"alice","totalPieces":3,"isSaved":false}]

                            event:uploads
                            data:{"uploadedPiecesCount":0,"uploadedMusicPieces":[]}

                            event:notifications
                            data:{"notifications":[],"unreadCount":0,"nextCursor":null}

                            :ping

                            event:library
                            data:[{"playlistId":"p_8F1eB5","playlistName":"Road Trip","playlistCoverUrl":null,"ownerUsername":"alice","totalPieces":3,"isSaved":false}]

                            """)))
    public SseEmitter streamEvents(
            @CurrentUser UserEntity user,
            // Already covered by the bearer security scheme; hidden so it isn't listed twice
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            HttpServletResponse response
    ) {
        // CurrentUserResolver has already checked this token, so this can't throw
        Instant expiresAt = sessions.expiresAt(authorization.substring("Bearer ".length()));
        // Stops nginx-style proxies from holding events back in a buffer
        response.setHeader("X-Accel-Buffering", "no");
        return liveEvents.connect(user.getUserId(), expiresAt);
    }

}
