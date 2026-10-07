package com.aux_app.entity;

import java.time.Instant;

import com.aux_app.dto.playlist.PlaylistMemberStatus;
import com.aux_app.dto.playlist.PlaylistPermission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

// Maps the `playlist_members` table. Only ACCEPTED rows grant access; PENDING is an unanswered invite.
@Entity
@Table(name = "playlist_members")
@IdClass(PlaylistMemberId.class)
public class PlaylistMemberEntity {

    @Id
    @Column(name = "playlist_id")
    private String playlistId;

    @Id
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private PlaylistPermission permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PlaylistMemberStatus status;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected PlaylistMemberEntity() {}

    public PlaylistMemberEntity(
            String playlistId,
            String userId,
            PlaylistPermission permission
    ) {
        this.playlistId = playlistId;
        this.userId = userId;
        this.permission = permission;
        this.status = PlaylistMemberStatus.PENDING;
        this.invitedAt = Instant.now();
    }

    public String getPlaylistId() { return playlistId; }
    public String getUserId() { return userId; }
    public PlaylistPermission getPermission() { return permission; }
    public PlaylistMemberStatus getStatus() { return status; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getRespondedAt() { return respondedAt; }

    public void setPermission(PlaylistPermission permission) { this.permission = permission; }

    public void accept() {
        this.status = PlaylistMemberStatus.ACCEPTED;
        this.respondedAt = Instant.now();
    }
}
