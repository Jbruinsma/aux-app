package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A user in someone's followers or following list")
public record FollowListUser(
        @Schema(description = "Public id of the user", example = "u_8f2kD1") String userId,
        @Schema(description = "Username", example = "arjay") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(description = "The caller follows this user; false when signed out", example = "true") boolean isFollowing,
        @Schema(description = "This user follows the caller; false when signed out", example = "false") boolean followsMe
) {}
