package com.aux_app.repository;

import com.aux_app.entity.UserFavoriteTrackEntity;
import com.aux_app.entity.UserFavoriteTrackId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteTrackRepository extends JpaRepository<UserFavoriteTrackEntity, UserFavoriteTrackId> {
}
