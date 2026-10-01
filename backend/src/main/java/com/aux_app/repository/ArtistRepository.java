package com.aux_app.repository;

import com.aux_app.entity.ArtistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ArtistRepository extends JpaRepository<ArtistEntity, String> {

    ArtistEntity findByArtistId(String artistId);

    // Matches the uq_artists_name_nocase index, so pass a stripped name
    ArtistEntity findByArtistNameIgnoreCase(String artistName);

    List<ArtistEntity> findTop20ByArtistNameContainingIgnoreCaseOrderByArtistName(String query);

    // Newest favorite first
    @Query("""
            select a from ArtistEntity a, UserFavoriteArtistEntity f
            where f.artistId = a.artistId and f.userId = :userId
            order by f.createdAt desc
            """)
    List<ArtistEntity> findFavoritesOf(@Param("userId") String userId);

}
