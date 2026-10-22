package com.aux_app.repository;

import com.aux_app.entity.PlayEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlayEventRepository extends JpaRepository<PlayEventEntity, String> {

    // Null if the user has never played anything
    PlayEventEntity findFirstByUserIdOrderByPlayedAtDesc(String userId);

    // The user's latest play of a piece they can still play, with its artist, favorite flag and playlist, in one
    // query. Pieces they can't play anymore are skipped (PlaybackEventService.isPlayable): a private piece counts
    // only if they uploaded it, or played it from a private playlist they and its uploader still collaborate on. `playlistId` is null when there was
    // no playlist or the user can no longer see it (made private, or they were removed). Null if nothing qualifies.
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   u.public_id AS uploaderId, u.username AS uploaderUsername, u.profile_picture_url AS uploaderPfpUrl,
                   CASE WHEN f.user_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
                   CASE WHEN p.is_public = 1 OR p.owner_id = :userId OR m.status = 'ACCEPTED'
                        THEN p.public_id END AS playlistId,
            """ + TagRepository.PIECE_TAGS + """
             AS tags
            FROM play_events pe
            JOIN music_pieces mp ON mp.music_piece_id = pe.music_piece_id
            JOIN artists a ON a.artist_id = mp.artist_id
            JOIN users u ON u.user_id = mp.uploader_user_id
            LEFT JOIN user_favorite_tracks f ON f.user_id = pe.user_id AND f.music_piece_id = mp.music_piece_id
            LEFT JOIN playlists p ON p.playlist_id = pe.context_playlist_id
            LEFT JOIN playlist_members m ON m.playlist_id = p.playlist_id AND m.user_id = pe.user_id
            WHERE pe.user_id = :userId
              AND (mp.is_public = 1 OR mp.uploader_user_id = :userId
                   OR (COALESCE(p.is_public, 0) = 0 AND (p.owner_id = :userId OR m.status = 'ACCEPTED')
                       AND (mp.uploader_user_id = p.owner_id OR EXISTS (
                           SELECT 1 FROM playlist_members um WHERE um.playlist_id = p.playlist_id
                             AND um.user_id = mp.uploader_user_id AND um.status = 'ACCEPTED'))))
            ORDER BY pe.played_at DESC
            LIMIT 1
            """, nativeQuery = true)
    LastPlaybackRow findLastPlayback(@Param("userId") String userId);

    // The user's 20 most recently played distinct pieces they can still play, newest first, one row per piece from its
    // latest play. Same visibility rules as findLastPlayback.
    @Query(value = """
            WITH latest AS (
                SELECT pe.music_piece_id, pe.context_playlist_id, pe.played_at,
                       ROW_NUMBER() OVER (PARTITION BY pe.music_piece_id ORDER BY pe.played_at DESC) AS rn
                FROM play_events pe
                WHERE pe.user_id = :userId
            )
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   u.public_id AS uploaderId, u.username AS uploaderUsername, u.profile_picture_url AS uploaderPfpUrl,
                   CASE WHEN f.user_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
                   CASE WHEN p.is_public = 1 OR p.owner_id = :userId OR m.status = 'ACCEPTED'
                        THEN p.public_id END AS playlistId,
            """ + TagRepository.PIECE_TAGS + """
             AS tags
            FROM latest l
            JOIN music_pieces mp ON mp.music_piece_id = l.music_piece_id
            JOIN artists a ON a.artist_id = mp.artist_id
            JOIN users u ON u.user_id = mp.uploader_user_id
            LEFT JOIN user_favorite_tracks f ON f.user_id = :userId AND f.music_piece_id = mp.music_piece_id
            LEFT JOIN playlists p ON p.playlist_id = l.context_playlist_id
            LEFT JOIN playlist_members m ON m.playlist_id = p.playlist_id AND m.user_id = :userId
            WHERE l.rn = 1
              AND (mp.is_public = 1 OR mp.uploader_user_id = :userId
                   OR (COALESCE(p.is_public, 0) = 0 AND (p.owner_id = :userId OR m.status = 'ACCEPTED')
                       AND (mp.uploader_user_id = p.owner_id OR EXISTS (
                           SELECT 1 FROM playlist_members um WHERE um.playlist_id = p.playlist_id
                             AND um.user_id = mp.uploader_user_id AND um.status = 'ACCEPTED'))))
            ORDER BY l.played_at DESC
            LIMIT 20
            """, nativeQuery = true)
    List<LastPlaybackRow> findRecentPlaybacks(@Param("userId") String userId);

    interface LastPlaybackRow {
        String getMusicPieceId();
        String getName();
        String getCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        String getUploaderId();
        String getUploaderUsername();
        String getUploaderPfpUrl();
        Integer getIsFavorite(); // SQLite has no boolean type: 1 or 0
        String getPlaylistId();
        String getTags(); // TagRepository.PIECE_TAGS; read with TagRepository.split
    }
}
