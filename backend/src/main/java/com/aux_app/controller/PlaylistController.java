package com.aux_app.controller;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.playlist.PlaylistCreationDetails;
import com.aux_app.dto.playlist.PlaylistDetailsUpdate;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.aux_app.dto.playlist.CorePlaylist;
import com.aux_app.dto.playlist.PlaylistOverview;
import com.aux_app.error.AuxException;
import com.aux_app.repository.PlaylistRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.aux_app.auth.CurrentUser;
import com.aux_app.entity.UserEntity;

import java.util.ArrayList;
import java.util.UUID;


@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    final private PlaylistRepository playlistRepository;
    final private UploadService uploads;

    public PlaylistController(
            PlaylistRepository playlistRepository,
            UploadService uploadService
    ) {
        this.playlistRepository = playlistRepository;
        this.uploads = uploadService;
    }

    @GetMapping("/{username}/{playlist_id}")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or private and not yours (code PLAYLIST_NOT_FOUND)")
    public PlaylistOverview getPlaylist(
            @PathVariable String username,
            @PathVariable("playlist_id") String playlistId,
            @CurrentUser UserEntity user
    ) {
        String userId = user.getUserId();
        PlaylistRepository.PlaylistPage playlist = playlistRepository.findPlaylistWithTracks(playlistId, userId);

        if (playlist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        if (!username.equals(playlist.owner().username())) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "username"
            );
        }

        if (!userId.equals(playlist.owner().userId()) && !playlist.isPublic()) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        return new PlaylistOverview(
                new CorePlaylist(
                        playlistId,
                        playlist.playlistName(),
                        playlist.playlistCoverUrl()
                ),
                playlist.pieces().size(),
                playlist.owner(),
                playlist.isSaved(),
                playlist.pieces()
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PlaylistOverview createPlaylist(
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute PlaylistCreationDetails playlistCreationDetails
    ) {

        PlaylistEntity newPlaylist = new PlaylistEntity(
                UUID.randomUUID().toString(),
                user.getUserId(),
                playlistCreationDetails.playlistName().strip(),
                playlistCreationDetails.isPublic()
        );

        String coverUrl = this.uploads.replacePlaylistCover(
                newPlaylist,
                playlistCreationDetails.playlistCover()
        );

        return new PlaylistOverview(
                new CorePlaylist(
                        newPlaylist.getPlaylistId(),
                        newPlaylist.getPlaylistName(),
                        coverUrl
                ),
                0,
                new PlaylistOwner(
                        user.getUserId(),
                        user.getProfilePictureUrl(),
                        user.getUsername()
                ),
                false,
                new ArrayList<MusicPieceOverview>()
        );
    }

    @PutMapping(value = "/{playlistId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CorePlaylist editPlaylist(
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute PlaylistDetailsUpdate playlistDetailsUpdate,
            @PathVariable String playlistId
    ) {
        PlaylistEntity playlist = playlistRepository.findPlaylistEntityByPlaylistId(playlistId);

        if (playlist == null || !playlist.getOwnerId().equals(user.getUserId())) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        if (playlistDetailsUpdate.playlistName() != null) {
            playlist.setPlaylistName(playlistDetailsUpdate.playlistName());
        }
        if (playlistDetailsUpdate.isPublic() != null) {
            playlist.setIsPublic(playlistDetailsUpdate.isPublic());
        }

        if (playlistDetailsUpdate.playlistCover() != null) {
            uploads.replacePlaylistCover(playlist, playlistDetailsUpdate.playlistCover());
        } else {
            playlistRepository.save(playlist);
        }

        return new CorePlaylist(
                playlist.getPlaylistId(),
                playlist.getPlaylistName(),
                playlist.getPlaylistCoverUrl()
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
