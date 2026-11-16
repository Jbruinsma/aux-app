package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class CachedMapTest {

    @Test
    void reloadsOnlyWhenExpiredOrInvalidated() {
        AtomicLong now = new AtomicLong();
        AtomicInteger loads = new AtomicInteger();
        CachedMap<String, Integer> cache = new CachedMap<>(Duration.ofNanos(10), 100, now::get);

        assertEquals(1, cache.get("a", k -> loads.incrementAndGet()));
        now.set(9);
        assertEquals(1, cache.get("a", k -> loads.incrementAndGet()));
        now.set(10);
        assertEquals(2, cache.get("a", k -> loads.incrementAndGet()));
        assertEquals(3, cache.get("b", k -> loads.incrementAndGet()));

        cache.invalidate("a"::equals);
        assertEquals(4, cache.get("a", k -> loads.incrementAndGet()));
        assertEquals(3, cache.get("b", k -> loads.incrementAndGet()));
    }

    // A load that started before invalidate() read the old data, so storing it would undo the invalidation
    @Test
    void dropsALoadThatRacedAnInvalidation() {
        CachedMap<String, Integer> cache = new CachedMap<>(Duration.ofMinutes(1), 100, () -> 0);

        assertEquals(1, cache.get("a", k -> {
            cache.invalidate("a"::equals);
            return 1;
        }));
        assertEquals(2, cache.get("a", k -> 2));
    }

    @Test
    void staysWithinMaxEntries() {
        AtomicLong now = new AtomicLong();
        CachedMap<Integer, Integer> cache = new CachedMap<>(Duration.ofNanos(10), 2, now::get);

        cache.get(1, k -> k);
        now.set(10);
        cache.get(2, k -> k);
        cache.get(3, k -> k); // 1 has expired, so it makes room
        assertEquals(2, cache.get(2, k -> -1));
        cache.get(4, k -> k); // nothing expired: everything goes
        assertEquals(-3, cache.get(3, k -> -3));
    }
}
