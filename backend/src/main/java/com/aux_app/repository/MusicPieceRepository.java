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
    // Private pieces only show up for their uploader (`userId` may be null).
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   """ + TagRepository.PIECE_TAGS + """
             AS tags
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
                Integer.valueOf(1).equals(row.getIsFavorite()),
                true);
    }

    // Shared by findOverviewRows and findUploadRows; `userId` is for isFavorite. Append the WHERE clause
    String OVERVIEW_COLUMNS = """
            SELECT mp.public_id AS musicPieceId, mp.name AS name, mp.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   u.public_id AS uploaderId, u.username AS uploaderUsername, u.profile_picture_url AS uploaderPfpUrl,
                   (uft.user_id IS NOT NULL) AS isFavorite,
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

    interface MusicPieceOverviewRow extends MusicPieceSearchRow {
        String getUploaderId();
        String getUploaderUsername();
        String getUploaderPfpUrl();
        Integer getIsFavorite(); // SQLite has no boolean type: 1 or 0
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
