package com.aux.dto.users;

import com.aux.entity.UserEntity;

// Public view of a user: never includes the password hash
public record UserSummary(
        String userId,
        String username,
        String profilePictureUrl
) {
    public static UserSummary of(UserEntity user) {
        return new UserSummary(user.getUserId(), user.getUsername(), user.getProfilePictureUrl());
    }
}
