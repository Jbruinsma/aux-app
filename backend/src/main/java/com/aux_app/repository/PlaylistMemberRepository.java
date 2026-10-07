package com.aux_app.repository;

import java.util.List;

import com.aux_app.dto.playlist.PlaylistMemberStatus;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.PlaylistMemberEntity;
import com.aux_app.entity.PlaylistMemberId;
import com.aux_app.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistMemberRepository extends JpaRepository<PlaylistMemberEntity, PlaylistMemberId> {

    PlaylistMemberEntity findByPlaylistIdAndUserId(String playlistId, String userId);

    List<PlaylistMemberEntity> findByPlaylistId(String playlistId);

    List<PlaylistMemberEntity> findByUserIdAndStatus(String userId, PlaylistMemberStatus status);

    // Every member row of a playlist (PENDING and ACCEPTED) with its playlist and user, oldest invite first.
    // `playlistId` is the internal id, not the public one.
    @Query("""
            SELECT new com.aux_app.repository.PlaylistMemberRepository$MemberWithUser(p, m, u)
            FROM PlaylistMemberEntity m
            JOIN PlaylistEntity p ON p.playlistId = m.playlistId
            JOIN UserEntity u ON u.userId = m.userId
            WHERE m.playlistId = :playlistId
            ORDER BY m.invitedAt
            """)
    List<MemberWithUser> findMembersWithUsers(@Param("playlistId") String playlistId);

    record MemberWithUser(PlaylistEntity playlist, PlaylistMemberEntity member, UserEntity user) {}

    // Every unanswered (PENDING) invite a user has, newest first, with the playlist and its owner.
    // `userId` is the internal id, not the public one.
    @Query("""
            SELECT new com.aux_app.repository.PlaylistMemberRepository$InviteWithOwner(p, m, o)
            FROM PlaylistMemberEntity m
            JOIN PlaylistEntity p ON p.playlistId = m.playlistId
            JOIN UserEntity o ON o.userId = p.ownerId
            WHERE m.userId = :userId AND m.status = com.aux_app.dto.playlist.PlaylistMemberStatus.PENDING
            ORDER BY m.invitedAt DESC
            """)
    List<InviteWithOwner> findInvitesForUser(@Param("userId") String userId);

    record InviteWithOwner(PlaylistEntity playlist, PlaylistMemberEntity member, UserEntity owner) {}

}
