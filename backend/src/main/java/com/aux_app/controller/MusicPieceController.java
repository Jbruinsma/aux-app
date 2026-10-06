package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceCreationDetails;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/music-pieces")
public class MusicPieceController {

    private final UserRepository users;
    private final UploadService uploads;
    private final ArtistRepository artists;
    private final MusicPieceRepository musicPieces;

    public MusicPieceController(
            UserRepository users,
            UploadService uploads,
            ArtistRepository artists,
            MusicPieceRepository musicPieces
    ) {
        this.users = users;
        this.uploads = uploads;
        this.artists = artists;
        this.musicPieces = musicPieces;
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
                    UploadService.StoredTrack track = stored.get(0);
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

}
