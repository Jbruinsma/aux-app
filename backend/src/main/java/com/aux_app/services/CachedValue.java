package com.aux_app.services;

import java.time.Duration;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

// One value kept in memory, reloaded once it's older than `ttl`, or by reload(). When get() has to load, callers
// wait while one thread loads, so a burst of requests runs the load once. In-process on purpose: there is one backend instance.
public final class CachedValue<T> {

    private final Supplier<T> load;
    private final long ttlNanos;
    private final LongSupplier clock;
    private T value;
    private long loadedAt;
    private boolean fresh;

    public CachedValue(Duration ttl, Supplier<T> load) {
        this(ttl, load, System::nanoTime);
    }

    // Tests pass a fake clock
    CachedValue(Duration ttl, Supplier<T> load, LongSupplier clock) {
        this.load = load;
        this.ttlNanos = ttl.toNanos();
        this.clock = clock;
    }

    public synchronized T get() {
        long now = clock.getAsLong();
        if (!fresh || now - loadedAt >= ttlNanos) {
            value = load.get();
            loadedAt = now;
            fresh = true;
        }
        return value;
    }

    // Loads a new value without holding the lock, so get() keeps returning the old one until the new one is ready
    public void reload() {
        T next = load.get();
        synchronized (this) {
            value = next;
            loadedAt = clock.getAsLong();
            fresh = true;
        }
    }
}
