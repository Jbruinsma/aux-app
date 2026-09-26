package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_favorite_tracks")
@IdClass(UserFavoriteTrackId.class)
public class UserFavoriteTrackEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "music_piece_id")
    private String musicPieceId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected UserFavoriteTrackEntity() {}

    public UserFavoriteTrackEntity(String userId, String musicPieceId) {
        this.userId = userId;
        this.musicPieceId = musicPieceId;
        this.createdAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getMusicPieceId() { return musicPieceId; }
    public Instant getCreatedAt() { return createdAt; }
}
