package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import com.aux_app.error.AuxException;
import org.junit.jupiter.api.Test;

class PlaybackEventServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private static String code(Runnable r) {
        return assertThrows(AuxException.class, r::run).getDetails().code();
    }

    @Test
    void creditCappedByElapsedTimeAndPieceLength() {
        // Claims 600s, token is 100s old: only 100s credited
        assertEquals(100, PlaybackEventService.creditedSeconds(NOW.minusSeconds(100), NOW, 600, 300, null));
        // Token is an hour old, piece is 200s: only 200s credited
        assertEquals(200, PlaybackEventService.creditedSeconds(NOW.minusSeconds(3600), NOW, 600, 200, null));
    }

    @Test
    void shortListensRejected() {
        assertEquals("PLAY_TOO_SHORT", code(() -> PlaybackEventService.creditedSeconds(NOW.minusSeconds(10), NOW, 600, 300, null)));
        // A 10s piece only needs 10s
        assertEquals(10, PlaybackEventService.creditedSeconds(NOW.minusSeconds(10), NOW, 10, 10, null));
    }

    @Test
    void overlappingPlaysRejected() {
        // Previous play ended 60s ago; claiming 120s would start before it
        assertEquals("PLAY_OVERLAPS", code(() -> PlaybackEventService.creditedSeconds(NOW.minusSeconds(600), NOW, 120, 300, NOW.minusSeconds(60))));
        // Token prefetched during the previous play, but this listen started after it ended
        assertEquals(50, PlaybackEventService.creditedSeconds(NOW.minusSeconds(600), NOW, 50, 300, NOW.minusSeconds(60)));
    }
}
