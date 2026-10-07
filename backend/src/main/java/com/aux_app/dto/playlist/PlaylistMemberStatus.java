package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "PENDING is an unanswered invite; only ACCEPTED rows grant access")
public enum PlaylistMemberStatus {
    PENDING,
    ACCEPTED
}
