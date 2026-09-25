package com.aux.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserFavoriteTrackId implements Serializable {
    private String userId;
    private String musicPieceId;

    public UserFavoriteTrackId() {}

    public UserFavoriteTrackId(String userId, String musicPieceId) {
        this.userId = userId;
        this.musicPieceId = musicPieceId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserFavoriteTrackId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(musicPieceId, that.musicPieceId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, musicPieceId); }
}
