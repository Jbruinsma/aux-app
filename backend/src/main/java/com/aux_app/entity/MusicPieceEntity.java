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

    @Column(name = "uploader_user_id", nullable = false)
    private String uploaderUserId;

    @Column(name = "cover_url")
    private String coverUrl;

    private String name;

    @Column(name = "artist_id", nullable = false)
    private String artistId;

    @Column(name = "mp3_file_url", nullable = false)
    private String mp3FileUrl;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected MusicPieceEntity() {}

    public MusicPieceEntity(String musicPieceId, String uploaderUserId, String artistId, String mp3FileUrl) {
        this.musicPieceId = musicPieceId;
        this.uploaderUserId = uploaderUserId;
        this.artistId = artistId;
        this.mp3FileUrl = mp3FileUrl;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getMusicPieceId() { return musicPieceId; }
    public String getUploaderUserId() { return uploaderUserId; }
    public String getCoverUrl() { return coverUrl; }
    public String getName() { return name; }
    public String getArtistId() { return artistId; }
    public String getMp3FileUrl() { return mp3FileUrl; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
