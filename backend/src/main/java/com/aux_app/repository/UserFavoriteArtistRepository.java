package com.aux_app.repository;

import com.aux_app.entity.UserFavoriteArtistEntity;
import com.aux_app.entity.UserFavoriteArtistId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFavoriteArtistRepository extends JpaRepository<UserFavoriteArtistEntity, UserFavoriteArtistId> {
}
