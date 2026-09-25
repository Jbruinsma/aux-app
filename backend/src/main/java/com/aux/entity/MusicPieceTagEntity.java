package com.aux.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "music_piece_tags")
@IdClass(MusicPieceTagId.class)
public class MusicPieceTagEntity {

    @Id
    @Column(name = "music_piece_id")
    private String musicPieceId;

    @Id
    @Column(name = "tag_id")
    private String tagId;

    protected MusicPieceTagEntity() {}

    public MusicPieceTagEntity(String musicPieceId, String tagId) {
        this.musicPieceId = musicPieceId;
        this.tagId = tagId;
    }

    public String getMusicPieceId() { return musicPieceId; }
    public String getTagId() { return tagId; }
}
