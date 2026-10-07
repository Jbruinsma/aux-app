package com.aux_app.repository;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.playlist.PlaylistEditor;
import com.aux_app.dto.playlist.PlaylistMemberStatus;
import com.aux_app.dto.playlist.PlaylistPermission;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.PlaylistMemberEntity;
import com.aux_app.entity.UserEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

public interface PlaylistRepository extends JpaRepository<PlaylistEntity, String> {

    // Callers pass the public id from the URL; the API never sees playlist UUIDs
    PlaylistEntity findByPublicId(String publicId);

    // Null if playlist doesn't exist or user hasn't saved it.
    @Query("""
            SELECT p FROM PlaylistEntity p
            WHERE p.publicId = :publicId
              AND EXISTS (SELECT 1 FROM UserSavedPlaylistEntity s
                          WHERE s.playlistId = p.playlistId AND s.userId = :userId)
            """)
    PlaylistEntity findSavedPlaylist(@Param("publicId") String publicId, @Param("userId") String userId);

    // Null if playlist doesn't exist. Guard before use. Ids in the result are public ids.
    default PlaylistPage findPlaylistWithTracks(String publicId, String userId) {
        List<PlaylistWithTracksRow> rows = findPlaylistPage(publicId, userId);
        if (rows.isEmpty()) {
            return null;
        }
        PlaylistWithTracksRow first = rows.get(0);
        List<MusicPieceOverview> pieces = createPieces(rows);
        return new PlaylistPage(
                first.getPlaylistId(),
                first.getPlaylistCoverUrl(),
                first.getPlaylistName(),
                Integer.valueOf(1).equals(first.getIsPublic()),
                new PlaylistOwner(first.getOwnerId(), first.getOwnerPfpUrl(), first.getOwnerUsername()),
                Integer.valueOf(1).equals(first.getIsSaved()),
                pieces,
                findEditors(publicId),
                first.getCallerStatus() == null ? null : PlaylistMemberStatus.valueOf(first.getCallerStatus()),
                first.getCallerPermission() == null ? null : PlaylistPermission.valueOf(first.getCallerPermission()));
    }

    // Accepted EDITOR members only, oldest invite first.
    @Query("""
            SELECT new com.aux_app.dto.playlist.PlaylistEditor(u.publicId, u.username, u.profilePictureUrl)
            FROM PlaylistMemberEntity m
            JOIN PlaylistEntity p ON p.playlistId = m.playlistId
            JOIN UserEntity u ON u.userId = m.userId
            WHERE p.publicId = :publicId AND m.status = com.aux_app.dto.playlist.PlaylistMemberStatus.ACCEPTED
              AND m.permission = com.aux_app.dto.playlist.PlaylistPermission.EDITOR
            ORDER BY m.invitedAt
            """)
    List<PlaylistEditor> findEditors(@Param("publicId") String publicId);

    private static @NonNull List<MusicPieceOverview> createPieces(List<PlaylistWithTracksRow> rows) {
        List<MusicPieceOverview> pieces = new ArrayList<>(rows.size());
        for (PlaylistWithTracksRow row : rows) {
            if (row.getMusicPieceId() == null) {
                continue;
            }
            pieces.add(new MusicPieceOverview(
                    row.getMusicPieceId(),
                    row.getPieceName(),
                    row.getPieceCoverUrl(),
                    new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()),
                    Integer.valueOf(1).equals(row.getIsFavorite())));
        }
        return pieces;
    }

    @Query(value = """
            SELECT p.public_id AS playlistId,
                   u.public_id AS ownerId,
                   CAST(p.is_public AS INTEGER) AS isPublic,
                   p.playlist_cover_url AS playlistCoverUrl,
                   p.playlist_name AS playlistName,
                   u.username AS ownerUsername,
                   u.profile_picture_url AS ownerPfpUrl,
                   (usp.user_id IS NOT NULL) AS isSaved,
                   mp.public_id AS musicPieceId,
                   pt.playlist_position AS playlistPosition,
                   mp.name AS pieceName,
                   mp.cover_url AS pieceCoverUrl,
                   a.public_id AS artistId,
                   a.artist_name AS artistName,
                   a.artist_pfp_url AS artistPfpUrl,
                   (uft.user_id IS NOT NULL) AS isFavorite,
                   pm.status AS callerStatus,
                   pm.permission AS callerPermission
            FROM playlists p
            JOIN users u ON u.user_id = p.owner_id
            LEFT JOIN user_saved_playlists usp ON usp.playlist_id = p.playlist_id AND usp.user_id = :userId
            LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.playlist_id
            LEFT JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            LEFT JOIN artists a ON a.artist_id = mp.artist_id
            LEFT JOIN playlist_members pm ON pm.playlist_id = p.playlist_id AND pm.user_id = :userId
            LEFT JOIN user_favorite_tracks uft ON uft.music_piece_id = mp.music_piece_id AND uft.user_id = :userId
            WHERE p.public_id = :publicId
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<PlaylistWithTracksRow> findPlaylistPage(@Param("publicId") String publicId, @Param("userId") String userId);

    interface PlaylistSearchRow {
        String getPlaylistId();
        String getPlaylistName();
        String getPlaylistCoverUrl();
        String getOwnerUsername();
        Integer getPieceCount();
    }

    interface PlaylistWithTracksRow {
        String getPlaylistId();
        String getOwnerId();
        Integer getIsPublic(); // SQLite has no boolean type: 1 or 0
        String getPlaylistCoverUrl();
        String getPlaylistName();
        String getOwnerUsername();
        String getOwnerPfpUrl();
        Integer getIsSaved(); // SQLite has no boolean type: 1 or 0
        String getMusicPieceId();
        Integer getPlaylistPosition();
        String getPieceName();
        String getPieceCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        Integer getIsFavorite(); // SQLite has no boolean type: 1 or 0
        String getCallerStatus(); // caller's playlist_members row; null if they have none
        String getCallerPermission();
    }

    record PlaylistPage(
            String playlistId,
            String playlistCoverUrl,
            String playlistName,
            boolean isPublic,
            PlaylistOwner owner,
            boolean isSaved,
            List<MusicPieceOverview> pieces,
            List<PlaylistEditor> editors,
            PlaylistMemberStatus callerStatus, // null if the caller has no member row
            PlaylistPermission callerPermission
    ) {}

    // Null if playlist doesn't exist. `member` is the caller's own row, null if they have none.
    @Query("""
            SELECT new com.aux_app.repository.PlaylistRepository$PlaylistWithCaller(p, m)
            FROM PlaylistEntity p
            LEFT JOIN PlaylistMemberEntity m ON m.playlistId = p.playlistId AND m.userId = :userId
            WHERE p.publicId = :publicId
            """)
    PlaylistWithCaller findPlaylistWithCaller(@Param("publicId") String publicId, @Param("userId") String userId);

    record PlaylistWithCaller(PlaylistEntity playlist, PlaylistMemberEntity member) {

        public boolean isOwner(String userId) { return playlist.getOwnerId().equals(userId); }

        // Only ACCEPTED rows grant access; PENDING is an unanswered invite.
        public boolean isEditor() {
            return member != null && member.getStatus() == PlaylistMemberStatus.ACCEPTED
                    && member.getPermission() == PlaylistPermission.EDITOR;
        }

        public boolean isAcceptedMember() {
            return member != null && member.getStatus() == PlaylistMemberStatus.ACCEPTED;
        }
    }

    // Null if playlist doesn't exist. `invitee` is null if no user has that public id;
    // `member` is null if the invitee has no row (not invited yet).
    @Query("""
            SELECT new com.aux_app.repository.PlaylistRepository$PlaylistWithMember(p, u, m)
            FROM PlaylistEntity p
            LEFT JOIN UserEntity u ON u.publicId = :inviteePublicId
            LEFT JOIN PlaylistMemberEntity m ON m.playlistId = p.playlistId AND m.userId = u.userId
            WHERE p.publicId = :playlistPublicId
            """)
    PlaylistWithMember findPlaylistWithMember(
            @Param("playlistPublicId") String playlistPublicId, @Param("inviteePublicId") String inviteePublicId);

    record PlaylistWithMember(PlaylistEntity playlist, UserEntity invitee, PlaylistMemberEntity member) {}

    // Search by name. `pattern` is a LIKE pattern (caller escapes %, _ and \\).
    // Private playlists only show up for their owner (`userId` may be null).
    @Query(value = """
            SELECT p.public_id AS playlistId, p.playlist_name AS playlistName, p.playlist_cover_url AS playlistCoverUrl,
                   o.username AS ownerUsername,
                   (SELECT COUNT(*) FROM playlist_tracks pt WHERE pt.playlist_id = p.playlist_id) AS pieceCount
            FROM playlists p
            JOIN users o ON o.user_id = p.owner_id
            WHERE (p.is_public = 1 OR p.owner_id = :userId) AND p.playlist_name LIKE :pattern ESCAPE '\\'
            ORDER BY p.playlist_name COLLATE NOCASE
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<PlaylistSearchRow> searchPlaylists(
            @Param("pattern") String pattern, @Param("userId") String userId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*) FROM playlists p
            WHERE (p.is_public = 1 OR p.owner_id = :userId) AND p.playlist_name LIKE :pattern ESCAPE '\\'
            """, nativeQuery = true)
    int countSearchPlaylists(@Param("pattern") String pattern, @Param("userId") String userId);

}
