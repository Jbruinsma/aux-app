package com.aux_app.controller;

import com.aux_app.dto.playlist.*;
import com.aux_app.entity.*;
import com.aux_app.services.PlaylistMembersService;
import com.aux_app.services.PlaylistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.aux_app.auth.CurrentUser;

import java.util.ArrayList;
import java.util.List;


@RestController
@RequestMapping("/api/playlists")
public class PlaylistController {

    final private PlaylistService playlists;
    final private PlaylistMembersService members;

    public PlaylistController(PlaylistMembersService members) {
        this.playlists = members;
        this.members = members;
    }

    @GetMapping("/{username}/{playlistId}")
    @Operation(
            summary = "Get a playlist with its tracks",
            description = """
                    `username` must be the playlist owner's username. Private playlists are only visible to
                    their owner and accepted members. A caller with a pending invite gets 403
                    (PLAYLIST_INVITE_PENDING); anyone else gets 404, the same as a missing playlist.
                    `access` is the caller's level (OWNER, EDITOR or LISTENER), null on a public playlist they have no part in.
                    `isSaved` is true when the caller has saved the playlist.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Private playlist and the caller's invite is still pending (code PLAYLIST_INVITE_PENDING)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or private and not yours (code PLAYLIST_NOT_FOUND)")
    public PlaylistOverview getPlaylist(
            @Parameter(description = "Username of the playlist owner", example = "justin") @PathVariable String username,
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user
    ) {
        return this.playlists.retrievePlaylist(username, playlistId, user);
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
        return this.playlists.createPlaylist(user, playlistCreationDetails);
    }

    @PutMapping(value = "/{playlistId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Edit a playlist's details",
            description = """
                    Partial update of a playlist the caller owns or can edit (accepted EDITOR). Only the owner can change `isPublic`. Every multipart field is optional; a missing or
                    blank field leaves that value unchanged.
                    - `playlistName`: at most 36 chars, trimmed.
                    - `isPublic`: `true` or `false`.
                    - `playlistCover`: same rules as on create. Replaces the old cover.
                    Returns the playlist's id, name and cover URL after the update.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Bad field, or unreadable or too small cover (codes INVALID_FIELD, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "403", description = "Editor tried to change `isPublic` (code PLAYLIST_ACTION_FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or caller can't edit it (code PLAYLIST_NOT_FOUND)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public CorePlaylist editPlaylist(
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute PlaylistDetailsUpdate playlistDetailsUpdate,
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable String playlistId
    ) {
        return this.playlists.editPlaylist(
                playlistId,
                playlistDetailsUpdate,
                user
        );
    }

    @PostMapping("/{playlistId}/pieces")
    @Operation(
            summary = "Add music pieces to a playlist",
            description = """
                    Owner or accepted editor only. Appends the pieces to the end of the playlist, in the order the server finds them.
                    A piece is skipped (counted in `failedCount`) when its id is unknown, it is already in the playlist, or it is private.
                    A private piece is only accepted from its uploader, and only into a non-public playlist or one the uploader owns.
                    Skipped pieces do not fail the request.
                    """)
    @ApiResponse(responseCode = "200", description = "OK, even if every piece was skipped")
    @ApiResponse(responseCode = "400", description = "`musicPieceIds` missing, empty or over 100 (code INVALID_FIELD)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or caller can't edit it (code PLAYLIST_NOT_FOUND)")
    public PlaylistMusicPieceAdditionResponse addPieceToPlaylist(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user,
            @Valid @RequestBody PlaylistMusicPieceAddition details
    ) {
        return playlists.addMusicPiecesToPlaylist(playlistId, user, details);
    }

    @PutMapping("/{playlistId}/save")
    @Operation(
            summary = "Save a playlist",
            description = """
                    Adds another user's public playlist to the caller's saved playlists. Saving a playlist
                    that is already saved is a no-op and still returns 200.
                    Private playlists return 404, the same as a missing playlist.
                    Returns the playlist's id, name and cover URL, and `isSaved` (always true).
                    """)
    @ApiResponse(responseCode = "200", description = "Playlist saved (or already saved)")
    @ApiResponse(responseCode = "403", description = "Caller owns the playlist or is a member of it (code PLAYLIST_ACTION_FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or private (code PLAYLIST_NOT_FOUND)")
    public SavedPlaylistResponse savePlaylist(
            @CurrentUser UserEntity user,
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId
    ) {
        return playlists.savePlaylist(playlistId, user);
    }

    @DeleteMapping("/{playlistId}/save")
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
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable String playlistId
    ) {
        return playlists.unsavePlaylist(playlistId, user);
    }

    @GetMapping("/{playlistId}/members")
    @Operation(
            summary = "List a playlist's members",
            description = """
                    Owner only. Returns every membership, PENDING and ACCEPTED, oldest invite first, each with
                    its user, permission, status and timestamps.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found or caller is not its owner (code PLAYLIST_NOT_FOUND)")
    public List<PlaylistMemberResponse> getPlaylistMembers(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user
    ) {
        return members.list(playlistId, user);
    }

    @PostMapping("/{playlistId}/members")
    @Operation(
            summary = "Invite a user to a playlist",
            description = """
                    Owner only. Creates a PENDING membership for the user with the given public id and emails
                    them an invitation (a failed email does not fail the request). Returns the new membership.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "User already has a membership row (code USER_ALREADY_INVITED)")
    @ApiResponse(responseCode = "400", description = "Missing or bad field, or inviting yourself (code INVALID_FIELD)")
    @ApiResponse(responseCode = "404", description = "Playlist not found or caller is not its owner (PLAYLIST_NOT_FOUND), or user not found (USER_NOT_FOUND)")
    public PlaylistMemberResponse addPlaylistMembers(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user,
            @Valid @RequestBody PlaylistInvitationDetails details
    ) {
        return members.invite(playlistId, user, details);
    }

    @PutMapping("/{playlistId}/members")
    @Operation(
            summary = "Change a member's permission",
            description = """
                    Owner only. Updates the permission of an existing member (PENDING or ACCEPTED).
                    Returns the updated membership.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Missing or bad field (code INVALID_FIELD)")
    @ApiResponse(responseCode = "404", description = "Playlist not found or caller is not its owner (PLAYLIST_NOT_FOUND), user not found (USER_NOT_FOUND), or user has no membership (USER_NOT_MEMBER)")
    public PlaylistMemberResponse editPlaylistMemberPermissions(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user,
            @Valid @RequestBody PlaylistInvitationDetails details
    ) {
        return members.updatePermission(playlistId, user, details);
    }

    @DeleteMapping("/{playlistId}/members")
    @Operation(
            summary = "Remove a member from a playlist",
            description = """
                    Owner only. Deletes the membership of the user with the given public id, whether they
                    accepted or the invite is still PENDING (which revokes it). Returns the removed user and
                    the status the membership had.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Missing or bad field (code INVALID_FIELD)")
    @ApiResponse(responseCode = "404", description = "Playlist not found or caller is not its owner (PLAYLIST_NOT_FOUND), user not found (USER_NOT_FOUND), or user has no membership (USER_NOT_MEMBER)")
    public PlaylistMemberRemovalResponse deletePlaylistMembers(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user,
            @Valid @RequestBody PlaylistMemberRemoval details
    ) {
        return members.remove(playlistId, user, details);
    }

    @GetMapping("/invites")
    @Operation(
            summary = "List the caller's pending playlist invites",
            description = """
                    Returns every unanswered (PENDING) invite the caller has, newest first, with the playlist's
                    name and cover, the permission offered, and who invited them. Accepted invites are not listed.
                    A pending invite does not grant access to a private playlist; accept it first.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    public PendingPlaylistInvites getPlaylistInvites(
            @CurrentUser UserEntity user
    ) {
        return members.retrievePlaylistInvites(user.getUserId());
    }

    @PostMapping("/{playlistId}/invites/accept")
    @Operation(
            summary = "Accept a playlist invite",
            description = """
                    Accepts the caller's PENDING invite to the playlist. From then on the caller has the
                    permission they were offered (LISTENER or EDITOR), including on a private playlist.
                    Returns the caller's membership with status ACCEPTED.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or the caller has no pending invite to it (code INVITE_NOT_FOUND)")
    public PlaylistMemberResponse acceptPlaylistInvite(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user
    ) {
        return members.acceptInvite(playlistId, user);
    }

    @DeleteMapping("/{playlistId}/invites")
    @Operation(
            summary = "Decline a playlist invite",
            description = """
                    Deletes the caller's PENDING invite to the playlist. The owner can invite them again later.
                    Returns the caller's user and `previousStatus` PENDING.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or the caller has no pending invite to it (code INVITE_NOT_FOUND)")
    public PlaylistMemberRemovalResponse deletePlaylistInvite(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user
    ) {
        return members.declineInvite(playlistId, user);
    }

    @DeleteMapping("/{playlistId}/membership")
    @Operation(
            summary = "Leave a playlist",
            description = """
                    Removes the caller from a playlist they accepted an invite to. The owner cannot leave
                    their own playlist. Returns the caller's user and `previousStatus` ACCEPTED.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Playlist not found, or the caller is not an accepted member of it (code MEMBERSHIP_NOT_FOUND)")
    public PlaylistMemberRemovalResponse deletePlaylistMembership(
            @Parameter(description = "Public id of the playlist", example = "p_7c2dK1") @PathVariable("playlistId") String playlistId,
            @CurrentUser UserEntity user
    ) {
        return members.leave(playlistId, user);
    }

    // TODO POST /{username}/create                        multipart: uuid, owner, name, isPublic, cover
    // TODO POST /{username}/{playlistId}/delete
    // TODO POST /{username}/{playlistId}/edit            multipart: name, isPublic, musicDeleted (JSON string), cover
    // TODO POST /{username}/{playlistId}/add             multipart: mp3s (files), uuids (JSON string)
    // TODO POST /{username}/{playlistId}/add/friends     json: friends
    // TODO GET  /summary/{username}/{playlistId}/music_piece/{index}/{mp3_uuid}
    // TODO POST /update/{username}/{playlistId}/music_piece/{index}/{mp3_uuid}   multipart: name, artist, cover
    // TODO GET  /{username}/{playlistId}/play[/{start_index}]   query: shuffle
    // TODO GET  /{username}/{playlistId}/play-shuffled/
}
