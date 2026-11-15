package com.aux_app.dto.album;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "An album's page: everything in AlbumOverview, plus who created it and when")
public record AlbumDetails(
        @Schema(description = "Public id of the album", example = "l_4Hq9xT") String albumId,
        @Schema(description = "Album name", example = "Currents") String albumName,
        @Schema(description = "Square WebP cover URL", example = "https://aux.justinabruinsma.com/album-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        @Schema(description = "The album's artist") ArtistSummary artistSummary,
        @Schema(description = "Pieces in the album the caller can play on their own: public ones, plus the caller's private ones", example = "12") int pieceCount,
        @Schema(description = "Whether the caller can rename, re-cover or delete the album: they created it or its artist. False with no token", example = "false") boolean canEdit,
        @Schema(description = "The user who created the album; null once their account is deleted", nullable = true) PlaylistOwner createdBy,
        @Schema(description = "When the album was created (UTC)", example = "2026-11-14T18:02:11Z") Instant createdAt
) {
}
