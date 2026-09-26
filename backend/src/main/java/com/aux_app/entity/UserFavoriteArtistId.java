package com.aux_app.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserFavoriteArtistId implements Serializable {
    private String userId;
    private String artistId;

    public UserFavoriteArtistId() {}

    public UserFavoriteArtistId(String userId, String artistId) {
        this.userId = userId;
        this.artistId = artistId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UserFavoriteArtistId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(artistId, that.artistId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, artistId); }
}
