package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Maps the `music_pieces` table
@Entity
@Table(name = "music_pieces")
public class MusicPieceEntity {

    @Id
    @Column(name = "music_piece_id")
    private String musicPieceId;

    // What the API shows instead of the UUID; see PublicId
    @Column(name = "public_id", nullable = false, unique = true, length = 8)
    private String publicId;

    @Column(name = "uploader_user_id", nullable = false)
    private String uploaderUserId;

    @Column(name = "cover_url")
    private String coverUrl;

    private String name;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    // R2 key in the private audio bucket, not a URL; see UploadService.signedAudioUrl
    @Column(name = "mp3_file_url", nullable = false)
    private String mp3FileUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Counts toward the uploader's storage quota
    @Column(name = "size_bytes", nullable = false)
    private Integer sizeBytes;

    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = false;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MusicPieceEntity() {}

    public MusicPieceEntity(
            String musicPieceId,
            String uploaderUserId,
            String artistId
    ) {
        this.musicPieceId = musicPieceId;
        this.publicId = PublicId.generate(PublicId.MUSIC_PIECE);
        this.uploaderUserId = uploaderUserId;
        this.artistId = artistId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getMusicPieceId() { return musicPieceId; }
    public String getPublicId() { return publicId; }
    public String getUploaderUserId() { return uploaderUserId; }
    public String getCoverUrl() { return coverUrl; }
    public String getName() { return name; }
    public String getArtistId() { return artistId; }
    public String getMp3FileUrl() { return mp3FileUrl; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Integer getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Boolean getIsPublic() { return isPublic; }

    public void setIsPublic(Boolean isPublic) { this.isPublic = isPublic; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public void setName(String name) { this.name = name; }
    public void setMp3FileUrl(String mp3FileUrl) { this.mp3FileUrl = mp3FileUrl; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public void setSizeBytes(Integer sizeBytes) { this.sizeBytes = sizeBytes; }

    public boolean isPlayableBy(String userId) {
        return Boolean.TRUE.equals(isPublic) || uploaderUserId.equals(userId);
    }
}
