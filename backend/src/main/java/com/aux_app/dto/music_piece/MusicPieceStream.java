package com.aux_app.dto.music_piece;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A short-lived URL the player can stream the MP3 from")
public record MusicPieceStream(
        @Schema(description = "Signed URL to the MP3 in the private audio bucket") String url,
        @Schema(description = "Seconds until the URL and play token stop working", example = "3600") int expiresInSeconds,
        @Schema(description = "Single-use token to send to `POST /api/music-pieces/{musicPieceId}/plays` once the caller has listened") String playToken
) {}
