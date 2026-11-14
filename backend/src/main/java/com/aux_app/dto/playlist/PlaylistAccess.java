package com.aux_app.dto.playlist;

// The caller's access level on a playlist. No class-level @Schema description: each field that uses it gives its own,
// and ModelConverters.readAll (OpenApiConfig, for the event-stream types) would let a class description replace it
public enum PlaylistAccess {
    OWNER,
    EDITOR,
    LISTENER
}
