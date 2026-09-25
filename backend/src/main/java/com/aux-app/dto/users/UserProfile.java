package com.aux.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux.dto.playlist.ProfilePlaylist;

import java.util.List;

public record UserProfile(
        @Schema(example = "justin") String username,
        @Schema(example = "/uploads/users/u_41x9.jpg") String pfpUrl,
        List<ProfilePlaylist> playlists,
        @Schema(example = "false") boolean isMe,
        @Schema(example = "true") boolean isFollowing,
        @Schema(example = "false") boolean followingMe
) {
}