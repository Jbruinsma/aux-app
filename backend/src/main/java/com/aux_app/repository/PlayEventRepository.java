package com.aux_app.repository;

import com.aux_app.entity.PlayEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlayEventRepository extends JpaRepository<PlayEventEntity, String> {

    // Null if the user has never played anything
    PlayEventEntity findFirstByUserIdOrderByPlayedAtDesc(String userId);

    // The user's latest play of a piece they can still play, with its artist, favorite flag and playlist, in one
    // query. Pieces made private since are skipped, so their titles don't leak. `playlistId` is null when there was
    // no playlist or the user can no longer see it (made private, or they were removed). Null if nothing qualifies.
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   CASE WHEN f.user_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
                   CASE WHEN p.is_public = 1 OR p.owner_id = :userId OR m.status = 'ACCEPTED'
                        THEN p.public_id END AS playlistId
            FROM play_events pe
            JOIN music_pieces mp ON mp.music_piece_id = pe.music_piece_id
            JOIN artists a ON a.artist_id = mp.artist_id
            LEFT JOIN user_favorite_tracks f ON f.user_id = pe.user_id AND f.music_piece_id = mp.music_piece_id
            LEFT JOIN playlists p ON p.playlist_id = pe.context_playlist_id
            LEFT JOIN playlist_members m ON m.playlist_id = p.playlist_id AND m.user_id = pe.user_id
            WHERE pe.user_id = :userId
              AND (mp.is_public = 1 OR mp.uploader_user_id = :userId)
            ORDER BY pe.played_at DESC
            LIMIT 1
            """, nativeQuery = true)
    LastPlaybackRow findLastPlayback(@Param("userId") String userId);

    interface LastPlaybackRow {
        String getMusicPieceId();
        String getName();
        String getCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        Integer getIsFavorite(); // SQLite has no boolean type: 1 or 0
        String getPlaylistId();
    }
}
