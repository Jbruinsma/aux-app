package com.aux.controller;

import com.aux.dto.playlist.PlaylistOverview;
import com.aux.error.AuxException;
import com.aux.repository.PlaylistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.aux.auth.CurrentUser;
import com.aux.entity.UserEntity;


@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    final private PlaylistRepository playlistRepository;

    public PlaylistController(PlaylistRepository playlistRepository) {
        this.playlistRepository = playlistRepository;
    }

    @GetMapping("/{username}/{playlist_id}")
    public PlaylistOverview getPlaylist(
            @PathVariable String username,
            @PathVariable("playlist_id") String playlistId,
            @CurrentUser UserEntity user
    ) {
        String userId = user.getUserId();
        PlaylistRepository.PlaylistPage playlist = playlistRepository.findPlaylistWithTracks(playlistId, userId);

        if (playlist == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "PLAYLIST_NOT_FOUND", "Playlist not found", "playlistId");
        }

        if (!username.equals(playlist.owner().username())) {
            throw new AuxException(HttpStatus.NOT_FOUND, "PLAYLIST_NOT_FOUND", "Playlist not found", "username");
        }

        if (!userId.equals(playlist.owner().userId()) && !playlist.isPublic()) {
            throw new AuxException(HttpStatus.NOT_FOUND, "PLAYLIST_NOT_FOUND", "Playlist not found", "playlistId");
        }

        return new PlaylistOverview(
                playlistId,
                playlist.playlistCoverUrl(),
                playlist.playlistName(),
                playlist.pieces().size(),
                playlist.owner(),
                playlist.isSaved(),
                playlist.pieces()
        );
    }

    // TODO POST /{username}/create                        multipart: uuid, owner, name, isPublic, cover
    // TODO POST /{username}/{playlist_id}/delete
    // TODO POST /{username}/{playlist_id}/edit            multipart: name, isPublic, musicDeleted (JSON string), cover
    // TODO POST /{username}/{playlist_id}/add             multipart: mp3s (files), uuids (JSON string)
    // TODO POST /{username}/{playlist_id}/add/friends     json: friends
    // TODO GET  /summary/{username}/{playlist_id}/music_piece/{index}/{mp3_uuid}
    // TODO POST /update/{username}/{playlist_id}/music_piece/{index}/{mp3_uuid}   multipart: name, artist, cover
    // TODO GET  /{username}/{playlist_id}/play[/{start_index}]   query: shuffle
    // TODO GET  /{username}/{playlist_id}/play-shuffled/
}
