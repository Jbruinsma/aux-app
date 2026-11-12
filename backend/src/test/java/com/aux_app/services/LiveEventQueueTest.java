package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.LiveEventService.Section;
import com.aux_app.services.LiveEventService.Update;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Updates queue per user and section, so a burst costs one task and one query per section, not one per update
class LiveEventQueueTest {

    @Test
    void burstOfUpdatesIsSentOnce() {
        UserRepository users = mock(UserRepository.class);
        when(users.findLibrary("u1")).thenReturn(List.of());
        List<Runnable> tasks = new ArrayList<>();
        LiveEventService live = new LiveEventService(mock(PlaylistMembersService.class), mock(PlaybackEventService.class),
                users, mock(MusicPieceRepository.class), mock(NotificationService.class), mock(CompatibilityService.class),
                tasks::add);
        live.connect("u1", Instant.now().plusSeconds(3600)); // snapshot reads the library once

        for (int i = 0; i < 10; i++) live.onUpdate(new Update(Set.of("u1"), Set.of(Section.LIBRARY)));
        live.onUpdate(new Update(Set.of("offline"), Set.of(Section.LIBRARY)));
        assertEquals(1, tasks.size());

        tasks.removeFirst().run();
        verify(users, times(2)).findLibrary("u1");

        // Once sent, the next update queues a new task
        live.onUpdate(new Update(Set.of("u1"), Set.of(Section.LIBRARY)));
        assertEquals(1, tasks.size());
    }
}
