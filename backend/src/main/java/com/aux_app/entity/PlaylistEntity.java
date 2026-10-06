package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "playlists")
public class PlaylistEntity {

    @Id
    @Column(name = "playlist_id",  nullable = false, length = 36)
    private String playlistId;

    // What the API shows instead of the UUID; see PublicId
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic;

    @Column(name = "playlist_cover_url", nullable = false)
    private String playlistCoverUrl;

    @Column(name = "playlist_name", nullable = false, length = 36)
    private String playlistName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public PlaylistEntity() {}

    public PlaylistEntity(String playlistId, String ownerId, String playlistName, boolean isPublic) {
        this.playlistId = playlistId;
        this.publicId = PublicId.generate(PublicId.PLAYLIST);
        this.ownerId = ownerId;
        this.playlistName = playlistName;
        this.isPublic = isPublic;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getPlaylistId() { return playlistId; }
    public String getPublicId() { return publicId; }
    public String getOwnerId() { return ownerId; }
    public Boolean getIsPublic() { return isPublic; }
    public String getPlaylistCoverUrl() { return playlistCoverUrl; }
    public String getPlaylistName() { return playlistName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPlaylistCoverUrl(String playlistCoverUrl) { this.playlistCoverUrl = playlistCoverUrl; }
    public void setPlaylistName(String playlistName) { this.playlistName = playlistName; }
    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }

    // Only runs when a column actually changed, so a no-op edit leaves it alone
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
