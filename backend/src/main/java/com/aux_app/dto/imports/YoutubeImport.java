package com.aux_app.dto.imports;

import java.time.Instant;

import com.aux_app.dto.base.ErrorDetails;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A YouTube import. Poll `GET /api/imports/youtube/{importId}` every couple of seconds until it is READY or FAILED")
public record YoutubeImport(
        @Schema(description = "Send as `importId` to `POST /api/music-pieces/create` once READY", example = "6f1c2a9e-3b7d-4e0a-9c51-8d2f4b6a7e10") String importId,
        YoutubeImportStatus status,
        @Schema(description = "YouTube video id", example = "dQw4w9WgXcQ") String videoId,
        @Schema(description = "The video's title, to prefill the piece's name. Null until FETCHING_INFO is done", nullable = true) String title,
        @Schema(description = "Length of the audio in seconds. Null until FETCHING_INFO is done", nullable = true) Integer durationSeconds,
        @Schema(description = "YouTube's thumbnail, for a preview only. The piece still needs its own cover (or an album's)", example = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg") String thumbnailUrl,
        @Schema(description = "Why it FAILED; null otherwise. Codes: AUDIO_TOO_SHORT, AUDIO_TOO_LONG, AUDIO_TOO_LARGE, VIDEO_IS_LIVE, VIDEO_UNAVAILABLE, VIDEO_REJECTED, IMPORT_BLOCKED, IMPORT_TIMED_OUT, IMPORT_FAILED", nullable = true) ErrorDetails error,
        @Schema(description = "When a READY import is thrown away if it hasn't been used; null otherwise", nullable = true) Instant expiresAt
) {}
