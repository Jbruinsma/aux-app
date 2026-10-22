package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Every music piece the caller uploaded")
public record UploadedMusicPiecesResponse(
        @Schema(description = "Number of pieces in `uploadedMusicPieces`", example = "4") Integer uploadedPiecesCount,
        @Schema(description = "Public and private uploads, newest first; empty if the caller has uploaded nothing. `isFavorite` is whether the caller favorited the piece") List<MusicPieceOverview> uploadedMusicPieces
) {

    // Shared by GET /api/music-pieces and the `uploads` live event
    public static UploadedMusicPiecesResponse of(List<MusicPieceOverview> uploads) {
        return new UploadedMusicPiecesResponse(uploads.size(), uploads);
    }
}
