package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "LISTENER can only play the playlist; EDITOR can also change its tracks")
public enum PlaylistPermission {
    LISTENER,
    EDITOR
}
