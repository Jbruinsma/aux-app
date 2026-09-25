package com.aux.repository;

import java.util.List;

import com.aux.entity.MusicPieceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.aux.entity.PlaylistTrackEntity;
import com.aux.entity.PlaylistTrackId;

public interface PlaylistTrackRepository extends JpaRepository<PlaylistTrackEntity, PlaylistTrackId> {

    // Joins to music_pieces so the track list comes back in one query, not one-per-track
    @Query(value = """
            SELECT mp.* FROM playlist_tracks pt
            JOIN music_pieces mp ON mp.music_piece_id = pt.music_piece_id
            WHERE pt.playlist_id = :playlistId
            ORDER BY pt.playlist_position
            """, nativeQuery = true)
    List<MusicPieceEntity> findTracksInOrder(@Param("playlistId") String playlistId);
}
