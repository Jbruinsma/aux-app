package com.aux.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Maps the `playlist_tracks` table
@Entity
@Table(name = "playlist_tracks",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "playlist_position"}))
@IdClass(PlaylistTrackId.class)
public class PlaylistTrackEntity {

    @Id
    @Column(name = "playlist_id")
    private String playlistId;

    @Id
    @Column(name = "music_piece_id")
    private String musicPieceId;

    @Column(name = "playlist_position", nullable = false)
    private Integer playlistPosition;

    @Column(name = "added_at")
    private Instant addedAt;

    protected PlaylistTrackEntity() {}

    public PlaylistTrackEntity(String playlistId, String musicPieceId, Integer playlistPosition) {
        this.playlistId = playlistId;
        this.musicPieceId = musicPieceId;
        this.playlistPosition = playlistPosition;
        this.addedAt = Instant.now();
    }

    public String getPlaylistId() { return playlistId; }
    public String getMusicPieceId() { return musicPieceId; }
    public Integer getPlaylistPosition() { return playlistPosition; }
    public Instant getAddedAt() { return addedAt; }
}
