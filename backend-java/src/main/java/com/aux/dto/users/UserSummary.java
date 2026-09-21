package com.aux.dto.users;

import com.aux.entity.UserEntity;

// Public view of a user: never includes the password hash
public record UserSummary(
        String id,
        String username,
        String email,
        String profilePicture
) {
    public static UserSummary of(UserEntity user) {
        return new UserSummary(user.getId(), user.getUsername(), user.getEmail(), user.getProfilePicture());
    }
}
