package com.aux_app.repository;

import java.util.List;

import com.aux_app.dto.playlist.PlaylistMemberStatus;
import com.aux_app.entity.PlaylistMemberEntity;
import com.aux_app.entity.PlaylistMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistMemberRepository extends JpaRepository<PlaylistMemberEntity, PlaylistMemberId> {

    PlaylistMemberEntity findByPlaylistIdAndUserId(String playlistId, String userId);

    List<PlaylistMemberEntity> findByPlaylistId(String playlistId);

    List<PlaylistMemberEntity> findByUserIdAndStatus(String userId, PlaylistMemberStatus status);

}
