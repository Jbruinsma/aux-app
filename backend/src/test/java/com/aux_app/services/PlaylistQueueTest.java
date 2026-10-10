package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aux_app.error.AuxException;
import com.aux_app.repository.PlaylistTrackRepository.TrackIdRow;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

class PlaylistQueueTest {

    private static TrackIdRow row(String id, int position) {
        return new TrackIdRow() {
            public String getMusicPieceId() { return id; }
            public Integer getPlaylistPosition() { return position; }
        };
    }

    private static List<TrackIdRow> playlist(int size) {
        List<TrackIdRow> rows = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            rows.add(row("m_" + i, i));
        }
        return rows;
    }

    // Pages through the whole queue, sending each cursor through encode/decode like a client would
    private static List<String> playAll(List<TrackIdRow> rows, PlaylistService.QueueCursor first) {
        List<String> played = new ArrayList<>();
        PlaylistService.QueueSlice slice = PlaylistService.sliceQueue(rows, first, true, 50);
        played.addAll(slice.musicPieceIds());
        while (slice.next() != null) {
            PlaylistService.QueueCursor next = PlaylistService.decodeCursor(PlaylistService.encodeCursor(slice.next()));
            slice = PlaylistService.sliceQueue(rows, next, false, 50);
            played.addAll(slice.musicPieceIds());
        }
        return played;
    }

    @Test
    void shufflePlaysStartFirstThenEveryTrackOnce() {
        List<String> played = playAll(playlist(120), new PlaylistService.QueueCursor(true, 42, null, -1, "m_7"));

        assertEquals("m_7", played.getFirst());
        assertEquals(120, played.size());
        assertEquals(120, new HashSet<>(played).size());
    }

    @Test
    void plainOrderPlaysFromStartOnward() {
        List<String> played = playAll(playlist(120), new PlaylistService.QueueCursor(false, 0, null, 9, null));

        assertEquals(110, played.size());
        assertEquals("m_10", played.getFirst());
        assertEquals("m_119", played.getLast());
    }

    @Test
    void trackAddedMidShuffleNeverRepeatsOthers() {
        List<TrackIdRow> rows = playlist(120);
        PlaylistService.QueueSlice first = PlaylistService.sliceQueue(
                rows, new PlaylistService.QueueCursor(true, 7, null, -1, null), true, 50);

        rows.add(row("m_new", 120));
        PlaylistService.QueueSlice second = PlaylistService.sliceQueue(rows, first.next(), false, 50);

        HashSet<String> seen = new HashSet<>(first.musicPieceIds());
        for (String id : second.musicPieceIds()) {
            assertEquals(true, seen.add(id), id + " repeated");
        }
    }

    @Test
    void badCursorIs400() {
        assertThrows(AuxException.class, () -> PlaylistService.decodeCursor("not a cursor!"));
        assertThrows(AuxException.class, () -> PlaylistService.decodeCursor("cDotMQ")); // "p:-1"
    }

    @Test
    void lastPageHasNoCursor() {
        assertNull(PlaylistService.sliceQueue(
                playlist(3), new PlaylistService.QueueCursor(true, 1, null, -1, null), true, 50).next());
    }
}
