package com.aux_app.repository;

import com.aux_app.dto.base.Country;
import com.aux_app.dto.playlist.CorePlaylist;
import com.aux_app.dto.playlist.ProfilePlaylist;
import com.aux_app.dto.users.ProfileDetails;
import com.aux_app.dto.users.UserProfile;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.UserEntity;

import java.util.ArrayList;
import java.util.List;

public interface UserRepository extends JpaRepository<UserEntity, String> {

    // Usernames are case-insensitive: "mo" and "MO" are the same user (see uq_users_username_nocase)
    boolean existsByUsernameIgnoreCase(String username);

    UserEntity findByUsernameIgnoreCase(String username);

    UserEntity findByEmail(String email);

    boolean existsByEmail(String email);

    // Null if username doesn't exist. Guard before use.
    default UserProfile findProfile(String username, String currentUserId) {
        List<ProfileRow> rows = findProfileRows(username, currentUserId);

        if (rows.isEmpty()) {
            return null;
        }

        ProfileRow first = rows.getFirst();

        return new UserProfile(
                first.getUsername(),
                first.getPfpUrl(),
                first.getBannerUrl(),
                new ProfileDetails(
                        first.getDisplayName(),
                        first.getCountry(),
                        first.getWebsite(),
                        first.getAbout()),
                formatPlaylists(rows),
                first.getUserId().equals(currentUserId),
                Integer.valueOf(1).equals(first.getIsFollowing()),
                Integer.valueOf(1).equals(first.getFollowingMe()));
    }

    private static @NonNull List<ProfilePlaylist> formatPlaylists(List<ProfileRow> rows) {
        List<ProfilePlaylist> playlists = new ArrayList<>(rows.size());

        for (ProfileRow row : rows) {
            if (row.getPlaylistId() == null) {
                continue; // user has no visible playlists
            }

            playlists.add(
                    new ProfilePlaylist(
                            new CorePlaylist(
                                    row.getPlaylistId(),
                                    row.getPlaylistName(),
                                    row.getPlaylistCoverUrl()
                            ),
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
                   u.banner_url AS bannerUrl,
                   pd.display_name AS displayName,
                   pd.country AS country,
                   pd.website AS website,
                   pd.about AS about,
                            EXISTS (SELECT 1 FROM follows f1
                                    WHERE f1.follower_user_id = CAST(:currentUserId AS varchar) AND f1.following_user_id = u.user_id) AS isFollowing,
                   EXISTS (SELECT 1 FROM follows f2
                           WHERE f2.follower_user_id = u.user_id AND f2.following_user_id = CAST(:currentUserId AS varchar)) AS followingMe,
                   p.playlist_id AS playlistId,
                   p.playlist_name AS playlistName,
                   p.playlist_cover_url AS playlistCoverUrl,
                   (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.playlist_id) AS totalPieces
            FROM users u
            LEFT JOIN profile_details pd ON pd.user_id = u.user_id
            LEFT JOIN playlists p ON p.owner_id = u.user_id
                                 AND (p.is_public = true OR u.user_id = CAST(:currentUserId AS varchar))
            WHERE u.username = :username COLLATE NOCASE
            """, nativeQuery = true)
    List<ProfileRow> findProfileRows(@Param("username") String username, @Param("currentUserId") String currentUserId);

    interface ProfileRow {
        String getUserId();
        String getUsername();
        String getPfpUrl();
        String getBannerUrl();
        String getDisplayName();
        Country getCountry();
        String getWebsite();
        String getAbout();
        Integer getIsFollowing(); // SQLite has no boolean type: 1 or 0
        Integer getFollowingMe(); // SQLite has no boolean type: 1 or 0
        String getPlaylistId();
        String getPlaylistName();
        String getPlaylistCoverUrl();
        int getTotalPieces();
    }
}
