package com.aux_app.dto.music_piece;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.users.PlaylistOwner;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A music piece in the top list")
public record TopMusicPiece(
        @Schema(description = "Public id of the music piece; play it with `/stream` and no `playlistId`", example = "m_92kd0Z") String musicPieceId,
        @Schema(description = "Title", example = "Devil In a New Dress") String name,
        @Schema(description = "Square WebP cover URL", example = "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String coverUrl,
        @Schema(description = "The artist") ArtistSummary artistSummary,
        @Schema(description = "The user who uploaded the piece") PlaylistOwner uploader,
        @Schema(description = "Plays counted for the list: the last 7 days, not counting the uploader's own", example = "42") int playCount
) {
}
