package com.aux_app.dto.music_piece;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.MusicPieceEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.MusicPieceRepository;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Every music piece the caller uploaded")
public record UploadedMusicPiecesResponse(
        @Schema(description = "Number of pieces in `uploadedMusicPieces`", example = "4") Integer uploadedPiecesCount,
        @Schema(description = "Public and private uploads, newest first; empty if the caller has uploaded nothing. `isFavorite` is whether the caller favorited the piece") List<MusicPieceOverview> uploadedMusicPieces
) {

    // Shared by GET /api/music-pieces and the `uploads` live event
    public static UploadedMusicPiecesResponse of(List<MusicPieceRepository.MusicPieceWithArtist> uploads) {
        List<MusicPieceOverview> pieces = uploads.stream()
                .map(upload -> {
                    MusicPieceEntity piece = upload.piece();
                    ArtistEntity artist = upload.artist();
                    UserEntity uploader = upload.uploader();
                    return new MusicPieceOverview(
                            piece.getPublicId(),
                            piece.getName(),
                            piece.getCoverUrl(),
                            new ArtistSummary(
                                    artist.getPublicId(),
                                    artist.getArtistName(),
                                    artist.getArtistPfpUrl()
                            ),
                            new PlaylistOwner(
                                    uploader.getPublicId(),
                                    uploader.getProfilePictureUrl(),
                                    uploader.getUsername()
                            ),
                            upload.isFavorite()
                    );
                })
                .toList();

        return new UploadedMusicPiecesResponse(pieces.size(), pieces);
    }
}
