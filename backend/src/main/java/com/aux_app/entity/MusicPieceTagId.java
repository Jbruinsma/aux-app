package com.aux_app.entity;

import java.io.Serializable;
import java.util.Objects;

public class MusicPieceTagId implements Serializable {
    private String musicPieceId;
    private String tagId;

    public MusicPieceTagId() {}

    public MusicPieceTagId(String musicPieceId, String tagId) {
        this.musicPieceId = musicPieceId;
        this.tagId = tagId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MusicPieceTagId that)) return false;
        return Objects.equals(musicPieceId, that.musicPieceId) && Objects.equals(tagId, that.tagId);
    }

    @Override
    public int hashCode() { return Objects.hash(musicPieceId, tagId); }
}
