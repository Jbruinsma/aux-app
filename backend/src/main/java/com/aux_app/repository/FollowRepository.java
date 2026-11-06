package com.aux_app.repository;

import com.aux_app.entity.FollowEntity;
import com.aux_app.entity.FollowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface FollowRepository extends JpaRepository<FollowEntity, FollowId> {

    // Atomic, so two simultaneous follows can't hit the primary key. Returns 0 only when `username` doesn't exist:
    // a repeat follow takes the DO UPDATE branch, which SQLite counts as a changed row
    @Transactional
    @Modifying
    @Query(value = """
            INSERT INTO follows (follower_user_id, following_user_id, created_at)
            SELECT :followerUserId, u.user_id, :createdAt FROM users u WHERE u.username = :username COLLATE NOCASE
            ON CONFLICT (follower_user_id, following_user_id) DO UPDATE SET created_at = follows.created_at
            """, nativeQuery = true)
    int follow(@Param("followerUserId") String followerUserId, @Param("username") String username,
               @Param("createdAt") Instant createdAt);

    @Transactional
    @Modifying
    @Query(value = """
            DELETE FROM follows
            WHERE follower_user_id = :followerUserId
              AND following_user_id = (SELECT u.user_id FROM users u WHERE u.username = :username COLLATE NOCASE)
            """, nativeQuery = true)
    void unfollow(@Param("followerUserId") String followerUserId, @Param("username") String username);
}
