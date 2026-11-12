package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class CachedValueTest {

    @Test
    void reloadsOnlyWhenExpiredOrAsked() {
        AtomicLong now = new AtomicLong();
        AtomicInteger loads = new AtomicInteger();
        CachedValue<Integer> cache = new CachedValue<>(Duration.ofNanos(10), loads::incrementAndGet, now::get);

        assertEquals(1, cache.get());
        now.set(9);
        assertEquals(1, cache.get());
        now.set(10);
        assertEquals(2, cache.get());
        cache.reload();
        assertEquals(3, cache.get());
        assertEquals(3, loads.get());
    }
}
