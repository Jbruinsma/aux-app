package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlists")
public class PlaylistEntity {

    @Id
    @Column(name = "playlist_id")
    private String playlistId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "playlist_cover_url")
    private String playlistCoverUrl;

    @Column(name = "playlist_name")
    private String playlistName;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PlaylistEntity() {}

    public PlaylistEntity(String playlistId, String ownerId, String playlistName, boolean isPublic) {
        this.playlistId = playlistId;
        this.ownerId = ownerId;
        this.playlistName = playlistName;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getPlaylistId() { return playlistId; }
    public String getOwnerId() { return ownerId; }
    public Boolean getIsPublic() { return isPublic; }
    public String getPlaylistCoverUrl() { return playlistCoverUrl; }
    public String getPlaylistName() { return playlistName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
