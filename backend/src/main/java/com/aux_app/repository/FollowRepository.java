package com.aux_app.repository;

import com.aux_app.entity.FollowEntity;
import com.aux_app.entity.FollowId;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

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

    // Internal ids of the users `followerUserId` follows
    @Query(value = "SELECT following_user_id FROM follows WHERE follower_user_id = :followerUserId", nativeQuery = true)
    List<String> findFollowingIds(@Param("followerUserId") String followerUserId);

    // The caller's friends: users they follow who follow them back and have finished onboarding, in no particular
    // order (the controller sorts them with live data). Each has their latest play of a public piece, so a private
    // upload or a piece made private since never shows; musicPieceId is '' and playedAt 0 when there's none ('' not
    // NULL: see TagRepository.PIECE_TAGS). played_at is millis when the app writes it, text in seeded rows.
    @Query(value = """
            WITH friends AS (
                SELECT f.following_user_id AS user_id FROM follows f
                JOIN follows back ON back.follower_user_id = f.following_user_id
                                 AND back.following_user_id = f.follower_user_id
                WHERE f.follower_user_id = :userId
            ),
            plays AS (
                SELECT pe.user_id, mp.public_id,
                       CASE typeof(pe.played_at) WHEN 'integer' THEN pe.played_at
                            ELSE unixepoch(pe.played_at) * 1000 END AS played_millis
                FROM play_events pe
                JOIN music_pieces mp ON mp.music_piece_id = pe.music_piece_id AND mp.is_public = 1
                WHERE pe.user_id IN (SELECT user_id FROM friends)
            ),
            latest AS (
                SELECT user_id, public_id, played_millis,
                       ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY played_millis DESC) AS rn
                FROM plays
            )
            SELECT u.user_id AS internalUserId, u.public_id AS userId, u.username AS username,
                   u.profile_picture_url AS pfpUrl,
                   COALESCE(l.public_id, '') AS musicPieceId, COALESCE(l.played_millis, 0) AS playedAt
            FROM friends fr
            JOIN users u ON u.user_id = fr.user_id AND u.onboarding_step = 'DONE'
            LEFT JOIN latest l ON l.user_id = u.user_id AND l.rn = 1
            """, nativeQuery = true)
    List<FriendRow> findFriends(@Param("userId") String userId);

    interface FriendRow {
        String getInternalUserId();
        String getUserId(); // public id
        String getUsername();
        String getPfpUrl();
        String getMusicPieceId(); // public id; '' when they've played nothing public
        Long getPlayedAt(); // epoch millis; 0 when getMusicPieceId() is ''
    }
}
