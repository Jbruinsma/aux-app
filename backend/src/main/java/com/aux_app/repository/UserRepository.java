package com.aux_app.repository;

import com.aux_app.dto.playlist.CorePlaylist;
import com.aux_app.dto.playlist.ProfilePlaylist;
import com.aux_app.dto.users.UserProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    boolean existsByUsername(String username);

    UserEntity findByUsername(String username);

    boolean existsByEmail(String email);

    // Null if username doesn't exist. Guard before use.
    default UserProfile findProfile(String username, String currentUserId) {
        List<ProfileRow> rows = findProfileRows(username, currentUserId);
        if (rows.isEmpty()) {
            return null;
        }
        ProfileRow first = rows.get(0);
        List<ProfilePlaylist> playlists = createPlaylists(rows);
        return new UserProfile(
                first.getUsername(),
                first.getPfpUrl(),
                playlists,
                first.getUserId().equals(currentUserId),
                Boolean.TRUE.equals(first.getIsFollowing()),
                Boolean.TRUE.equals(first.getFollowingMe()));
    }

    private static @NonNull List<ProfilePlaylist> createPlaylists(List<ProfileRow> rows) {
        List<ProfilePlaylist> playlists = new ArrayList<>(rows.size());
        for (ProfileRow row : rows) {
            if (row.getPlaylistId() == null) {
                continue; // user has no visible playlists
            }
            playlists.add(
                    new ProfilePlaylist(
                            new CorePlaylist(row.getPlaylistId(), row.getPlaylistName(), row.getPlaylistCoverUrl()),
                            row.getTotalPieces()
                    )
            );
        }
        return playlists;
    }

    @Query(value = """
            SELECT u.user_id AS userId,
                   u.username AS username,
                   u.profile_picture_url AS pfpUrl,
                   EXISTS (SELECT 1 FROM follows f1
                           WHERE f1.follower_user_id = CAST(:currentUserId AS varchar) AND f1.following_user_id = u.user_id) AS isFollowing,
                   EXISTS (SELECT 1 FROM follows f2
                           WHERE f2.follower_user_id = u.user_id AND f2.following_user_id = CAST(:currentUserId AS varchar)) AS followingMe,
                   p.playlist_id AS playlistId,
                   p.playlist_name AS playlistName,
                   p.playlist_cover_url AS playlistCoverUrl,
                   (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.playlist_id) AS totalPieces
            FROM users u
            LEFT JOIN playlists p ON p.owner_id = u.user_id AND (p.is_public = true OR p.owner_id = CAST(:currentUserId AS varchar))
            WHERE u.username = :username
            """, nativeQuery = true)
    List<ProfileRow> findProfileRows(@Param("username") String username, @Param("currentUserId") String currentUserId);

    interface ProfileRow {
        String getUserId();
        String getUsername();
        String getPfpUrl();
        Boolean getIsFollowing();
        Boolean getFollowingMe();
        String getPlaylistId();
        String getPlaylistName();
        String getPlaylistCoverUrl();
        int getTotalPieces();
    }
}
