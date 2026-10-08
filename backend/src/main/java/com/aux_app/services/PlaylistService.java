package com.aux_app.services;

import com.aux_app.dto.music_piece.MusicPieceOverview;
import com.aux_app.dto.playlist.*;
import com.aux_app.dto.users.PlaylistOwner;
import com.aux_app.entity.*;
import com.aux_app.error.AuxException;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.PlaylistRepository;
import com.aux_app.repository.PlaylistTrackRepository;
import com.aux_app.repository.UserSavedPlaylistRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public abstract class PlaylistService {

    protected final PlaylistRepository playlists;
    protected final UserSavedPlaylistRepository savedPlaylists;
    protected final MusicPieceRepository musicPieces;
    protected final PlaylistTrackRepository tracks;
    protected final UploadService uploads;

    protected PlaylistService(
            PlaylistRepository playlists,
            UserSavedPlaylistRepository savedPlaylists,
            MusicPieceRepository musicPieces,
            PlaylistTrackRepository tracks,
            UploadService uploads
    ) {
        this.playlists = playlists;
        this.savedPlaylists = savedPlaylists;
        this.musicPieces = musicPieces;
        this.tracks = tracks;
        this.uploads = uploads;
    }

    public PlaylistOverview retrievePlaylist(String username, String playlistId, UserEntity user) {
        PlaylistRepository.PlaylistPage playlist = playlists.findPlaylistWithTracks(playlistId, user.getUserId());

        if (playlist == null) {
            throw playlistNotFound("playlistId");
        }
        if (!username.equalsIgnoreCase(playlist.owner().username())) {
            throw playlistNotFound("username");
        }

        PlaylistAccess access = resolveAccess(user, playlist);

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
                playlist.pieces(),
                playlist.editors(),
                access
        );
    }

    public PlaylistOverview createPlaylist(UserEntity owner, PlaylistCreationDetails playlistCreationDetails) {
        PlaylistEntity newPlaylist = new PlaylistEntity(
                UUID.randomUUID().toString(),
                owner.getUserId(),
                playlistCreationDetails.playlistName().strip(),
                playlistCreationDetails.isPublic()
        );

        String coverUrl = this.uploads.replacePlaylistCover(
                newPlaylist,
                playlistCreationDetails.playlistCover()
        );

        return new PlaylistOverview(
                new CorePlaylist(
                        newPlaylist.getPublicId(),
                        newPlaylist.getPlaylistName(),
                        coverUrl
                ),
                0,
                newPlaylist.getIsPublic(),
                new PlaylistOwner(
                        owner.getPublicId(),
                        owner.getProfilePictureUrl(),
                        owner.getUsername()
                ),
                false,
                new ArrayList<MusicPieceOverview>(),
                new ArrayList<PlaylistEditor>(),
                PlaylistAccess.OWNER
        );
    }

    public CorePlaylist editPlaylist(String playlistId, PlaylistDetailsUpdate playlistDetailsUpdate, UserEntity user) {
        PlaylistRepository.PlaylistWithCaller found = playlists.findPlaylistWithCaller(playlistId, user.getUserId());
        boolean isOwner = found != null && found.isOwner(user.getUserId());

        if (found == null || !(isOwner || found.isEditor())) {
            throw playlistNotFound("playlistId");
        }

        if (playlistDetailsUpdate.isPublic() != null && !isOwner) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "PLAYLIST_ACTION_FORBIDDEN",
                    "Only the owner can change whether a playlist is public",
                    "isPublic"
            );
        }

        PlaylistEntity playlist = found.playlist();

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
                playlist.getPublicId(),
                playlist.getPlaylistName(),
                playlist.getPlaylistCoverUrl()
        );
    }

    @Transactional
    public PlaylistMusicPieceAdditionResponse addMusicPiecesToPlaylist(String playlistId, UserEntity user, PlaylistMusicPieceAddition details) {
        String userId = user.getUserId();

        PlaylistRepository.PlaylistWithPieces found = playlists.findPlaylistWithPieces(
                playlistId,
                userId,
                details.musicPieceIds()
        );

        // Only owner and accepted editors may change tracks. Anyone else gets 404, same as editPlaylist.
        if (found == null || !(found.caller().isOwner(userId) || found.caller().isEditor())) {
            throw playlistNotFound("playlistId");
        }

        PlaylistEntity playlist = found.caller().playlist();
        List<MusicPieceEntity> existing = tracks.findTracksInOrder(playlist.getPlaylistId());
        Set<String> alreadyInPlaylist = existing.stream().map(MusicPieceEntity::getMusicPieceId).collect(Collectors.toSet());

        int nextPosition = existing.size();
        List<PlaylistTrackEntity> newTracks = new ArrayList<>();

        // A private piece needs its uploader to add it, and a playlist that is not public or that they own,
        // so its title never shows up on someone else's public playlist.
        boolean canAddPrivate = !playlist.getIsPublic() || found.caller().isOwner(userId);

        for (MusicPieceEntity musicPiece : found.pieces()) {
            boolean allowed = musicPiece.getIsPublic()
                    || (canAddPrivate && userId.equals(musicPiece.getUploaderUserId()));
            if (!allowed || alreadyInPlaylist.contains(musicPiece.getMusicPieceId())) {
                continue;
            }
            newTracks.add(new PlaylistTrackEntity(playlist.getPlaylistId(), musicPiece.getMusicPieceId(), nextPosition++));
        }

        tracks.saveAll(newTracks);

        int requested = new HashSet<>(details.musicPieceIds()).size();

        return new PlaylistMusicPieceAdditionResponse(
                new CorePlaylist(playlist.getPublicId(), playlist.getPlaylistName(), playlist.getPlaylistCoverUrl()),
                newTracks.size(),
                requested - newTracks.size(),
                nextPosition
        );
    }

    public SavedPlaylistResponse savePlaylist(String playlistId, UserEntity user) {

        PlaylistRepository.PlaylistWithCaller found = playlists.findPlaylistWithCaller(playlistId, user.getUserId());
        PlaylistEntity selectedPlaylist = findPublicPlaylist(found);

        String userId = user.getUserId();

        if (found.isOwner(userId) || found.isAcceptedMember()) {
            throw new AuxException(
                    HttpStatus.FORBIDDEN,
                    "PLAYLIST_ACTION_FORBIDDEN",
                    "Playlist cannot be saved",
                    "playlistId"
            );
        }

        UserSavedPlaylistId id = new UserSavedPlaylistId(userId, selectedPlaylist.getPlaylistId());
        if (!savedPlaylists.existsById(id)) {
            savedPlaylists.save(new UserSavedPlaylistEntity(userId, selectedPlaylist.getPlaylistId()));
        }

        return new SavedPlaylistResponse(
                new CorePlaylist(
                        selectedPlaylist.getPublicId(),
                        selectedPlaylist.getPlaylistName(),
                        selectedPlaylist.getPlaylistCoverUrl()
                ),
                true
        );
    }

    public SavedPlaylistResponse unsavePlaylist(String playlistId, UserEntity user) {

        String userId = user.getUserId();

        PlaylistEntity selectedPlaylist = playlists.findSavedPlaylist(playlistId, userId);

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
                    "You cannot remove this saved playlist",
                    "playlistId"
            );
        }

        savedPlaylists.deleteSaved(userId, selectedPlaylist.getPlaylistId());

        return new SavedPlaylistResponse(
                new CorePlaylist(
                        selectedPlaylist.getPublicId(),
                        selectedPlaylist.getPlaylistName(),
                        selectedPlaylist.getPlaylistCoverUrl()
                ),
                false
        );
    }

    protected PlaylistEntity findOwnedPlaylist(String publicPlaylistId, String ownerId) {

        PlaylistEntity playlist = playlists.findByPublicId(publicPlaylistId);

        if (playlist == null || !playlist.getOwnerId().equals(ownerId)) {
            throw playlistNotFound("playlistId");
        }

        return playlist;
    }

    // Caller's access level, or null on a public playlist they have no part in.
    // Private playlists 403 for a pending invite and 404 for everyone else without access.
    private static @Nullable PlaylistAccess resolveAccess(UserEntity user, PlaylistRepository.PlaylistPage playlist) {
        boolean isOwner = user.getPublicId().equals(playlist.owner().userId());
        boolean isAccepted = playlist.callerStatus() == PlaylistMemberStatus.ACCEPTED;

        if (!isOwner && !isAccepted && !playlist.isPublic()) {
            if (playlist.callerStatus() == PlaylistMemberStatus.PENDING) {
                throw new AuxException(
                        HttpStatus.FORBIDDEN,
                        "PLAYLIST_INVITE_PENDING",
                        "Accept your invitation to access this playlist",
                        "playlistId"
                );
            }
            throw playlistNotFound("playlistId");
        }

        if (isOwner) return PlaylistAccess.OWNER;
        if (!isAccepted) return null;
        return playlist.callerPermission() == PlaylistPermission.EDITOR
                ? PlaylistAccess.EDITOR
                : PlaylistAccess.LISTENER;
    }

    // 404s unless the playlist exists and is public; private ones look missing on purpose.
    private static PlaylistEntity findPublicPlaylist(PlaylistRepository.PlaylistWithCaller found) {
        if (found == null || found.playlist() == null || !found.playlist().getIsPublic()) {
            throw playlistNotFound("playlistId");
        }
        return found.playlist();
    }

    private static AuxException playlistNotFound(String field) {
        return new AuxException(
                HttpStatus.NOT_FOUND,
                "PLAYLIST_NOT_FOUND",
                "Playlist not found",
                field
        );
    }

}
