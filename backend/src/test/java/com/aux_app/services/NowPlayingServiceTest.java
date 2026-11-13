package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aux_app.entity.MusicPieceEntity;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

class NowPlayingServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-13T12:00:00Z");

    @Test
    void lastsUntilThePieceWouldEnd() {
        NowPlayingService service = new NowPlayingService(Clock.fixed(T0, ZoneOffset.UTC));
        service.update("u", piece(true, 200), 50, true);
        NowPlayingService.Listening entry = service.current("u");
        assertEquals("m_x", entry.publicMusicPieceId());
        assertEquals(T0.minusSeconds(50), entry.startedAt());
        assertEquals(T0.plusSeconds(150 + NowPlayingService.END_SLACK_SECONDS), entry.endsAt());

        NowPlayingService later = new NowPlayingService(Clock.fixed(entry.endsAt().plusSeconds(1), ZoneOffset.UTC));
        later.update("u", piece(true, 200), 0, true);
        assertEquals(entry.endsAt().plusSeconds(1), later.current("u").startedAt());
    }

    @Test
    void expires() {
        MutableClock clock = new MutableClock(T0);
        NowPlayingService service = new NowPlayingService(clock);
        service.update("u", piece(true, 60), 0, true);
        clock.now = T0.plusSeconds(60 + NowPlayingService.END_SLACK_SECONDS + 1);
        assertNull(service.current("u"));
    }

    @Test
    void pauseAndPrivatePiecesClearIt() {
        NowPlayingService service = new NowPlayingService(Clock.fixed(T0, ZoneOffset.UTC));
        service.update("u", piece(true, 60), 0, true);
        service.update("u", piece(true, 60), 10, false);
        assertNull(service.current("u"));

        service.update("u", piece(true, 60), 0, true);
        service.update("u", piece(false, 60), 0, true);
        assertNull(service.current("u"));
    }

    private static MusicPieceEntity piece(boolean isPublic, int seconds) {
        MusicPieceEntity piece = mock(MusicPieceEntity.class);
        when(piece.getIsPublic()).thenReturn(isPublic);
        when(piece.getDurationSeconds()).thenReturn(seconds);
        when(piece.getPublicId()).thenReturn("m_x");
        return piece;
    }

    private static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
