package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.playlist.ProfilePlaylist;

import java.util.List;

public record UserProfile(
        @Schema(example = "justin") String username,
        @Schema(example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        ProfileDetails profileDetails,
        List<ProfilePlaylist> playlists,
        @Schema(example = "false") boolean isMe,
        @Schema(example = "true") boolean isFollowing,
        @Schema(example = "false") boolean followingMe
) {
}