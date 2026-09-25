package com.aux.repository;

import com.aux.dto.artist.ArtistSummary;
import com.aux.dto.music_piece.MusicPieceOverview;
import com.aux.dto.users.PlaylistOwner;
import com.aux.entity.PlaylistEntity;
import com.aux.entity.PlaylistTrackEntity;
import com.aux.entity.PlaylistTrackId;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.ArrayList;
import java.util.List;

public interface PlaylistRepository extends JpaRepository<PlaylistEntity, String> {

    // Null if playlist doesn't exist. Guard before use.
    default PlaylistPage findPlaylistWithTracks(String playlistId, String userId) {
        List<PlaylistWithTracksRow> rows = findPlaylistPage(playlistId, userId);
        if (rows.isEmpty()) {
            return null;
        }
        PlaylistWithTracksRow first = rows.get(0);
        List<MusicPieceOverview> pieces = createPieces(rows);
        return new PlaylistPage(
                first.getPlaylistId(),
                first.getPlaylistCoverUrl(),
                first.getPlaylistName(),
                Boolean.TRUE.equals(first.getIsPublic()),
                new PlaylistOwner(first.getOwnerId(), first.getOwnerPfpUrl(), first.getOwnerUsername()),
                Boolean.TRUE.equals(first.getIsSaved()),
                pieces);
    }

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
                    Boolean.TRUE.equals(row.getIsFavorite())));
        }
        return pieces;
    }

    @Query(value = """
            SELECT p.playlist_id AS playlistId,
                   p.owner_id AS ownerId,
                   p.is_public AS isPublic,
                   p.playlist_cover_url AS playlistCoverUrl,
                   p.playlist_name AS playlistName,
                   u.username AS ownerUsername,
                   u.profile_picture_url AS ownerPfpUrl,
                   (usp.user_id IS NOT NULL) AS isSaved,
                   pt.music_piece_id AS musicPieceId,
                   pt.playlist_position AS playlistPosition,
                   mp.name AS pieceName,
                   mp.cover_url AS pieceCoverUrl,
                   a.artist_id AS artistId,
                   a.artist_name AS artistName,
                   a.artist_pfp_url AS artistPfpUrl,
                   (uft.user_id IS NOT NULL) AS isFavorite
            FROM playlists p
            JOIN users u ON u.user_id = p.owner_id
            LEFT JOIN user_saved_playlists usp ON usp.playlist_id = p.playlist_id AND usp.user_id = :userId
            LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.playlist_id
            LEFT JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            LEFT JOIN artists a ON a.artist_id = mp.artist_id
            LEFT JOIN user_favorite_tracks uft ON uft.music_piece_id = mp.music_piece_id AND uft.user_id = :userId
            WHERE p.playlist_id = :playlistId
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<PlaylistWithTracksRow> findPlaylistPage(@Param("playlistId") String playlistId, @Param("userId") String userId);

    interface PlaylistWithTracksRow {
        String getPlaylistId();
        String getOwnerId();
        Boolean getIsPublic();
        String getPlaylistCoverUrl();
        String getPlaylistName();
        String getOwnerUsername();
        String getOwnerPfpUrl();
        Boolean getIsSaved();
        String getMusicPieceId();
        Integer getPlaylistPosition();
        String getPieceName();
        String getPieceCoverUrl();
        String getArtistId();
        String getArtistName();
        String getArtistPfpUrl();
        Boolean getIsFavorite();
    }

    record PlaylistPage(
            String playlistId,
            String playlistCoverUrl,
            String playlistName,
            boolean isPublic,
            PlaylistOwner owner,
            boolean isSaved,
            List<MusicPieceOverview> pieces
    ) {}

}
