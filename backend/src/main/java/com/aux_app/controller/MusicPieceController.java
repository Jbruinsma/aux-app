package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceCreationDetails;
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
import com.aux_app.services.PlaybackEventService;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/music-pieces")
public class MusicPieceController {

    private final UserRepository users;
    private final UploadService uploads;
    private final ArtistRepository artists;
    private final MusicPieceRepository musicPieces;
    private final PlaybackEventService playbackEvents;

    public MusicPieceController(
            UserRepository users,
            UploadService uploads,
            ArtistRepository artists,
            MusicPieceRepository musicPieces,
            PlaybackEventService playbackEvents
    ) {
        this.users = users;
        this.uploads = uploads;
        this.artists = artists;
        this.musicPieces = musicPieces;
        this.playbackEvents = playbackEvents;
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
        List<MusicPieceRepository.MusicPieceWithArtist> uploadsWithArtist = musicPieces.findUploadsWithArtist(
                user.getUserId()
        );

        List<MusicPieceOverview> uploadedMusicPieces = new ArrayList<>();

        for (MusicPieceRepository.MusicPieceWithArtist uploadedPiece : uploadsWithArtist) {
            MusicPieceEntity musicPiece = uploadedPiece.piece();
            ArtistEntity artist = uploadedPiece.artist();
            boolean isFavorite = uploadedPiece.isFavorite();

            uploadedMusicPieces.add(
                    new MusicPieceOverview(
                            musicPiece.getPublicId(),
                            musicPiece.getName(),
                            musicPiece.getCoverUrl(),
                            new ArtistSummary(
                                    artist.getPublicId(),
                                    artist.getArtistName(),
                                    artist.getArtistPfpUrl()
                            ),
                            isFavorite
                    )
            );
        }

        return new UploadedMusicPiecesResponse(
                uploadedMusicPieces.size(),
                uploadedMusicPieces
        );
    }

    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a music piece",
            description = """
                    Multipart fields:
                    - `name` (required): at most 200 chars, trimmed.
                    - `artistId` (required): id of an existing artist.
                    - `isPublic`: `true` or `false`, defaults to `false`. Only the uploader can play a private piece,
                      whatever playlists it is in.
                    - `mp3File` (required): an MP3, at most 25MB, 5 seconds to 10 minutes long. Counts toward the
                      caller's 2GB storage quota.
                    - `coverImage` (required): same rules as a playlist cover. Send the original image; the server
                      crops it to a square and stores it as WebP.
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

        return new MusicPieceOverview(
                newMusicPiece.getPublicId(),
                newMusicPiece.getName(),
                newMusicPiece.getCoverUrl(),
                new ArtistSummary(artist.getPublicId(), artist.getArtistName(), artist.getArtistPfpUrl()),
                false
        );
    }

    @GetMapping("/{musicPieceId}/stream")
    @Operation(
            summary = "Get a playable URL for a music piece",
            description = """
                    Returns a signed URL to the MP3 in the private audio bucket, valid for `expiresInSeconds`.
                    Fetch a new one when it runs out. A private piece is only playable by its uploader.
                    Also returns a single-use `playToken` for reporting the listen to `/plays`. Pass `playlistId`
                    when playing from a playlist; the playlist must contain the piece and be visible to the caller.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Music piece not found, or private and not the caller's (code MUSIC_PIECE_NOT_FOUND), or playlist not found, not visible, or without this piece (code PLAYLIST_NOT_FOUND)")
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

        String url = this.uploads.signedAudioUrl(piece, user.getUserId());

        return new MusicPieceStream(
                url,
                (int) UploadService.SIGNED_URL_TTL.toSeconds(),
                this.playbackEvents.issuePlayToken(user, piece, playlistId)
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
