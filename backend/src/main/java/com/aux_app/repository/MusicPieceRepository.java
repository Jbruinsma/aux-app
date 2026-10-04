package com.aux_app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.MusicPieceEntity;

public interface MusicPieceRepository extends JpaRepository<MusicPieceEntity, String> {

    @Query("select coalesce(sum(m.sizeBytes), 0) from MusicPieceEntity m where m.uploaderUserId = :userId")
    long totalSizeBytes(@Param("userId") String userId);

    // Search by piece or artist name. `pattern` is a LIKE pattern (caller escapes %, _ and \\).
    // Private pieces only show up for their uploader (`userId` may be null).
    @Query(value = """
            SELECT mp.music_piece_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.artist_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl
            FROM music_pieces mp
            JOIN artists a ON a.artist_id = mp.artist_id
            WHERE (mp.is_public = 1 OR mp.uploader_user_id = :userId)
              AND (mp.name LIKE :pattern ESCAPE '\\' OR a.artist_name LIKE :pattern ESCAPE '\\')
            ORDER BY mp.name COLLATE NOCASE
            LIMIT :limit OFFSET :offset
            """, nativeQuery = true)
    List<MusicPieceSearchRow> searchMusicPieces(
            @Param("pattern") String pattern, @Param("userId") String userId,
            @Param("limit") int limit, @Param("offset") int offset);

    @Query(value = """
            SELECT COUNT(*) FROM music_pieces mp
            JOIN artists a ON a.artist_id = mp.artist_id
            WHERE (mp.is_public = 1 OR mp.uploader_user_id = :userId)
              AND (mp.name LIKE :pattern ESCAPE '\\' OR a.artist_name LIKE :pattern ESCAPE '\\')
            """, nativeQuery = true)
    int countSearchMusicPieces(@Param("pattern") String pattern, @Param("userId") String userId);

    interface MusicPieceSearchRow {
        String getMusicPieceId();
        String getName();
        String getCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
    }

}
