package com.aux_app.controller;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.playlist.*;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.PlaylistEntity;
import com.aux_app.entity.UserSavedPlaylistEntity;
import com.aux_app.entity.UserSavedPlaylistId;
import com.aux_app.repository.UserSavedPlaylistRepository;
import com.aux_app.services.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

    final private PlaylistRepository playlists;
    final private UserSavedPlaylistRepository savedPlaylists;
    final private UploadService uploads;

    public PlaylistController(
            PlaylistRepository playlists,
            UserSavedPlaylistRepository savedPlaylists,
            UploadService uploadService
    ) {
        this.playlists = playlists;
        this.savedPlaylists = savedPlaylists;
        this.uploads = uploadService;
    }

    @GetMapping("/{username}/{playlist_id}")
    @Operation(
            summary = "Get a playlist with its tracks",
            description = """
                    `username` must be the playlist owner's username. Private playlists are only visible to
                    their owner; to anyone else they return 404, the same as a missing playlist.
                    `isSaved` is true when the caller has saved the playlist.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or private and not yours (code PLAYLIST_NOT_FOUND)")
    public PlaylistOverview getPlaylist(
            @PathVariable String username,
            @PathVariable("playlist_id") String playlistId,
            @CurrentUser UserEntity user
    ) {
        String userId = user.getUserId();
        PlaylistRepository.PlaylistPage playlist = playlists.findPlaylistWithTracks(playlistId, userId);

        if (playlist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        if (!username.equalsIgnoreCase(playlist.owner().username())) {
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
                playlist.isPublic(),
                playlist.owner(),
                playlist.isSaved(),
                playlist.pieces()
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Create a playlist",
            description = """
                    Multipart fields:
                    - `playlistName` (required): 1-36 chars, trimmed.
                    - `isPublic`: `true` or `false`, defaults to `false`.
                    - `playlistCover` (required): a JPEG or PNG, at most 5MB and 25 megapixels, at least 256x256.
                      Send the original image; do NOT crop it on the frontend. The server applies EXIF rotation,
                      center-crops to a square, resizes to at most 1024x1024, and stores it as WebP.
                    The caller owns the new playlist. Returns it with no tracks.
                    """)
    @ApiResponse(responseCode = "200", description = "Playlist created; body is the new, empty playlist")
    @ApiResponse(responseCode = "400", description = "Missing or bad field, or unreadable or too small cover (codes INVALID_FIELD, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
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
                newPlaylist.getIsPublic(),
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
    @Operation(
            summary = "Edit a playlist's details",
            description = """
                    Partial update of a playlist the caller owns. Every multipart field is optional; a missing or
                    blank field leaves that value unchanged.
                    - `playlistName`: at most 36 chars, trimmed.
                    - `isPublic`: `true` or `false`.
                    - `playlistCover`: same rules as on create. Replaces the old cover.
                    Returns the playlist's id, name and cover URL after the update.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Bad field, or unreadable or too small cover (codes INVALID_FIELD, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or not yours (code PLAYLIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public CorePlaylist editPlaylist(
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute PlaylistDetailsUpdate playlistDetailsUpdate,
            @PathVariable String playlistId
    ) {
        PlaylistEntity playlist = playlists.findPlaylistEntityByPlaylistId(playlistId);

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
            playlists.save(playlist);
        }

        return new CorePlaylist(
                playlist.getPlaylistId(),
                playlist.getPlaylistName(),
                playlist.getPlaylistCoverUrl()
        );
    }

    @PutMapping("/{playlist_id}/save")
    @Operation(
            summary = "Save a playlist",
            description = """
                    Adds another user's public playlist to the caller's saved playlists. Saving a playlist
                    that is already saved is a no-op and still returns 200.
                    Private playlists return 404, the same as a missing playlist.
                    Returns the playlist's id, name and cover URL, and `isSaved` (always true).
                    """)
    @ApiResponse(responseCode = "200", description = "Playlist saved (or already saved)")
    @ApiResponse(responseCode = "403", description = "Caller owns the playlist (code PLAYLIST_ACTION_FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or private (code PLAYLIST_NOT_FOUND)")
    public SavedPlaylistResponse savePlaylist(
            @CurrentUser UserEntity user,
            @PathVariable String playlist_id
    ) {

        PlaylistEntity selectedPlaylist = playlists.findPlaylistEntityByPlaylistId(playlist_id);

        if (selectedPlaylist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        if (!selectedPlaylist.getIsPublic()) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "PLAYLIST_NOT_FOUND",
                    "Playlist not found",
                    "playlistId"
            );
        }

        String userId = user.getUserId();

        if (selectedPlaylist.getOwnerId().equals(userId)) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "PLAYLIST_ACTION_FORBIDDEN",
                    "Playlist cannot be saved",
                    "playlistId"
            );
        }

        UserSavedPlaylistId id = new UserSavedPlaylistId(userId, playlist_id);
        if (!savedPlaylists.existsById(id)) {
            savedPlaylists.save(new UserSavedPlaylistEntity(userId, playlist_id));
        }

        return new SavedPlaylistResponse(
                new CorePlaylist(
                        selectedPlaylist.getPlaylistId(),
                        selectedPlaylist.getPlaylistName(),
                        selectedPlaylist.getPlaylistCoverUrl()
                ),
                true
        );
    }

    @DeleteMapping("/{playlist_id}/save")
    @Operation(
            summary = "Unsave a playlist",
            description = """
                    Removes a playlist from the caller's saved playlists. Unsaving a playlist the caller
                    has not saved returns 404, so a repeated call is not idempotent.
                    Returns the playlist's id, name and cover URL, and `isSaved` (always false).
                    """)
    @ApiResponse(responseCode = "200", description = "Playlist unsaved")
    @ApiResponse(responseCode = "403", description = "Caller owns the playlist (code PLAYLIST_ACTION_FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Playlist missing, or not saved by the caller (code SAVED_PLAYLIST_NOT_FOUND)")
    public SavedPlaylistResponse unsavePlaylist(
            @CurrentUser UserEntity user,
            @PathVariable String playlist_id
    ) {

        String userId = user.getUserId();

        PlaylistEntity selectedPlaylist = playlists.findSavedPlaylist(playlist_id, userId);

        if (selectedPlaylist == null) {
            throw new AuxException(
                    HttpStatus.NOT_FOUND,
                    "SAVED_PLAYLIST_NOT_FOUND",
                    "You do not have this playlist saved",
                    "playlistId"
            );
        }

        if (selectedPlaylist.getOwnerId().equals(userId)) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "PLAYLIST_ACTION_FORBIDDEN",
                    "You do cannot remove this saved playlist",
                    "playlistId"
            );
        }

        savedPlaylists.deleteSaved(userId, playlist_id);

        return new SavedPlaylistResponse(
                new CorePlaylist(
                        selectedPlaylist.getPlaylistId(),
                        selectedPlaylist.getPlaylistName(),
                        selectedPlaylist.getPlaylistCoverUrl()
                ),
                false
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
