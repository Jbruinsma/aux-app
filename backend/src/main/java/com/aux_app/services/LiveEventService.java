package com.aux_app.services;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.aux_app.dto.music_piece.UploadedMusicPiecesResponse;
import com.aux_app.dto.users.RecentListeningResponse;
import com.aux_app.dto.users.TopArtistsResponse;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.UserRepository;

// Keeps every open GET /api/users/me/events stream and pushes fresh sections down them when data changes.
// In memory, so it only works with one backend instance (fine while the DB is SQLite).
//
// Flow:
//   1. connect() registers an emitter and sends a snapshot of every Section
//   2. A service changes data, then publishes new LiveEventService.Update(userIds, sections)
//      through Spring's ApplicationEventPublisher. Services never call this class directly
//   3. onUpdate() runs after that transaction commits, re-reads each section, and sends it to each user's emitters
//
// A section is always sent whole, never as a diff, so the client just replaces what it has.
//
// Where updates are published (who gets notified -> which sections):
//   PlaybackEventService.recordPlay                 caller                      -> RECENTS, TOP_ARTISTS
//   MusicPieceController.createMusicPiece           caller                      -> UPLOADS
//   PlaylistService.createPlaylist                  owner                       -> LIBRARY
//   PlaylistService.editPlaylist                    everyone on the playlist    -> LIBRARY, INVITES, NOTIFICATIONS (name, cover)
//   PlaylistService.deletePlaylist                  everyone on the playlist    -> LIBRARY, INVITES, NOTIFICATIONS
//   PlaylistService.addMusicPiecesToPlaylist        everyone on the playlist    -> LIBRARY (totalPieces)
//                                                   owner + members but adder   -> NOTIFICATIONS (TRACKS_ADDED)
//   PlaylistService.removeMusicPieceFromPlaylist    everyone on the playlist    -> LIBRARY (totalPieces)
//   PlaylistService.save/unsavePlaylist             caller                      -> LIBRARY
//   PlaylistMembersService.invite                   invitee                     -> INVITES, NOTIFICATIONS (PLAYLIST_INVITE)
//   PlaylistMembersService.acceptInvite             caller                      -> INVITES, LIBRARY, NOTIFICATIONS
//                                                   owner                       -> NOTIFICATIONS (INVITE_ACCEPTED)
//   PlaylistMembersService.declineInvite            caller                      -> INVITES, NOTIFICATIONS
//   PlaylistMembersService.leave                    caller                      -> LIBRARY, NOTIFICATIONS
//   PlaylistMembersService.updatePermission         that member                 -> INVITES, NOTIFICATIONS (a pending invite shows it)
//   PlaylistMembersService.remove                   removed user                -> INVITES, LIBRARY, NOTIFICATIONS
//   NotificationService.markRead/markAllRead        caller                      -> NOTIFICATIONS (other tabs' badges)
// "Everyone on the playlist" is PlaylistRepository.findAudience: owner, members (pending too) and savers.
//
// Not covered yet: username or profile picture changes (they show in other users' library and invites).
@Service
public class LiveEventService {

    private static final Logger log = LoggerFactory.getLogger(LiveEventService.class);

    // Event names the frontend listens for
    public enum Section {
        INVITES("invites"),
        RECENTS("recents"),
        TOP_ARTISTS("top-artists"),
        LIBRARY("library"),
        UPLOADS("uploads"),
        NOTIFICATIONS("notifications");

        public final String eventName;

        Section(String eventName) {
            this.eventName = eventName;
        }
    }

    // What services publish. userIds are internal user ids (UserEntity.getUserId()), not public ones
    public record Update(Set<String> userIds, Set<Section> sections) {}

    // userId -> that user's open streams, one per tab
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final PlaylistMembersService playlistMembers;
    private final PlaybackEventService playbackEvents;
    private final UserRepository users;
    private final MusicPieceRepository musicPieces;
    private final NotificationService notifications;

    public LiveEventService(
            PlaylistMembersService playlistMembers,
            PlaybackEventService playbackEvents,
            UserRepository users,
            MusicPieceRepository musicPieces,
            NotificationService notifications
    ) {
        this.playlistMembers = playlistMembers;
        this.playbackEvents = playbackEvents;
        this.users = users;
        this.musicPieces = musicPieces;
        this.notifications = notifications;
    }

    // Called by GET /api/users/me/events. The stream ends when the token does: tokens last 1 hour and can't be
    // revoked, so an open stream must not outlive one. The client then reconnects with a current token.
    public SseEmitter connect(String userId, Instant tokenExpiresAt) {
        long millisLeft = Duration.between(Instant.now(), tokenExpiresAt).toMillis();
        SseEmitter emitter = new SseEmitter(Math.max(millisLeft, 1));

        // compute() is atomic per key, so a tab closing at the same moment can't drop this one
        emitters.compute(userId, (id, open) -> {
            Set<SseEmitter> set = open == null ? ConcurrentHashMap.newKeySet() : open;
            set.add(emitter);
            return set;
        });

        // Whichever way the stream ends (client left, token expired, write failed), forget it
        emitter.onCompletion(() -> remove(userId, emitter));
        // complete() ends the stream normally. Without it Spring raises AsyncRequestTimeoutException, and
        // AuxErrorHandler can't write its JSON error into an event stream
        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> remove(userId, emitter));

        // Snapshot. Sends before the handler returns are buffered by Spring and flushed once the stream opens
        for (Section section : Section.values()) {
            send(userId, emitter, event(section, load(userId, section)));
        }

        return emitter;
    }

    // AFTER_COMMIT (the default) so the re-read sees the new data, and a rolled-back change reaches no one.
    // fallbackExecution: most service methods aren't @Transactional (each repository call commits on its own);
    // for those the update runs right away, which is fine because they publish after their last write.
    // @Async so a slow client never holds up the request that made the change.
    @Async
    @TransactionalEventListener(fallbackExecution = true)
    public void onUpdate(Update update) {
        for (String userId : update.userIds()) {
            Set<SseEmitter> open = emitters.get(userId);
            // Offline users cost no queries
            if (open == null) continue;

            for (Section section : update.sections()) {
                // One query per user and section, however many tabs they have open
                SseEmitter.SseEventBuilder event = event(section, load(userId, section));
                for (SseEmitter emitter : open) {
                    send(userId, emitter, event);
                }
            }
        }
    }

    // A comment line every 25 seconds keeps proxies from closing a quiet stream, and finds clients that left
    // without saying so (the write fails)
    @Scheduled(fixedRate = 25_000)
    public void heartbeat() {
        emitters.forEach((userId, open) -> {
            for (SseEmitter emitter : open) {
                send(userId, emitter, SseEmitter.event().comment("ping"));
            }
        });
    }

    // The same data the old REST endpoints returned, so the frontend can reuse its row code
    private Object load(String userId, Section section) {
        return switch (section) {
            case INVITES -> playlistMembers.retrievePlaylistInvites(userId);
            case RECENTS -> new RecentListeningResponse(playbackEvents.retrieveRecentPlaybacks(userId));
            case TOP_ARTISTS -> new TopArtistsResponse(users.findTopArtists(userId));
            case LIBRARY -> users.findLibrary(userId);
            case UPLOADS -> UploadedMusicPiecesResponse.of(musicPieces.findUploadsWithArtist(userId));
            case NOTIFICATIONS -> notifications.page(userId, null);
        };
    }

    private static SseEmitter.SseEventBuilder event(Section section, Object payload) {
        return SseEmitter.event().name(section.eventName).data(payload);
    }

    private void send(String userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            // SseEmitter isn't safe to write from two threads at once (heartbeat vs update)
            synchronized (emitter) {
                emitter.send(event);
            }
        } catch (IOException | IllegalStateException e) {
            // IOException: client is gone. IllegalStateException: stream already completed.
            // Spring finishes the request itself, so just forget the emitter
            log.debug("Dropping event stream for user {}", userId, e);
            remove(userId, emitter);
        }
    }

    private void remove(String userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (id, open) -> {
            open.remove(emitter);
            return open.isEmpty() ? null : open;
        });
    }
}
