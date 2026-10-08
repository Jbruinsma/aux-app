package com.aux_app.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;

import com.aux_app.dto.playlist.ProfilePlaylist;

import java.util.List;

@Schema(description = "A user's public profile and the caller's relation to them")
public record UserProfile(
        @Schema(description = "Username", example = "justin") String username,
        @Schema(description = "Profile picture URL", example = "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String pfpUrl,
        @Schema(description = "Banner URL", example = "https://aux.justinabruinsma.com/banner/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp") String bannerUrl,
        @Schema(description = "Optional profile fields") ProfileDetails profileDetails,
        @Schema(description = "The user's playlists: public ones, plus private ones when the caller is the owner") List<ProfilePlaylist> playlists,
        @Schema(description = "Whether the caller owns this profile", example = "false") boolean isMe,
        @Schema(description = "Whether the caller follows this user", example = "true") boolean isFollowing,
        @Schema(description = "Whether this user follows the caller", example = "false") boolean followingMe
) {
}