package com.aux.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserSavedPlaylistId implements Serializable {
    private String userId;
    private String playlistId;

    public UserSavedPlaylistId() {}

    public UserSavedPlaylistId(String userId, String playlistId) {
        this.userId = userId;
        this.playlistId = playlistId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserSavedPlaylistId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(playlistId, that.playlistId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, playlistId); }
}
