package com.aux_app.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.RefreshTokenEntity;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.familyId = :familyId")
    int deleteFamily(@Param("familyId") String familyId);

    // Every session the user has except `keepFamilyId` (null keeps none)
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.userId = :userId AND (:keepFamilyId IS NULL OR t.familyId <> :keepFamilyId)")
    int deleteOtherFamilies(@Param("userId") String userId, @Param("keepFamilyId") String keepFamilyId);

    @Modifying
    @Query("DELETE FROM RefreshTokenEntity t WHERE t.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
