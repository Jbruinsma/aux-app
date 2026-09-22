package com.aux.entity;

import java.io.Serializable;
import java.util.Objects;

public class PlaylistTrackId implements Serializable {
    private String playlistId;
    private String musicPieceId;

    public PlaylistTrackId() {}

    public PlaylistTrackId(String playlistId, String musicPieceId) {
        this.playlistId = playlistId;
        this.musicPieceId = musicPieceId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlaylistTrackId that)) return false;
        return Objects.equals(playlistId, that.playlistId) && Objects.equals(musicPieceId, that.musicPieceId);
    }

    @Override
    public int hashCode() { return Objects.hash(playlistId, musicPieceId); }
}
