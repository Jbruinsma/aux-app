package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// Maps the `albums` table. Covers are shared with pieces that use them; see AlbumService
@Entity
@Table(name = "albums")
public class AlbumEntity {

    @Id
    @Column(name = "album_id")
    private String albumId;

    // What the API shows instead of the UUID; see PublicId
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @Column(name = "album_name", nullable = false)
    private String albumName;

    @Column(name = "cover_url", nullable = false)
    private String coverUrl;

    // Null once the creator's account is deleted; the artist's creator can still manage the album
    @Column(name = "created_by_user_id")
    private String createdByUserId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AlbumEntity() {}

    public AlbumEntity(String albumId, String artistId, String albumName, String createdByUserId) {
        this.albumId = albumId;
        this.publicId = PublicId.generate(PublicId.ALBUM);
        this.artistId = artistId;
        this.albumName = albumName;
        this.createdByUserId = createdByUserId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getAlbumId() { return albumId; }
    public String getPublicId() { return publicId; }
    public String getArtistId() { return artistId; }
    public String getAlbumName() { return albumName; }
    public String getCoverUrl() { return coverUrl; }
    public String getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setAlbumName(String albumName) { this.albumName = albumName; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    // The album's creator, or the creator of its artist
    public boolean isEditableBy(String userId, ArtistEntity artist) {
        return userId != null && (userId.equals(createdByUserId) || artist.isEditableBy(userId));
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
