package com.aux_app.entity;

import java.io.Serializable;
import java.util.Objects;

public class PlaylistMemberId implements Serializable {
    private String playlistId;
    private String userId;

    public PlaylistMemberId() {}

    public PlaylistMemberId(String playlistId, String userId) {
        this.playlistId = playlistId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlaylistMemberId that)) return false;
        return Objects.equals(playlistId, that.playlistId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() { return Objects.hash(playlistId, userId); }
}
