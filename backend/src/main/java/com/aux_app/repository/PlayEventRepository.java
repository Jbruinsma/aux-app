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
                   CAST(mp.is_public AS INTEGER) AS isPublic, CASE WHEN f.user_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
                   COALESCE(CASE WHEN p.is_public = 1 OR p.owner_id = :userId OR m.status = 'ACCEPTED'
                        THEN p.public_id END, '') AS playlistId, -- '' not NULL: see TagRepository.PIECE_TAGS
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
                   CAST(mp.is_public AS INTEGER) AS isPublic, CASE WHEN f.user_id IS NOT NULL THEN 1 ELSE 0 END AS isFavorite,
                   COALESCE(CASE WHEN p.is_public = 1 OR p.owner_id = :userId OR m.status = 'ACCEPTED'
                        THEN p.public_id END, '') AS playlistId, -- '' not NULL: see TagRepository.PIECE_TAGS
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
        Integer getIsPublic(); // SQLite has no boolean type: 1 or 0
        Integer getIsFavorite(); // 1 or 0
        String getPlaylistId(); // '' when none
        String getTags(); // TagRepository.PIECE_TAGS; read with TagRepository.split
    }

    // Counted plays that shape taste (CompatibilityService): public pieces only, so a private upload never shows up as
    // a shared artist, and never the user's own uploads. Users who haven't finished onboarding are left out. `w` is
    // the play's weight, halving every :halfLifeMillis. :userA null means every user; otherwise only :userA and :userB.
    // played_at is millis when the app writes it, text in seeded rows, so both are read.
    String TASTE_PLAYS = """
            WITH plays AS (
                SELECT pe.user_id, mp.music_piece_id, mp.artist_id,
                       power(0.5, (:now - (CASE typeof(pe.played_at) WHEN 'integer' THEN pe.played_at
                                           ELSE unixepoch(pe.played_at) * 1000 END)) / :halfLifeMillis) AS w
                FROM play_events pe
                JOIN music_pieces mp ON mp.music_piece_id = pe.music_piece_id
                JOIN users u ON u.user_id = pe.user_id
                WHERE u.onboarding_step = 'DONE' AND mp.is_public = 1 AND mp.uploader_user_id <> pe.user_id
                  AND (:userA IS NULL OR pe.user_id IN (:userA, :userB))
            )
            """;

    // One row per user and artist: log(1 + decayed plays) + 2 if a favorite artist + 0.5 per favorite track of theirs.
    // Favorites count even without plays.
    String ARTIST_WEIGHTS = TASTE_PLAYS + """
            , artist_plays AS (
                SELECT user_id, artist_id, SUM(w) AS plays FROM plays GROUP BY user_id, artist_id
            ),
            fav_tracks AS (
                SELECT f.user_id, mp.artist_id, COUNT(*) AS n
                FROM user_favorite_tracks f
                JOIN music_pieces mp ON mp.music_piece_id = f.music_piece_id
                WHERE mp.is_public = 1 AND mp.uploader_user_id <> f.user_id
                GROUP BY f.user_id, mp.artist_id
            ),
            taste AS (
                SELECT user_id, artist_id FROM artist_plays
                UNION SELECT user_id, artist_id FROM user_favorite_artists
                UNION SELECT user_id, artist_id FROM fav_tracks
            )
            SELECT t.user_id AS userId, a.public_id AS artistId, a.artist_name AS artistName,
                   a.artist_pfp_url AS artistPfpUrl,
                   ln(1 + COALESCE(ap.plays, 0)) + 2 * (fa.user_id IS NOT NULL) + 0.5 * COALESCE(ft.n, 0) AS weight
            FROM taste t
            JOIN users u ON u.user_id = t.user_id AND u.onboarding_step = 'DONE'
            JOIN artists a ON a.artist_id = t.artist_id
            LEFT JOIN artist_plays ap ON ap.user_id = t.user_id AND ap.artist_id = t.artist_id
            LEFT JOIN user_favorite_artists fa ON fa.user_id = t.user_id AND fa.artist_id = t.artist_id
            LEFT JOIN fav_tracks ft ON ft.user_id = t.user_id AND ft.artist_id = t.artist_id
            WHERE :userA IS NULL OR t.user_id IN (:userA, :userB)
            """;

    // One row per user and curated tag: log(1 + decayed plays of pieces carrying it). User-made tags are ignored.
    String TAG_WEIGHTS = TASTE_PLAYS + """
            SELECT p.user_id AS userId, t.tag AS tag, ln(1 + SUM(p.w)) AS weight
            FROM plays p
            JOIN music_piece_tags mpt ON mpt.music_piece_id = p.music_piece_id
            JOIN tags t ON t.tag_id = mpt.tag_id AND t.is_curated = 1
            GROUP BY p.user_id, t.tag_id
            """;

    @Query(value = ARTIST_WEIGHTS, nativeQuery = true)
    List<ArtistWeightRow> findArtistWeights(@Param("now") long now, @Param("halfLifeMillis") double halfLifeMillis,
                                            @Param("userA") String userA, @Param("userB") String userB);

    @Query(value = TAG_WEIGHTS, nativeQuery = true)
    List<TagWeightRow> findTagWeights(@Param("now") long now, @Param("halfLifeMillis") double halfLifeMillis,
                                      @Param("userA") String userA, @Param("userB") String userB);

    interface ArtistWeightRow {
        String getUserId(); // internal id
        String getArtistId(); // public id
        String getArtistName();
        String getArtistPfpUrl();
        Double getWeight();
    }

    interface TagWeightRow {
        String getUserId(); // internal id
        String getTag();
        Double getWeight();
    }
}
