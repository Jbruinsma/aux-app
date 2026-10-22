package com.aux_app.repository;

import java.util.List;

import com.aux_app.entity.MusicPieceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux_app.entity.PlaylistTrackEntity;
import com.aux_app.entity.PlaylistTrackId;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrackEntity, PlaylistTrackId> {

    // Joins to music_pieces so the track list comes back in one query, not one-per-track
    @Query(value = """
            SELECT mp.* FROM playlist_tracks pt
            JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            WHERE pt.playlist_id = :playlistId
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<MusicPieceEntity> findTracksInOrder(@Param("playlistId") String playlistId);

    // Ids only, for building the play queue: just the pieces `userId` (internal id) can play from this playlist,
    // per PlaybackEventService.isPlayable. `publicId` is the playlist's public id; piece ids are public too.
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, pt.playlist_position AS playlistPosition
            FROM playlist_tracks pt
            JOIN playlists p ON p.playlist_id = pt.playlist_id
            JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            WHERE p.public_id = :publicId
              AND (mp.is_public = 1 OR mp.uploader_user_id = :userId
                   OR (COALESCE(p.is_public, 0) = 0 AND (mp.uploader_user_id = p.owner_id OR EXISTS (
                       SELECT 1 FROM playlist_members um WHERE um.playlist_id = p.playlist_id
                         AND um.user_id = mp.uploader_user_id AND um.status = 'ACCEPTED'))))
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<TrackIdRow> findPlayableTrackIdsInOrder(@Param("publicId") String publicId, @Param("userId") String userId);

    // Highest position in the playlist, -1 when empty. Positions can have gaps where tracks were removed.
    @Query(value = """
            SELECT COALESCE(MAX(playlist_position), -1) FROM playlist_tracks WHERE playlist_id = :playlistId
            """, nativeQuery = true)
    int findLastPosition(@Param("playlistId") String playlistId);

    interface TrackIdRow {
        String getMusicPieceId();
        Integer getPlaylistPosition();
    }
}
