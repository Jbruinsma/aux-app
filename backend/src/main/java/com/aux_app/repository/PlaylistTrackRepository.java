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

    // Ids only, for building the play queue. `publicId` is the playlist's public id; piece ids are public too.
    @Query(value = """
            SELECT mp.public_id AS musicPieceId, pt.playlist_position AS playlistPosition
            FROM playlist_tracks pt
            JOIN playlists p ON p.playlist_id = pt.playlist_id
            JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            WHERE p.public_id = :publicId
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<TrackIdRow> findTrackIdsInOrder(@Param("publicId") String publicId);

    interface TrackIdRow {
        String getMusicPieceId();
        Integer getPlaylistPosition();
    }
}
