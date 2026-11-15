package com.aux_app.dto.album;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The short form of an album, shown next to a music piece")
public record AlbumSummary(
        @Schema(description = "Public id of the album", example = "l_4Hq9xT") String albumId,
        @Schema(description = "Album name", example = "Currents") String albumName
) {
}
