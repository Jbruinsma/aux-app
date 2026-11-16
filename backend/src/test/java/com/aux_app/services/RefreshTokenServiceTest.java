package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.aux_app.entity.RefreshTokenEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.RefreshTokenRepository;

class RefreshTokenServiceTest {

    // A clock the test can move forward
    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-11-16T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration by) { now = now.plus(by); }
    }

    private final Map<String, RefreshTokenEntity> rows = new HashMap<>();
    private final RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
    private final MovableClock clock = new MovableClock();
    private final RefreshTokenService service = new RefreshTokenService(repo, clock);

    RefreshTokenServiceTest() {
        // Back the mock with a map so the service sees its own writes
        when(repo.save(any())).thenAnswer(call -> {
            RefreshTokenEntity token = call.getArgument(0);
            rows.put(token.getTokenHash(), token);
            return token;
        });
        when(repo.findById(anyString())).thenAnswer(call -> Optional.ofNullable(rows.get(call.<String>getArgument(0))));
        doAnswer(call -> rows.remove(call.<RefreshTokenEntity>getArgument(0).getTokenHash())).when(repo).delete(any());
        when(repo.deleteFamily(anyString())).thenAnswer(call -> {
            String family = call.getArgument(0);
            int before = rows.size();
            rows.values().removeIf(t -> t.getFamilyId().equals(family));
            return before - rows.size();
        });
    }

    private static void assertInvalid(Runnable call) {
        AuxException e = assertThrows(AuxException.class, call::run);
        assertEquals("INVALID_REFRESH_TOKEN", e.getDetails().code());
    }

    @Test
    void rotateReturnsSuccessorInSameFamilyWithSlidingExpiry() {
        RefreshTokenService.Issued first = service.issue("u1", "fam");
        clock.advance(Duration.ofDays(10));

        RefreshTokenService.Rotation rotation = service.rotate(first.token());

        assertEquals("u1", rotation.userId());
        assertEquals("fam", rotation.familyId());
        assertNotEquals(first.token(), rotation.next().token());
        assertEquals(clock.now.plus(RefreshTokenService.TTL), rotation.next().expiresAt());
        // The successor works
        assertEquals("u1", service.rotate(rotation.next().token()).userId());
    }

    @Test
    void storesOnlyAHash() {
        RefreshTokenService.Issued issued = service.issue("u1", "fam");
        assertEquals(RefreshTokenService.hash(issued.token()), rows.keySet().iterator().next());
        assertNotEquals(issued.token(), rows.keySet().iterator().next());
    }

    @Test
    void reuseWithinGraceIsTreatedAsARetry() {
        RefreshTokenService.Issued first = service.issue("u1", "fam");
        service.rotate(first.token());
        clock.advance(RefreshTokenService.REUSE_GRACE.minusSeconds(1));

        // e.g. the first response was lost, or two tabs refreshed at once
        assertEquals("fam", service.rotate(first.token()).familyId());
    }

    @Test
    void reuseAfterGraceEndsTheWholeFamily() {
        RefreshTokenService.Issued first = service.issue("u1", "fam");
        RefreshTokenService.Issued other = service.issue("u1", "other-device");
        RefreshTokenService.Rotation rotation = service.rotate(first.token());
        clock.advance(RefreshTokenService.REUSE_GRACE.plusSeconds(1));

        assertInvalid(() -> service.rotate(first.token()));
        // The legitimate successor dies with it, so whoever stole the old token is locked out too
        assertInvalid(() -> service.rotate(rotation.next().token()));
        // Other sessions are untouched
        assertEquals("other-device", service.rotate(other.token()).familyId());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshTokenService.Issued first = service.issue("u1", "fam");
        clock.advance(RefreshTokenService.TTL);

        assertInvalid(() -> service.rotate(first.token()));
    }

    @Test
    void unknownTokenIsRejected() {
        assertInvalid(() -> service.rotate("not-a-token"));
    }

    @Test
    void revokeEndsTheSession() {
        RefreshTokenService.Issued first = service.issue("u1", "fam");
        service.revoke(first.token());

        assertInvalid(() -> service.rotate(first.token()));
    }
}
