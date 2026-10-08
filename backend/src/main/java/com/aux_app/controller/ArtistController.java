package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.dto.artist.ArtistCreationDetails;
import com.aux_app.dto.artist.ArtistDetails;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.BannerUpdate;
import com.aux_app.dto.users.ProfilePictureUpdate;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.entity.UserFavoriteArtistEntity;
import com.aux_app.entity.UserFavoriteArtistId;
import com.aux_app.error.AuxException;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.UserFavoriteArtistRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/artists")
public class ArtistController {

    private final UserRepository users;
    private final ArtistRepository artists;
    private final UserFavoriteArtistRepository favorites;
    private final UploadService uploads;

    public ArtistController(
            UserRepository users,
            ArtistRepository artists,
            UserFavoriteArtistRepository favorites,
            UploadService uploads
    ) {
        this.users = users;
        this.artists = artists;
        this.favorites = favorites;
        this.uploads = uploads;
    }

    @GetMapping
    @Operation(
            summary = "Search artists by name",
            description = """
                    `q` is matched case-insensitively anywhere in the name. Returns at most 20 artists,
                    sorted by name. Use it for the upload form's artist picker.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public List<ArtistSummary> searchArtists(
            @CurrentUser UserEntity user,
            @Parameter(description = "Text to match anywhere in the artist name, case-insensitive", example = "tame") @RequestParam String q
    ) {
        String query = q.strip();

        if (query.isEmpty()) {
            return List.of();
        }

        return this.artists.findTop20ByArtistNameContainingIgnoreCaseOrderByArtistName(query).stream()
                .map(ArtistController::toSummary)
                .toList();
    }

    @PostMapping
    @Operation(
            summary = "Find or create an artist",
            description = """
                    JSON body: `artistName` (required), at most 100 chars, trimmed. If an artist with the same name
                    exists (case-insensitive), returns that artist instead of making a duplicate. The name can't be
                    changed later; the creator can set the pfp and banner.
                    """)
    @ApiResponse(responseCode = "200", description = "Artist found or created; body is the artist")
    @ApiResponse(responseCode = "400", description = "Missing or bad field (code INVALID_FIELD)")
    @ApiResponse(responseCode = "409", description = "Someone created the same artist at the same moment; retry (code CONFLICT)")
    public ArtistSummary createArtist(
            @CurrentUser UserEntity user,
            @Valid @RequestBody ArtistCreationDetails artistCreationDetails
    ) {
        String name = artistCreationDetails.artistName().strip();

        ArtistEntity artist = this.artists.findByArtistNameIgnoreCase(name);

        if (artist == null) {
            // A racing create hits uq_artists_name_nocase and AuxErrorHandler returns 409
            artist = this.artists.save(new ArtistEntity(UUID.randomUUID().toString(), name, user.getUserId()));
        }

        return toSummary(artist);
    }

    @GetMapping("/{artistId}")
    @Operation(
            summary = "Get an artist",
            description = """
                    Public. `isFavorite` is true when the caller has favorited the artist. `canEdit` is true when the
                    caller created the artist and may change its pfp and banner. Both are false with no token.
                    `artistPfpUrl` and `artistBannerUrl` are null until set.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    public ArtistDetails getArtist(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @OptionalCurrentUser UserEntity user
    ) {
        ArtistEntity artist = findArtist(artistId);
        String userId = (user != null) ? user.getUserId() : null;
        boolean isFavorite = userId != null
                && this.favorites.existsById(new UserFavoriteArtistId(userId, artist.getArtistId()));

        return new ArtistDetails(
                artist.getPublicId(),
                artist.getArtistName(),
                artist.getArtistPfpUrl(),
                artist.getArtistBannerUrl(),
                isFavorite,
                artist.isEditableBy(userId)
        );
    }

    @PutMapping(value = "/{artistId}/pfp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Replace an artist's profile picture",
            description = "Only the artist's creator. Multipart field `file`: same rules as the user profile picture. Returns the new public URL.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Missing `file` part (code REQUEST_FAILED), or empty, unreadable or too small image (codes INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "403", description = "Caller didn't create the artist (code NOT_ARTIST_CREATOR)")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public ProfilePictureUpdate updateArtistPfp(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @CurrentUser UserEntity user,
            @Parameter(description = "Image file, JPEG or PNG") @RequestParam("file") MultipartFile file
    ) {
        ArtistEntity artist = findEditableArtist(artistId, user);
        return new ProfilePictureUpdate(this.uploads.replaceArtistPfp(artist, file));
    }

    @PutMapping(value = "/{artistId}/banner", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Replace an artist's banner image",
            description = """
                    Only the artist's creator. Multipart fields `file`, `cropX`, `cropY`, `cropWidth`, `cropHeight`:
                    same rules as the user banner. Returns the new public URL.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Missing, unreadable or too small image, or missing or bad crop (codes INVALID_IMAGE, IMAGE_TOO_SMALL, INVALID_CROP, REQUEST_FAILED)")
    @ApiResponse(responseCode = "403", description = "Caller didn't create the artist (code NOT_ARTIST_CREATOR)")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public BannerUpdate updateArtistBanner(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @CurrentUser UserEntity user,
            @Parameter(description = "Image file, JPEG or PNG") @RequestParam("file") MultipartFile file,
            @Parameter(description = "Left edge of the crop area, in pixels of the original image") @RequestParam int cropX,
            @Parameter(description = "Top edge of the crop area, in pixels of the original image") @RequestParam int cropY,
            @Parameter(description = "Width of the crop area, in pixels") @RequestParam int cropWidth,
            @Parameter(description = "Height of the crop area, in pixels") @RequestParam int cropHeight
    ) {
        ArtistEntity artist = findEditableArtist(artistId, user);
        return new BannerUpdate(this.uploads.replaceArtistBanner(artist, file, cropX, cropY, cropWidth, cropHeight));
    }

    @PutMapping("/{artistId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Favorite an artist", description = "Does nothing if the caller already favorited the artist.")
    @ApiResponse(responseCode = "204", description = "Artist is now a favorite")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    public void favoriteArtist(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @CurrentUser UserEntity user
    ) {
        ArtistEntity artist = findArtist(artistId);
        UserFavoriteArtistId id = new UserFavoriteArtistId(user.getUserId(), artist.getArtistId());

        // Skip the save so a repeat call keeps the original created_at
        if (!this.favorites.existsById(id)) {
            this.favorites.save(new UserFavoriteArtistEntity(user.getUserId(), artist.getArtistId()));
        }
    }

    @DeleteMapping("/{artistId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unfavorite an artist", description = "Does nothing if the artist wasn't a favorite or doesn't exist.")
    @ApiResponse(responseCode = "204", description = "Artist is no longer a favorite")
    public void unfavoriteArtist(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @CurrentUser UserEntity user
    ) {
        ArtistEntity artist = this.artists.findByPublicId(artistId);
        if (artist != null) {
            this.favorites.deleteById(new UserFavoriteArtistId(user.getUserId(), artist.getArtistId()));
        }
    }

    @GetMapping("/favorites/{username}")
    @Operation(summary = "List a user's favorite artists", description = "Public. Newest favorite first.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "User not found (code USER_NOT_FOUND)")
    public List<ArtistSummary> getFavoriteArtists(@Parameter(description = "Username of the user whose favorites to list", example = "justin") @PathVariable String username) {
        UserEntity owner = this.users.findByUsernameIgnoreCase(username);

        if (owner == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "USER_NOT_FOUND",
                    "User not found",
                    "username"
            );
        }

        return this.artists.findFavoritesOf(owner.getUserId()).stream()
                .map(ArtistController::toSummary)
                .toList();
    }

    private ArtistEntity findArtist(String artistId) {
        ArtistEntity artist = this.artists.findByPublicId(artistId);

        if (artist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "ARTIST_NOT_FOUND",
                    "Artist not found",
                    "artistId"
            );
        }

        return artist;
    }

    private ArtistEntity findEditableArtist(String artistId, UserEntity user) {
        ArtistEntity artist = findArtist(artistId);

        if (!artist.isEditableBy(user.getUserId())) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "NOT_ARTIST_CREATOR",
                    "Only the artist's creator can change it",
                    "artistId"
            );
        }

        return artist;
    }

    private static ArtistSummary toSummary(ArtistEntity artist) {
        return new ArtistSummary(artist.getPublicId(), artist.getArtistName(), artist.getArtistPfpUrl());
    }

}
