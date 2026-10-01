package com.aux_app.repository;

import com.aux_app.entity.ArtistEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<ArtistEntity, String> {

    ArtistEntity findByArtistId(String artistId);

}
