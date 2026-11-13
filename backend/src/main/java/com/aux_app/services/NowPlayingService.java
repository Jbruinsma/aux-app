package com.aux_app.services;

import com.aux_app.entity.MusicPieceEntity;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// What each user is listening to right now, as their player last reported it (PUT /api/users/me/now-playing).
// Only public pieces are kept, since friends see it. In memory on purpose: it's gone in minutes anyway, and a restart
// just shows everyone as not playing until their player reports again.
// An entry ends when the piece would have finished, or when the player reports a pause or a private piece. It isn't
// cleared when a live event stream closes (streams reconnect hourly, mid-song); readers show it only while the user
// is online (LiveEventService.isOnline), so a closed tab doesn't keep "playing".
@Service
public class NowPlayingService {

    // Covers buffering and the gap before the player reports the next piece
    static final long END_SLACK_SECONDS = 30;

    public record Listening(String publicMusicPieceId, Instant startedAt, Instant endsAt) {}

    // internal user id -> what they're playing
    private final Map<String, Listening> listening = new ConcurrentHashMap<>();
    private final Clock clock;

    public NowPlayingService() {
        this(Clock.systemUTC());
    }

    NowPlayingService(Clock clock) {
        this.clock = clock;
    }

    // `positionSeconds` is how far into the piece the player is, so a seek or a resume moves the start time
    public void update(String userId, MusicPieceEntity piece, int positionSeconds, boolean isPlaying) {
        if (!isPlaying || !Boolean.TRUE.equals(piece.getIsPublic())) {
            listening.remove(userId);
            return;
        }
        Instant now = clock.instant();
        int length = piece.getDurationSeconds() == null ? 0 : piece.getDurationSeconds();
        int position = Math.clamp(positionSeconds, 0, length);
        listening.put(userId, new Listening(
                piece.getPublicId(),
                now.minusSeconds(position),
                now.plusSeconds(length - position + END_SLACK_SECONDS)
        ));
    }

    public void clear(String userId) {
        listening.remove(userId);
    }

    // Null when they aren't playing anything public
    public @Nullable Listening current(String userId) {
        Listening entry = listening.get(userId);
        if (entry == null) return null;
        if (clock.instant().isAfter(entry.endsAt())) {
            listening.remove(userId, entry);
            return null;
        }
        return entry;
    }
}
