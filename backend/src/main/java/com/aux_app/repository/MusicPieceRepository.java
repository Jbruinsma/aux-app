package com.aux_app.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.MusicPieceEntity;

public interface MusicPieceRepository extends JpaRepository<MusicPieceEntity, String> {

    MusicPieceEntity findByPublicId(String publicId);

    @Query("select coalesce(sum(m.sizeBytes), 0) from MusicPieceEntity m where m.uploaderUserId = :userId")
    long totalSizeBytes(@Param("userId") String userId);

    // Everything the user uploaded, public and private, newest first. `userId` is the internal user id;
    // `isFavorite` is whether that same user favorited the piece.
    @Query("""
            SELECT new com.aux_app.repository.MusicPieceRepository$MusicPieceWithArtist(
                mp, a, CASE WHEN f.userId IS NOT NULL THEN true ELSE false END)
            FROM MusicPieceEntity mp
            JOIN ArtistEntity a ON a.artistId = mp.artistId
            LEFT JOIN UserFavoriteTrackEntity f ON f.musicPieceId = mp.musicPieceId AND f.userId = :userId
            WHERE mp.uploaderUserId = :userId
            ORDER BY mp.createdAt DESC
            """)
    List<MusicPieceWithArtist> findUploadsWithArtist(@Param("userId") String userId);

    record MusicPieceWithArtist(MusicPieceEntity piece, ArtistEntity artist, boolean isFavorite) {}

    // Search by piece or artist name. `pattern` is a LIKE pattern (caller escapes %, _ and \\).
    // Private pieces only show up for their uploader (`userId` may be null).
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl
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
