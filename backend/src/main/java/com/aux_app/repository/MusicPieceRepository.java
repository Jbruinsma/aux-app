package com.aux_app.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.MusicPieceEntity;

public interface MusicPieceRepository extends JpaRepository<MusicPieceEntity, String> {

    @Query("select coalesce(sum(m.sizeBytes), 0) from MusicPieceEntity m where m.uploaderUserId = :userId")
    long totalSizeBytes(@Param("userId") String userId);
}
