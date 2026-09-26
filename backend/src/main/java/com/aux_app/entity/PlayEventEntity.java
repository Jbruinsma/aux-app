package com.aux_app.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

// Maps the `play_events` table
@Entity
@Table(name = "play_events", indexes = {
        @Index(name = "idx_play_events_user_played_at", columnList = "user_id, played_at"),
        @Index(name = "idx_play_events_music_piece_played_at", columnList = "music_piece_id, played_at")
})
public class PlayEventEntity {

    @Id
    @Column(name = "play_event_id")
    private String playEventId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "music_piece_id", nullable = false)
    private String musicPieceId;

    @Column(name = "played_at", nullable = false)
    private Instant playedAt;

    @Column(name = "listen_duration_seconds", nullable = false)
    private Integer listenDurationSeconds;

    @Column(name = "context_playlist_id")
    private String contextPlaylistId;

    protected PlayEventEntity() {}

    public PlayEventEntity(String playEventId, String userId, String musicPieceId, Integer listenDurationSeconds) {
        this.playEventId = playEventId;
        this.userId = userId;
        this.musicPieceId = musicPieceId;
        this.listenDurationSeconds = listenDurationSeconds;
        this.playedAt = Instant.now();
    }

    public String getPlayEventId() { return playEventId; }
    public String getUserId() { return userId; }
    public String getMusicPieceId() { return musicPieceId; }
    public Instant getPlayedAt() { return playedAt; }
    public Integer getListenDurationSeconds() { return listenDurationSeconds; }
    public String getContextPlaylistId() { return contextPlaylistId; }
}
