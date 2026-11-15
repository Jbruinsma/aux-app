package com.aux_app.repository;

import com.aux_app.dto.album.AlbumOverview;
import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.entity.AlbumEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlbumRepository extends JpaRepository<AlbumEntity, String> {

    // Callers pass the public id from the request; the API never sees album UUIDs
    AlbumEntity findByPublicId(String publicId);

    // Matches the uq_albums_artist_name_nocase index, so pass a stripped name. `artistId` is the internal id
    AlbumEntity findByArtistIdAndAlbumNameIgnoreCase(String artistId, String albumName);

    boolean existsByCoverUrl(String coverUrl);

    // Pieces `userId` can play on their own (public, or their own private ones); `userId` may be null
    @Query(value = """
            SELECT COUNT(*) FROM music_pieces mp
            WHERE mp.album_id = :albumId AND (mp.is_public = 1 OR mp.uploader_user_id = :userId)
            """, nativeQuery = true)
    int countPlayablePieces(@Param("albumId") String albumId, @Param("userId") String userId);

    // Pieces using the album's cover follow it; pieces with their own cover keep theirs
    @Modifying
    @Query(value = "UPDATE music_pieces SET cover_url = :coverUrl WHERE album_id = :albumId AND uses_album_cover = 1",
            nativeQuery = true)
    int syncPieceCovers(@Param("albumId") String albumId, @Param("coverUrl") String coverUrl);

    // Pieces keep the cover they show: a piece that used the album's cover now holds it as its own
    @Modifying
    @Query(value = "UPDATE music_pieces SET album_id = NULL, uses_album_cover = 0 WHERE album_id = :albumId",
            nativeQuery = true)
    int detachPieces(@Param("albumId") String albumId);

    // Internal ids of everyone with a piece in the album, for live refreshes
    @Query(value = "SELECT DISTINCT uploader_user_id FROM music_pieces WHERE album_id = :albumId", nativeQuery = true)
    List<String> findUploaderIds(@Param("albumId") String albumId);

    // Shared by the album lists. `userId` (may be null) decides pieceCount and canEdit. Append the rest of the query
    String OVERVIEW_COLUMNS = """
            SELECT al.public_id AS albumId, al.album_name AS albumName, al.cover_url AS coverUrl,
                   a.public_id AS artistId, a.artist_name AS artistName, a.artist_pfp_url AS artistPfpUrl,
                   (SELECT COUNT(*) FROM music_pieces mp
                    WHERE mp.album_id = al.album_id AND (mp.is_public = 1 OR mp.uploader_user_id = :userId)) AS pieceCount,
                   COALESCE(al.created_by_user_id = :userId OR a.created_by_user_id = :userId, 0) AS canEdit
            """;

    // The artist's albums (internal id) that `userId` can see, by name. An album with no piece the caller can
    // play only shows to its editors, so empty or joke albums stay out of sight
    default List<AlbumOverview> findArtistAlbums(String artistId, String userId) {
        return findArtistAlbumRows(artistId, userId).stream().map(AlbumRepository::toOverview).toList();
    }

    @Query(value = "SELECT * FROM (" + OVERVIEW_COLUMNS + """
            FROM albums al
            JOIN artists a ON a.artist_id = al.artist_id
            WHERE al.artist_id = :artistId
            ) WHERE pieceCount > 0 OR canEdit = 1
            ORDER BY albumName COLLATE NOCASE
            """, nativeQuery = true)
    List<AlbumOverviewRow> findArtistAlbumRows(@Param("artistId") String artistId, @Param("userId") String userId);

    // The 10 albums `ownerId` played most, all time; ties go to the one played most recently. Only plays of pieces
    // the caller can play on their own count, so a profile never reveals someone's private pieces
    default List<AlbumOverview> findTopAlbums(String ownerId, String userId) {
        return findTopAlbumRows(ownerId, userId).stream().map(AlbumRepository::toOverview).toList();
    }

    @Query(value = OVERVIEW_COLUMNS + """
            FROM play_events pe
            JOIN music_pieces p ON p.music_piece_id = pe.music_piece_id
            JOIN albums al ON al.album_id = p.album_id
            JOIN artists a ON a.artist_id = al.artist_id
            WHERE pe.user_id = :ownerId AND (p.is_public = 1 OR p.uploader_user_id = :userId)
            GROUP BY al.album_id
            ORDER BY COUNT(*) DESC, MAX(pe.played_at) DESC, al.album_id
            LIMIT 10
            """, nativeQuery = true)
    List<AlbumOverviewRow> findTopAlbumRows(@Param("ownerId") String ownerId, @Param("userId") String userId);

    private static AlbumOverview toOverview(AlbumOverviewRow row) {
        return new AlbumOverview(
                row.getAlbumId(),
                row.getAlbumName(),
                row.getCoverUrl(),
                new ArtistSummary(row.getArtistId(), row.getArtistName(), row.getArtistPfpUrl()),
                row.getPieceCount(),
                Integer.valueOf(1).equals(row.getCanEdit()));
    }

    interface AlbumOverviewRow {
        String getAlbumId();
        String getAlbumName();
        String getCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        int getPieceCount();
        Integer getCanEdit(); // SQLite has no boolean type: 1 or 0
    }
}
