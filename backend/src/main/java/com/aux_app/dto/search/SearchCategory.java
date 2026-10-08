package com.aux_app.dto.search;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "What to search for")
public enum SearchCategory {
    USERS,
    MUSIC,
    PLAYLISTS
}
