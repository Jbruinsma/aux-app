package com.aux_app.services;

import com.aux_app.config.PlaylistConfig;
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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
        PlaylistRepository.PlaylistPage playlist = playlists.findPlaylistWithTracks(playlistId, user.getUserId(), -1, TRACK_PAGE_SIZE);

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
                playlist.totalPieces(),
                playlist.isPublic(),
                playlist.owner(),
                playlist.isSaved(),
                playlist.pieces(),
                positionCursor(playlist.nextAfterPosition()),
                playlist.editors(),
                access
        );
    }

    // Next pages of the track list after retrievePlaylist. Same visibility rules.
    public PlaylistQueuePage retrievePlaylistPieces(String playlistId, String cursor, UserEntity user) {
        PlaylistRepository.PlaylistPage playlist = playlists.findPlaylistWithTracks(
                playlistId, user.getUserId(), parsePositionCursor(cursor), TRACK_PAGE_SIZE);

        if (playlist == null) {
            throw playlistNotFound("playlistId");
        }
        resolveAccess(user, playlist);

        return new PlaylistQueuePage(playlist.pieces(), positionCursor(playlist.nextAfterPosition()));
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
                null,
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

            if (nextPosition >= PlaylistConfig.MAX_PLAYLIST_SIZE) {
                break;
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

    static final int TRACK_PAGE_SIZE = 50;

    // Everything the server needs to rebuild the queue and continue where the last page stopped.
    // Plain order: lastPosition. Shuffle: seed, the last shuffled piece sent (null before any), and the start piece,
    // which plays first and is left out of the shuffled part.
    record QueueCursor(
            boolean shuffle,
            long seed,
            @Nullable String lastMusicPieceId,
            int lastPosition,
            @Nullable String startMusicPieceId
    ) {}

    // Piece ids for one page, plus the cursor for the next one (null when the queue is done).
    record QueueSlice(List<String> musicPieceIds, @Nullable QueueCursor next) {}

    public PlaylistQueuePage playPlaylist(
            String playlistId,
            UserEntity user,
            @Nullable String startMusicPieceId,
            boolean shuffle,
            @Nullable String cursor
    ) {
        requireViewable(playlists.findPlaylistWithCaller(playlistId, user.getUserId()), user.getUserId());

        List<PlaylistTrackRepository.TrackIdRow> rows = tracks.findTrackIdsInOrder(playlistId);

        QueueSlice slice;
        if (cursor == null) {
            slice = sliceQueue(rows, firstCursor(rows, startMusicPieceId, shuffle), true, TRACK_PAGE_SIZE);
        } else {
            if (startMusicPieceId != null || shuffle) {
                throw new AuxException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_FIELD",
                        "Send either cursor or start/shuffle, not both",
                        "cursor"
                );
            }
            slice = sliceQueue(rows, decodeCursor(cursor), false, TRACK_PAGE_SIZE);
        }

        // findOverviews returns rows in any order; put them back in queue order.
        // A piece deleted between the two queries is just left out.
        Map<String, MusicPieceOverview> byId = musicPieces.findOverviews(slice.musicPieceIds(), user.getUserId())
                .stream()
                .collect(Collectors.toMap(MusicPieceOverview::musicPieceId, piece -> piece));
        List<MusicPieceOverview> page = slice.musicPieceIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();

        return new PlaylistQueuePage(page, slice.next() == null ? null : encodeCursor(slice.next()));
    }

    private static QueueCursor firstCursor(
            List<PlaylistTrackRepository.TrackIdRow> rows,
            @Nullable String startMusicPieceId,
            boolean shuffle
    ) {
        int startPosition = 0;
        if (startMusicPieceId != null) {
            startPosition = rows.stream()
                    .filter(row -> row.getMusicPieceId().equals(startMusicPieceId))
                    .findFirst()
                    .map(PlaylistTrackRepository.TrackIdRow::getPlaylistPosition)
                    .orElseThrow(() -> new AuxException(
                            HttpStatus.NOT_FOUND,
                            "MUSIC_PIECE_NOT_FOUND",
                            "Music piece is not in this playlist",
                            "start"
                    ));
        }

        if (shuffle) {
            return new QueueCursor(true, ThreadLocalRandom.current().nextLong(), null, -1, startMusicPieceId);
        }
        // Plain order plays from the start piece onward; anything before it is not queued.
        return new QueueCursor(false, 0, null, startPosition - 1, null);
    }

    // `rows` must be in playlist order (findTrackIdsInOrder). On the first page the start piece goes first.
    static QueueSlice sliceQueue(
            List<PlaylistTrackRepository.TrackIdRow> rows,
            QueueCursor cursor,
            boolean firstPage,
            int pageSize
    ) {
        if (!cursor.shuffle()) {
            List<PlaylistTrackRepository.TrackIdRow> rest = rows.stream()
                    .filter(row -> row.getPlaylistPosition() > cursor.lastPosition())
                    .toList();
            List<PlaylistTrackRepository.TrackIdRow> taken = rest.subList(0, Math.min(pageSize, rest.size()));
            List<String> ids = taken.stream().map(PlaylistTrackRepository.TrackIdRow::getMusicPieceId).toList();
            if (taken.size() == rest.size()) {
                return new QueueSlice(ids, null);
            }
            int lastPosition = taken.getLast().getPlaylistPosition();
            return new QueueSlice(ids, new QueueCursor(false, 0, null, lastPosition, null));
        }

        String start = cursor.startMusicPieceId();
        String last = cursor.lastMusicPieceId();
        // Ties on the key (two ids with the same hash) fall back to the id, so the order is total.
        Comparator<String> order = Comparator.<String>comparingLong(id -> shuffleKey(cursor.seed(), id))
                .thenComparing(Comparator.naturalOrder());

        List<String> ids = new ArrayList<>(pageSize);
        if (firstPage && start != null) {
            ids.add(start);
        }

        List<String> rest = rows.stream()
                .map(PlaylistTrackRepository.TrackIdRow::getMusicPieceId)
                .filter(id -> !id.equals(start))
                .filter(id -> last == null || order.compare(id, last) > 0)
                .sorted(order)
                .toList();
        int take = Math.min(pageSize - ids.size(), rest.size());
        ids.addAll(rest.subList(0, take));

        if (take == rest.size()) {
            return new QueueSlice(ids, null);
        }
        String newLast = take == 0 ? last : rest.get(take - 1);
        return new QueueSlice(ids, new QueueCursor(true, cursor.seed(), newLast, -1, start));
    }

    // Opaque to the client. "p:<lastPosition>" or "s:<seed>:<lastId>:<startId>" (empty for null),
    // URL-safe base64 since it travels in a query param. Public ids are base62 plus "_", so ":" is a safe separator.
    static String encodeCursor(QueueCursor cursor) {
        String raw = cursor.shuffle()
                ? "s:" + cursor.seed() + ":" + Objects.toString(cursor.lastMusicPieceId(), "")
                        + ":" + Objects.toString(cursor.startMusicPieceId(), "")
                : "p:" + cursor.lastPosition();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    static QueueCursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(":", -1);
            if (parts.length == 2 && parts[0].equals("p")) {
                int lastPosition = Integer.parseInt(parts[1]);
                if (lastPosition >= 0) {
                    return new QueueCursor(false, 0, null, lastPosition, null);
                }
            } else if (parts.length == 4 && parts[0].equals("s")) {
                return new QueueCursor(
                        true,
                        Long.parseLong(parts[1]),
                        parts[2].isEmpty() ? null : parts[2],
                        -1,
                        parts[3].isEmpty() ? null : parts[3]
                );
            }
        } catch (IllegalArgumentException ignored) {
            // Bad base64 or number (NumberFormatException is an IllegalArgumentException); falls through to the 400
        }
        throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "Invalid cursor", "cursor");
    }

    // Same seed + id always gives the same key, whatever else is in the playlist, so an added or removed
    // track never reshuffles the rest. SplitMix64's finalizer over the seed and the id's hash.
    static long shuffleKey(long seed, String musicPieceId) {
        long z = seed + 0x9E3779B97F4A7C15L * musicPieceId.hashCode();
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    // Same visibility as resolveAccess: owner, accepted member, or public playlist.
    private static void requireViewable(PlaylistRepository.@Nullable PlaylistWithCaller found, String userId) {
        if (found == null) {
            throw playlistNotFound("playlistId");
        }
        if (found.isOwner(userId) || found.isAcceptedMember() || found.playlist().getIsPublic()) {
            return;
        }
        if (found.member() != null && found.member().getStatus() == PlaylistMemberStatus.PENDING) {
            throw invitePending();
        }
        throw playlistNotFound("playlistId");
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
                throw invitePending();
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

    // Track-list cursor is just the last position sent. The client treats it as opaque, so this can change later.
    private static @Nullable String positionCursor(@Nullable Integer lastPosition) {
        return lastPosition == null ? null : lastPosition.toString();
    }

    private static int parsePositionCursor(String cursor) {
        try {
            int position = Integer.parseInt(cursor);
            if (position >= 0) {
                return position;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the 400
        }
        throw new AuxException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", "Invalid cursor", "cursor");
    }

    private static AuxException invitePending() {
        return new AuxException(
                HttpStatus.FORBIDDEN,
                "PLAYLIST_INVITE_PENDING",
                "Accept your invitation to access this playlist",
                "playlistId"
        );
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
