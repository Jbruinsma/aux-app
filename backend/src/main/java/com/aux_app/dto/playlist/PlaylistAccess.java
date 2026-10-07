package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The caller's access level on a playlist")
public enum PlaylistAccess {
    OWNER,
    EDITOR,
    LISTENER
}
