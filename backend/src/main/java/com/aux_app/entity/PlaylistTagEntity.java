package com.aux_app.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlist_tags")
@IdClass(PlaylistTagId.class)
public class PlaylistTagEntity {

    @Id
    @Column(name = "playlist_id")
    private String playlistId;

    @Id
    @Column(name = "tag_id")
    private String tagId;

    protected PlaylistTagEntity() {}

    public PlaylistTagEntity(String playlistId, String tagId) {
        this.playlistId = playlistId;
        this.tagId = tagId;
    }

    public String getPlaylistId() { return playlistId; }
    public String getTagId() { return tagId; }
}
