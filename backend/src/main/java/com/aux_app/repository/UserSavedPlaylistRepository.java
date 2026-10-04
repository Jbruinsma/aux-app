package com.aux_app.repository;

import com.aux_app.entity.UserSavedPlaylistEntity;
import com.aux_app.entity.UserSavedPlaylistId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserSavedPlaylistRepository extends JpaRepository<UserSavedPlaylistEntity, UserSavedPlaylistId> {

    UserSavedPlaylistEntity findByUserIdAndPlaylistId(String userId, String playlistId);

    @Transactional
    @Modifying
    @Query("DELETE FROM UserSavedPlaylistEntity s WHERE s.userId = :userId AND s.playlistId = :playlistId")
    int deleteSaved(@Param("userId") String userId, @Param("playlistId") String playlistId);

}
