package com.aux_app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.MusicPieceEntity;

public interface MusicPieceRepository extends JpaRepository<MusicPieceEntity, String> {

    MusicPieceEntity findByPublicId(String publicId);

    @Query("select coalesce(sum(m.sizeBytes), 0) from MusicPieceEntity m where m.uploaderUserId = :userId")
    long totalSizeBytes(@Param("userId") String userId);

    // Everything the user uploaded, public and private, newest first. `userId` is the internal user id;
    // `isFavorite` is whether that same user favorited the piece.
    default List<MusicPieceOverview> findUploads(String userId) {
        return findUploadRows(userId).stream().map(MusicPieceRepository::toOverview).toList();
    }

    @Query(value = OVERVIEW_COLUMNS + """
            WHERE mp.uploader_user_id = :userId
            ORDER BY mp.created_at DESC
            """, nativeQuery = true)
    List<MusicPieceOverviewRow> findUploadRows(@Param("userId") String userId);

    // Search by piece or artist name. `pattern` is a LIKE pattern (caller escapes %, _ and \\).
    // Artist names are matched once in the subquery, not by joining every piece to its artist first.
    // Private pieces only show up for their uploader (`userId` may be null).
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   """ + TagRepository.PIECE_TAGS + """
             AS tags
            FROM music_pieces mp
            JOIN artists a ON a.artist_id = mp.artist_id
            WHERE (mp.is_public = 1 OR mp.uploader_user_id = :userId)
              AND (mp.name LIKE :pattern ESCAPE '\\'
                   OR mp.artist_id IN (SELECT artist_id FROM artists WHERE artist_name LIKE :pattern ESCAPE '\\'))
            ORDER BY mp.name COLLATE NOCASE
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<MusicPieceSearchRow> searchMusicPieces(
            @Param("pattern") String pattern, @Param("userId") String userId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*) FROM music_pieces mp
            WHERE (mp.is_public = 1 OR mp.uploader_user_id = :userId)
              AND (mp.name LIKE :pattern ESCAPE '\\'
                   OR mp.artist_id IN (SELECT artist_id FROM artists WHERE artist_name LIKE :pattern ESCAPE '\\'))
            """, nativeQuery = true)
    int countSearchMusicPieces(@Param("pattern") String pattern, @Param("userId") String userId);

    // Track-list view of the given pieces. `userId` is the internal user id, for `isFavorite`.
    // Rows come back in no particular order; reorder them yourself. Only pass pieces the user can play:
    // every row is marked available.
    default List<MusicPieceOverview> findOverviews(List<String> publicIds, String userId) {
        if (publicIds.isEmpty()) {
            return List.of();
        }
        return findOverviewRows(publicIds, userId).stream().map(MusicPieceRepository::toOverview).toList();
    }

    private static MusicPieceOverview toOverview(MusicPieceOverviewRow row) {
        return new MusicPieceOverview(
                row.getMusicPieceId(),
                row.getName(),
                row.getCoverUrl(),
                new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()),
                new PlaylistOwner(row.getUploaderId(), row.getUploaderPfpUrl(), row.getUploaderUsername()),
                TagRepository.split(row.getTags()),
                Integer.valueOf(1).equals(row.getIsPublic()),
                Integer.valueOf(1).equals(row.getIsFavorite()),
                true);
    }

    // Shared by findOverviewRows and findUploadRows; `userId` is for isFavorite. Append the WHERE clause
    String OVERVIEW_COLUMNS = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   u.public_id AS uploaderId, u.username AS uploaderUsername, u.profile_picture_url AS uploaderPfpUrl,
                   CAST(mp.is_public AS INTEGER) AS isPublic, (uft.user_id IS NOT NULL) AS isFavorite,
            """ + TagRepository.PIECE_TAGS + """
             AS tags
            FROM music_pieces mp
            JOIN artists a ON a.artist_id = mp.artist_id
            JOIN users u ON u.user_id = mp.uploader_user_id
            LEFT JOIN user_favorite_tracks uft ON uft.music_piece_id = mp.music_piece_id AND uft.user_id = :userId
            """;

    @Query(value = OVERVIEW_COLUMNS + """
            WHERE mp.public_id IN (:publicIds)
            """, nativeQuery = true)
    List<MusicPieceOverviewRow> findOverviewRows(@Param("publicIds") List<String> publicIds, @Param("userId") String userId);

    // Pieces credited to the artist (internal id) that `userId` can play on their own: public ones, plus their own
    // private ones (`userId` may be null). Most played first, all time, newest as the tie-break.
    default List<MusicPieceOverview> findArtistPieces(String artistId, String userId, int limit, int offset) {
        return findArtistPieceRows(artistId, userId, limit, offset).stream().map(MusicPieceRepository::toOverview).toList();
    }

    @Query(value = OVERVIEW_COLUMNS + """
            WHERE mp.artist_id = :artistId AND (mp.is_public = 1 OR mp.uploader_user_id = :userId)
            -- Counted per piece through idx_play_events_music_piece_played_at; a GROUP BY over play_events would
            -- read every play in the app on each page
            ORDER BY (SELECT COUNT(*) FROM play_events pe WHERE pe.music_piece_id = mp.music_piece_id) DESC,
                     mp.created_at DESC, mp.music_piece_id
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<MusicPieceOverviewRow> findArtistPieceRows(@Param("artistId") String artistId, @Param("userId") String userId,
                                                    @Param("limit") int limit, @Param("offset") int offset);

    // Public ids of pieces like the seed, best first; see RecommendationService.discover for the rules.
    // Score: 3 per shared seed tag, 1 per other shared tag from the seed artist's top 5, plus 1 per listener
    // (up to 4) among the seed's recent listeners who also played the piece. Equal scores come back in random order.
    // Pieces scoring 0 only fill in when they were played in the window, most played first.
    // Since-times are epoch millis. The app writes played_at as millis; seeded rows hold text, so both are read.
    String DISCOVER = """
            WITH seed_tags AS (
                SELECT tag_id FROM music_piece_tags WHERE music_piece_id = :seedId
            ),
            artist_tags AS (
                SELECT mpt.tag_id FROM music_piece_tags mpt
                JOIN music_pieces mp ON mp.music_piece_id = mpt.music_piece_id
                WHERE mp.artist_id = :artistId AND mp.is_public = 1
                GROUP BY mpt.tag_id
                ORDER BY COUNT(*) DESC, mpt.tag_id
                LIMIT 5
            ),
            tag_scores AS (
                SELECT music_piece_id,
                       3 * SUM(tag_id IN (SELECT tag_id FROM seed_tags))
                         + SUM(tag_id NOT IN (SELECT tag_id FROM seed_tags)) AS score
                FROM music_piece_tags
                WHERE tag_id IN (SELECT tag_id FROM seed_tags UNION SELECT tag_id FROM artist_tags)
                GROUP BY music_piece_id
            ),
            -- Listeners from the latest 500 plays of the seed: a hit would otherwise pull in every play of most users
            co_listens AS (
                SELECT pe.music_piece_id, MIN(COUNT(DISTINCT pe.user_id), 4) AS score
                FROM play_events pe
                WHERE pe.user_id IN (
                    SELECT user_id FROM (
                        SELECT user_id FROM play_events WHERE music_piece_id = :seedId
                        ORDER BY played_at DESC LIMIT 500)
                    WHERE user_id <> :userId)
                GROUP BY pe.music_piece_id
            ),
            popular AS (
                SELECT music_piece_id, COUNT(*) AS plays FROM play_events
                WHERE (CASE typeof(played_at) WHEN 'integer' THEN played_at ELSE unixepoch(played_at) * 1000 END)
                      >= :popularSince
                GROUP BY music_piece_id
            ),
            -- Read once here instead of once per candidate
            recent AS (
                SELECT music_piece_id FROM play_events
                WHERE user_id = :userId
                  AND (CASE typeof(played_at) WHEN 'integer' THEN played_at ELSE unixepoch(played_at) * 1000 END)
                      >= :recentSince
            ),
            -- Only pieces something above scored, not every piece in the app
            candidates AS (
                SELECT mp.public_id, mp.artist_id,
                       COALESCE(ts.score, 0) + COALESCE(cl.score, 0) AS score,
                       COALESCE(pop.plays, 0) AS plays
                FROM (SELECT music_piece_id FROM tag_scores UNION SELECT music_piece_id FROM co_listens
                      UNION SELECT music_piece_id FROM popular) c
                JOIN music_pieces mp ON mp.music_piece_id = c.music_piece_id
                LEFT JOIN tag_scores ts ON ts.music_piece_id = mp.music_piece_id
                LEFT JOIN co_listens cl ON cl.music_piece_id = mp.music_piece_id
                LEFT JOIN popular pop ON pop.music_piece_id = mp.music_piece_id
                WHERE mp.is_public = 1
                  AND mp.music_piece_id <> :seedId
                  AND mp.uploader_user_id <> :userId
                  AND NOT EXISTS (SELECT 1 FROM user_favorite_tracks f
                                  WHERE f.user_id = :userId AND f.music_piece_id = mp.music_piece_id)
                  AND mp.music_piece_id NOT IN (SELECT music_piece_id FROM recent)
            ),
            ranked AS (
                SELECT public_id, score, plays, random() AS tiebreak,
                       ROW_NUMBER() OVER (PARTITION BY artist_id = :artistId ORDER BY score DESC, plays DESC, random())
                           AS artist_rank,
                       artist_id = :artistId AS same_artist
                FROM candidates
            )
            SELECT public_id FROM ranked
            WHERE NOT same_artist OR artist_rank <= :sameArtistCap
            ORDER BY score DESC, CASE WHEN score = 0 THEN plays END DESC, tiebreak
            LIMIT :limit
            """;

    @Query(value = DISCOVER, nativeQuery = true)
    List<String> findDiscoverIds(
            @Param("seedId") String seedId, @Param("artistId") String artistId, @Param("userId") String userId,
            @Param("recentSince") long recentSince, @Param("popularSince") long popularSince,
            @Param("sameArtistCap") int sameArtistCap, @Param("limit") int limit);

    interface MusicPieceOverviewRow extends MusicPieceSearchRow {
        String getUploaderId();
        String getUploaderUsername();
        String getUploaderPfpUrl();
        Integer getIsFavorite(); // SQLite has no boolean type: 1 or 0
        Integer getIsPublic();
    }

    interface MusicPieceSearchRow {
        String getMusicPieceId();
        String getName();
        String getCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        String getTags(); // TagRepository.PIECE_TAGS; read with TagRepository.split
    }

}
