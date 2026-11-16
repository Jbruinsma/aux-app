package com.aux_app.services;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

// Values kept in memory per key, reloaded once older than `ttl`. invalidate() drops matching keys, and a load that
// was already running when invalidate() was called isn't stored, so it can't put back what invalidate() just dropped.
// Concurrent misses on one key each load; fine for cheap loads. Over `maxEntries`, expired entries go first, then
// everything. In-process on purpose, like CachedValue: there is one backend instance.
public final class CachedMap<K, V> {

    private record Entry<V>(V value, long loadedAt) {}

    private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int maxEntries;
    private final LongSupplier clock;
    private long version; // bumped by invalidate(); guarded by `this`

    public CachedMap(Duration ttl, int maxEntries) {
        this(ttl, maxEntries, System::nanoTime);
    }

    // Tests pass a fake clock
    CachedMap(Duration ttl, int maxEntries, LongSupplier clock) {
        this.ttlNanos = ttl.toNanos();
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public V get(K key, Function<K, V> load) {
        Entry<V> entry = entries.get(key);
        if (entry != null && clock.getAsLong() - entry.loadedAt() < ttlNanos) {
            return entry.value();
        }
        long seen;
        synchronized (this) {
            seen = version;
        }
        V value = load.apply(key);
        synchronized (this) {
            if (version == seen) {
                long now = clock.getAsLong();
                if (entries.size() >= maxEntries) {
                    entries.values().removeIf(e -> now - e.loadedAt() >= ttlNanos);
                    if (entries.size() >= maxEntries) {
                        entries.clear();
                    }
                }
                entries.put(key, new Entry<>(value, now));
            }
        }
        return value;
    }

    public synchronized void invalidate(Predicate<K> matches) {
        version++;
        entries.keySet().removeIf(matches);
    }
}
