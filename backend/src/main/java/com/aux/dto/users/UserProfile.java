package com.aux.dto.users;

import com.aux.dto.playlist.ProfilePlaylist;

import java.util.List;

public record UserProfile(
        String username,
        String pfpUrl,
        List<ProfilePlaylist> playlists,
        boolean isMe,
        boolean isFollowing,
        boolean followingMe
) {
}