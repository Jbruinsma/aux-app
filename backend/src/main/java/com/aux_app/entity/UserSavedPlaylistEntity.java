package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Maps the `user_saved_playlists` table
@Entity
@Table(name = "user_saved_playlists")
@IdClass(UserSavedPlaylistId.class)
public class UserSavedPlaylistEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "playlist_id")
    private String playlistId;

    @Column(name = "saved_at")
    private Instant savedAt;

    protected UserSavedPlaylistEntity() {}

    public UserSavedPlaylistEntity(String userId, String playlistId) {
        this.userId = userId;
        this.playlistId = playlistId;
        this.savedAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getPlaylistId() { return playlistId; }
    public Instant getSavedAt() { return savedAt; }
}
