package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The caller's most recently played music pieces")
public record RecentListeningResponse(
        @Schema(description = "Up to 4 distinct pieces, most recently played first; each with the playlist of its latest play") List<LastPlayback> recents
) {
}
