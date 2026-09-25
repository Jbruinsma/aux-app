package com.aux.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux.entity.UserEntity;

// Public view of a user: never includes the password hash
public record UserSummary(
        @Schema(example = "u_41x9") String userId,
        @Schema(example = "justin") String username,
        @Schema(example = "/uploads/users/u_41x9.jpg") String profilePictureUrl
) {
    public static UserSummary of(UserEntity user) {
        return new UserSummary(user.getUserId(), user.getUsername(), user.getProfilePictureUrl());
    }
}
