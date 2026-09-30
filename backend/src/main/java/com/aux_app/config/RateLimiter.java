package com.aux_app.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.aux_app.error.AuxException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Per-IP token bucket for every request. Each bucket holds `limit` tokens and refills `limit` per minute,
// so short bursts are fine but the sustained rate is capped. /api/auth/** gets a tighter cap to slow credential stuffing.
// Throws AuxException, so a 429 comes back through AuxErrorHandler like every other error.
// ponytail: in-memory, single instance only; move to Redis/bucket4j if the backend ever runs more than one node
@Component
public class RateLimiter implements HandlerInterceptor {

    static final int DEFAULT_LIMIT = 120;
    static final int AUTH_LIMIT = 10;
    private static final long REFILL_NANOS = 60_000_000_000L; // time to refill an empty bucket

    private static final class Bucket {
        double tokens;
        long updated;

        Bucket(double tokens, long updated) {
            this.tokens = tokens;
            this.updated = updated;
        }
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;
    private volatile long lastSweep;

    public RateLimiter() {
        this(System::nanoTime);
    }

    // Tests pass a fake clock
    RateLimiter(LongSupplier clock) {
        this.clock = clock;
        this.lastSweep = clock.getAsLong();
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        long now = clock.getAsLong();
        sweep(now);

        boolean auth = request.getRequestURI().startsWith("/api/auth/");
        int limit = auth ? AUTH_LIMIT : DEFAULT_LIMIT;
        String key = (auth ? "auth:" : "all:") + request.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit, now));
        synchronized (bucket) {
            bucket.tokens = Math.min(limit, bucket.tokens + (double) (now - bucket.updated) * limit / REFILL_NANOS);
            bucket.updated = now;
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1;
                return true;
            }
            // Seconds until one whole token is back
            long wait = (long) Math.ceil((1 - bucket.tokens) * REFILL_NANOS / limit / 1e9);
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(wait));
        }
        throw new AuxException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Too many requests, try again later");
    }

    // A bucket idle for a full refill period is full again, so dropping it changes nothing and keeps the map small
    private void sweep(long now) {
        if (now - lastSweep < REFILL_NANOS) return;
        lastSweep = now;
        buckets.values().removeIf(b -> now - b.updated >= REFILL_NANOS);
    }
}
