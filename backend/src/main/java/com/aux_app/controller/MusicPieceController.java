package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.music_piece.MusicPieceCreationDetails;
import com.aux_app.dto.music_piece.MusicPieceDetailsUpdate;
import com.aux_app.dto.music_piece.DiscoverResponse;
import com.aux_app.dto.base.Message;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.music_piece.MusicPieceStream;
import com.aux_app.dto.music_piece.PlayEventCreation;
import com.aux_app.dto.music_piece.UploadedMusicPiecesResponse;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.LiveEventService;
import com.aux_app.services.PlaybackEventService;
import com.aux_app.services.RecommendationService;
import com.aux_app.services.TagService;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/music-pieces")
public class MusicPieceController {

    private final UserRepository users;
    private final UploadService uploads;
    private final ArtistRepository artists;
    private final MusicPieceRepository musicPieces;
    private final PlaybackEventService playbackEvents;
    private final TagService tags;
    private final RecommendationService recommendations;
    private final ApplicationEventPublisher events;

    public MusicPieceController(
            UserRepository users,
            UploadService uploads,
            ArtistRepository artists,
            MusicPieceRepository musicPieces,
            PlaybackEventService playbackEvents,
            TagService tags,
            RecommendationService recommendations,
            ApplicationEventPublisher events
    ) {
        this.users = users;
        this.uploads = uploads;
        this.artists = artists;
        this.musicPieces = musicPieces;
        this.playbackEvents = playbackEvents;
        this.tags = tags;
        this.recommendations = recommendations;
        this.events = events;
    }

    @GetMapping("")
    @Operation(
            summary = "List the caller's uploads",
            description = """
                    Every music piece the caller uploaded, public and private, newest first, each with its artist.
                    `isFavorite` is true when the caller has favorited the piece.
                    Path is `GET /api/music-pieces`, no trailing slash.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; `uploadedMusicPieces` is empty if the caller has uploaded nothing")
    public UploadedMusicPiecesResponse getMusicPieces(
            @CurrentUser UserEntity user
    ) {
        return UploadedMusicPiecesResponse.of(musicPieces.findUploads(user.getUserId()));
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a music piece",
            description = """
                    Multipart fields:
                    - `name` (required): at most 200 chars, trimmed.
                    - `artistId` (required): id of an existing artist.
                    - `isPublic`: `true` or `false`, defaults to `false`. A private piece is only playable by its
                      uploader, and by the other collaborators on private playlists the uploader adds it to.
                    - `mp3File` (required): an MP3, at most 25MB, 5 seconds to 10 minutes long. Counts toward the
                      caller's 2GB storage quota.
                    - `coverImage` (required): same rules as a playlist cover. Send the original image; the server
                      crops it to a square and stores it as WebP.
                    - `tags`: up to 5, one value per tag (repeat the field). Lowercased and trimmed; each is 2-24
                      letters, digits, spaces, `&` or `-`. Unknown tags are created. `GET /api/tags` suggests some.
                    The caller is the uploader. Returns the new piece; `isFavorite` is always `false`.
                    """)
    @ApiResponse(responseCode = "200", description = "Music piece created; body is the new piece")
    @ApiResponse(responseCode = "400", description = "Missing or bad field (code INVALID_FIELD), damaged, too short or too long MP3 (codes INVALID_AUDIO, AUDIO_TOO_SHORT, AUDIO_TOO_LONG), or unreadable or too small cover (codes INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Upload would pass the 2GB storage quota (code STORAGE_QUOTA_EXCEEDED), cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "File is not an MP3 (code UNSUPPORTED_AUDIO_TYPE), or cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public MusicPieceOverview createMusicPiece(
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute MusicPieceCreationDetails musicPieceCreationDetails
    ) {
        // Before the upload, so bad tags fail fast
        List<String> newTags = TagService.normalize(musicPieceCreationDetails.tags());

        ArtistEntity artist = this.artists.findByPublicId(musicPieceCreationDetails.artistId());

        if (artist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "ARTIST_NOT_FOUND",
                    "Artist not found",
                    "artistId"
            );
        }

        // Uploads the MP3 and saves the row; mp3_file_url gets the R2 key, signed into a URL at play time
        MusicPieceEntity newMusicPiece = this.uploads.storeTracks(
                user,
                List.of(musicPieceCreationDetails.mp3File()),
                stored -> {
                    UploadService.StoredTrack track = stored.getFirst();
                    MusicPieceEntity piece = new MusicPieceEntity(
                            UUID.randomUUID().toString(),
                            user.getUserId(),
                            artist.getArtistId()
                    );
                    piece.setName(musicPieceCreationDetails.name().strip());
                    piece.setIsPublic(musicPieceCreationDetails.isPublic());
                    piece.setMp3FileUrl(track.key());
                    piece.setDurationSeconds(track.durationSeconds());
                    piece.setSizeBytes(track.sizeBytes());
                    return List.of(piece);
                }
        ).getFirst();

        // The cover is required, so a failed cover undoes the whole piece
        try {
            this.uploads.replaceMusicPieceCover(newMusicPiece, musicPieceCreationDetails.coverImage());
        } catch (RuntimeException e) {
            this.musicPieces.delete(newMusicPiece);
            this.uploads.deleteTrack(newMusicPiece.getMp3FileUrl());
            throw e;
        }
        if (newTags != null) {
            this.tags.setPieceTags(newMusicPiece.getMusicPieceId(), newTags);
        }

        // Live-refreshes the caller's uploads row (see LiveEventService)
        events.publishEvent(new LiveEventService.Update(Set.of(user.getUserId()), Set.of(LiveEventService.Section.UPLOADS)));

        return this.musicPieces.findOverviews(List.of(newMusicPiece.getPublicId()), user.getUserId()).getFirst();
    }

    @PutMapping(value = "/{musicPieceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Edit a music piece's details",
            description = """
                    Partial update of a piece the caller uploaded. Every multipart field is optional; a missing or
                    blank field leaves that value unchanged.
                    - `name`: at most 200 chars, trimmed.
                    - `artistId`: id of an existing artist.
                    - `isPublic`: `true` or `false`. Making a piece private marks it unavailable on public playlists
                      and on playlists where the caller is no longer a collaborator; it stays in those playlists.
                    - `coverImage`: same rules as on upload. Replaces the old cover.
                    - `tags`: same rules as on upload. Replaces all tags; send a single blank `tags` value to remove
                      them all.
                    The MP3 can't be replaced; upload a new piece instead. Returns the piece after the update.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Bad field, or unreadable or too small cover (codes INVALID_FIELD, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "404", description = "Music piece not found or not the caller's (code MUSIC_PIECE_NOT_FOUND), or artist not found (code ARTIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public MusicPieceOverview editMusicPiece(
            @CurrentUser UserEntity user,
            @Parameter(description = "Public id of the music piece", example = "m_92kd0Z") @PathVariable String musicPieceId,
            @Valid @ModelAttribute MusicPieceDetailsUpdate details
    ) {
        MusicPieceEntity piece = this.musicPieces.findByPublicId(musicPieceId);

        // Someone else's piece looks missing, same as on /stream
        if (piece == null || !piece.getUploaderUserId().equals(user.getUserId())) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "MUSIC_PIECE_NOT_FOUND",
                    "Music piece not found",
                    "musicPieceId"
            );
        }

        // Before any write, so bad tags leave the piece unchanged
        List<String> newTags = TagService.normalize(details.tags());

        if (details.artistId() != null) {
            ArtistEntity artist = this.artists.findByPublicId(details.artistId());
            if (artist == null) {
                throw new AuxException(
                        HttpStatus.NOT_FOUND,
                        "ARTIST_NOT_FOUND",
                        "Artist not found",
                        "artistId"
                );
            }
            piece.setArtistId(artist.getArtistId());
        }
        if (details.name() != null) {
            piece.setName(details.name());
        }
        if (details.isPublic() != null) {
            piece.setIsPublic(details.isPublic());
        }

        if (details.coverImage() != null) {
            this.uploads.replaceMusicPieceCover(piece, details.coverImage());
        } else {
            this.musicPieces.save(piece);
        }
        if (newTags != null) {
            this.tags.setPieceTags(piece.getMusicPieceId(), newTags);
        }

        // ponytail: only the uploader's rows refresh live; other listeners' recents catch up on their next refresh
        events.publishEvent(new LiveEventService.Update(
                Set.of(user.getUserId()),
                Set.of(LiveEventService.Section.UPLOADS, LiveEventService.Section.RECENTS, LiveEventService.Section.TOP_ARTISTS)
        ));

        return this.musicPieces.findOverviews(List.of(piece.getPublicId()), user.getUserId()).getFirst();
    }

    @GetMapping("/{musicPieceId}/discover")
    @Operation(
            summary = "Discover music like a piece",
            description = """
                    Up to 100 public pieces like the given one, best match first. Pieces score for sharing its tags,
                    for sharing the artist's most used tags, and for being played by the same listeners. At most 3
                    are by the same artist. When too few match, the most played pieces of the last 30 days fill in.
                    Leaves out the piece itself, the caller's own uploads and favorites, and pieces the caller played
                    in the last 7 days. Equal matches come back in random order, so results vary between calls.
                    The piece must be playable by the caller on its own: public, or uploaded by the caller.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; `musicPieces` is empty if nothing fits")
    @ApiResponse(responseCode = "404", description = "Music piece not found or not playable by the caller (code MUSIC_PIECE_NOT_FOUND)")
    public DiscoverResponse discover(
            @CurrentUser UserEntity user,
            @Parameter(description = "Public id of the music piece", example = "m_92kd0Z") @PathVariable String musicPieceId
    ) {
        MusicPieceEntity piece = this.musicPieces.findByPublicId(musicPieceId);

        if (piece == null || !piece.isPlayableBy(user.getUserId())) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "MUSIC_PIECE_NOT_FOUND",
                    "Music piece not found",
                    "musicPieceId"
            );
        }

        return new DiscoverResponse(this.recommendations.discover(piece, user));
    }

    @GetMapping("/{musicPieceId}/stream")
    @Operation(
            summary = "Get a playable URL for a music piece",
            description = """
                    Returns a signed URL to the MP3 in the private audio bucket, valid for `expiresInSeconds`.
                    Fetch a new one when it runs out. A private piece is only playable by its uploader, or from a
                    private playlist whose owner or accepted member uploaded it (pass that `playlistId`).
                    Also returns a single-use `playToken` for reporting the listen to `/plays`. Pass `playlistId`
                    when playing from a playlist; the playlist must contain the piece and be visible to the caller.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Music piece not found, or not playable by the caller (code MUSIC_PIECE_NOT_FOUND), or playlist not found, not visible, or without this piece (code PLAYLIST_NOT_FOUND)")
    public MusicPieceStream getMusicPieceStream(
            @CurrentUser UserEntity user,
            @PathVariable String musicPieceId,
            @Parameter(description = "Public id of the playlist the piece is played from") @RequestParam(required = false) String playlistId
    ) {
        MusicPieceEntity piece = this.musicPieces.findByPublicId(musicPieceId);

        if (piece == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "MUSIC_PIECE_NOT_FOUND",
                    "Music piece not found",
                    "musicPieceId"
            );
        }

        // Checks the caller can play the piece before any URL is signed
        String playToken = this.playbackEvents.issuePlayToken(user, piece, playlistId);

        return new MusicPieceStream(
                this.uploads.signedAudioUrl(piece),
                (int) UploadService.SIGNED_URL_TTL.toSeconds(),
                playToken
        );
    }

    @PostMapping("/{musicPieceId}/plays")
    @Operation(
            summary = "Record a listen of a music piece",
            description = """
                    JSON body: `playToken` from the `/stream` call that started the listen, and
                    `listenDurationSeconds`. Send it once the caller has listened at least 30 seconds (or the whole
                    piece, if shorter). Each token counts once.
                    The server credits at most the time since the token was issued and the piece's length, and
                    rejects a listen that overlaps the caller's previous play. It is fine to fetch the next piece's
                    token early, while the current one is still playing.
                    Feeds listening history, last playback and top music pieces.
                    """)
    @ApiResponse(responseCode = "200", description = "Play recorded")
    @ApiResponse(responseCode = "400", description = "Bad field (codes INVALID_FIELD, MALFORMED_BODY), bad, expired or someone else's token (code INVALID_PLAY_TOKEN), or listen too short (code PLAY_TOO_SHORT)")
    @ApiResponse(responseCode = "404", description = "Music piece or playlist no longer available (codes MUSIC_PIECE_NOT_FOUND, PLAYLIST_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Token already used (code PLAY_ALREADY_RECORDED), or listen overlaps the previous play (code PLAY_OVERLAPS)")
    public Message recordPlay(
            @CurrentUser UserEntity user,
            @PathVariable String musicPieceId,
            @Valid @RequestBody PlayEventCreation playEvent
    ) {
        this.playbackEvents.recordPlay(user, musicPieceId, playEvent.playToken(), playEvent.listenDurationSeconds());
        return new Message("Play recorded");
    }

}
