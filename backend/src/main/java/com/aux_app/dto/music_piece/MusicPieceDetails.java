package com.aux_app.dto.music_piece;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

// MusicPieceOverview's fields plus the details page's. Flat so the frontend can treat it as an overview
@Schema(description = "A music piece's details page: everything in MusicPieceOverview, plus length, plays and upload date")
public record MusicPieceDetails(
        @Schema(description = "Public id of the music piece", example = "m_92kd0Z") String musicPieceId,
        @Schema(description = "Title", example = "Let It Happen") String name,
        @Schema(description = "Square WebP cover URL", example = "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        @Schema(description = "The artist") ArtistSummary artistSummary,
        @Schema(description = "The user who uploaded the piece") PlaylistOwner uploader,
        @Schema(description = "Up to 5 lowercase tags, in name order; empty if none", example = "[\"chill\", \"lo-fi\"]") List<String> tags,
        @Schema(description = "Whether the music piece is public") boolean isPublic,
        @Schema(description = "Whether the caller has favorited the piece; false with no token", example = "true") boolean isFavorite,
        @Schema(description = "Always true here: pieces the caller can't play are a 404", example = "true") boolean isAvailable,
        @Schema(description = "Length in seconds; null for pieces uploaded before lengths were stored", example = "214") Integer durationSeconds,
        @Schema(description = "Every recorded listen, all time, from everyone", example = "1280") long playCount,
        @Schema(description = "When the piece was uploaded (UTC)", example = "2026-10-03T21:28:57Z") Instant createdAt
) {
    public static MusicPieceDetails of(MusicPieceOverview o, Integer durationSeconds, long playCount, Instant createdAt) {
        return new MusicPieceDetails(o.musicPieceId(), o.name(), o.coverUrl(), o.artistSummary(), o.uploader(), o.tags(),
                o.isPublic(), o.isFavorite(), o.isAvailable(), durationSeconds, playCount, createdAt);
    }
}
