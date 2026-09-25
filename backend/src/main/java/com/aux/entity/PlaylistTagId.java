package com.aux.entity;

import java.io.Serializable;
import java.util.Objects;

public class PlaylistTagId implements Serializable {
    private String playlistId;
    private String tagId;

    public PlaylistTagId() {}

    public PlaylistTagId(String playlistId, String tagId) {
        this.playlistId = playlistId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlaylistTagId that)) return false;
        return Objects.equals(playlistId, that.playlistId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() { return Objects.hash(playlistId, tagId); }
}
