package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record PlaylistMemberRemoval(
        @Schema(description = "Public id of the member (or invitee) to remove", example = "u_41x9Rb") @NotBlank String userId
) {
}
