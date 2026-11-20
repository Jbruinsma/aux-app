package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.aux_app.dto.users.FollowListPage;
import com.aux_app.dto.users.OnboardingStep;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.FollowRepository.FollowListRow;
import com.aux_app.repository.UserRepository;

class FollowListServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final FollowListService service = new FollowListService(users, follows);

    private static UserEntity user(String id) {
        return new UserEntity(id, id + "@example.com", "hash", OnboardingStep.DONE);
    }

    private static List<FollowListRow> rows(int count) {
        List<FollowListRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new FollowListRow(user("u" + i), Instant.parse("2026-01-01T00:00:00Z").minusSeconds(i), false, false));
        }
        return rows;
    }

    @Test
    void cursorRoundTripsAtFullPrecision() {
        Instant when = Instant.parse("2026-03-04T05:06:07.123456789Z");
        FollowListRow row = new FollowListRow(user("u1"), when, false, false);

        FollowListService.Cursor cursor = FollowListService.decodeCursor(FollowListService.encodeCursor(row));

        assertEquals(when, cursor.followedAt());
        assertEquals("u1", cursor.userId());
    }

    @Test
    void badCursorIs400() {
        for (String bad : new String[] {"!!!", "bm90LWEtY3Vyc29y", "eHx5"}) {
            AuxException e = assertThrows(AuxException.class, () -> FollowListService.decodeCursor(bad), bad);
            assertEquals(400, e.getStatus().value());
        }
    }

    @Test
    void unknownUserIs404() {
        when(users.findByUsernameIgnoreCase("nobody")).thenReturn(null);

        AuxException e = assertThrows(AuxException.class, () -> service.followers("nobody", null, null));

        assertEquals(404, e.getStatus().value());
    }

    @Test
    void fullPageGivesNextCursorAndDropsTheExtraRow() {
        when(users.findByUsernameIgnoreCase("justin")).thenReturn(user("target"));
        // The service asks for PAGE_SIZE + 1 rows; getting all of them back means there is another page
        when(follows.findFollowersPage(eq("target"), eq(""), any(), any(), eq(FollowListService.PAGE_SIZE + 1)))
                .thenReturn(rows(FollowListService.PAGE_SIZE + 1));

        FollowListPage page = service.followers("justin", null, null);

        assertEquals(FollowListService.PAGE_SIZE, page.users().size());
        assertNotNull(page.nextCursor());
    }

    @Test
    void shortPageHasNoNextCursor() {
        when(users.findByUsernameIgnoreCase("justin")).thenReturn(user("target"));
        when(follows.findFollowingPage(eq("target"), eq("viewer"), any(), any(), anyInt())).thenReturn(rows(3));

        FollowListPage page = service.following("justin", user("viewer"), null);

        assertEquals(3, page.users().size());
        assertNull(page.nextCursor());
    }

    @Test
    void cursorFromThePreviousPageIsPassedToTheQuery() {
        when(users.findByUsernameIgnoreCase("justin")).thenReturn(user("target"));
        when(follows.findFollowersPage(eq("target"), eq(""), any(), any(), anyInt()))
                .thenReturn(rows(FollowListService.PAGE_SIZE + 1));
        String cursor = service.followers("justin", null, null).nextCursor();

        service.followers("justin", null, cursor);

        // Last row kept on page one is index PAGE_SIZE - 1
        verify(follows).findFollowersPage(eq("target"), eq(""),
                eq(Instant.parse("2026-01-01T00:00:00Z").minusSeconds(FollowListService.PAGE_SIZE - 1)),
                eq("u" + (FollowListService.PAGE_SIZE - 1)), eq(FollowListService.PAGE_SIZE + 1));
    }
}
