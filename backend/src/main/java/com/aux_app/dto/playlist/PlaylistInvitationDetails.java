package com.aux_app.dto.playlist;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlaylistInvitationDetails(
        @Schema(description = "Public id of the user to invite or update", example = "u_41x9Rb") @NotBlank String userId,
        @Schema(description = "Access level to grant") @NotNull PlaylistPermission permission
) {
}
