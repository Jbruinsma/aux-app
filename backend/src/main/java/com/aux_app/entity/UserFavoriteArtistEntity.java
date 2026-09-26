package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_favorite_artists")
@IdClass(UserFavoriteArtistId.class)
public class UserFavoriteArtistEntity {

    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "artist_id")
    private String artistId;

    @Column(name = "created_at")
    private Instant createdAt;

    protected UserFavoriteArtistEntity() {}

    public UserFavoriteArtistEntity(String userId, String artistId) {
        this.userId = userId;
        this.artistId = artistId;
        this.createdAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getArtistId() { return artistId; }
    public Instant getCreatedAt() { return createdAt; }
}
