package com.aux_app.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.aux_app.error.AuxException;

class RateLimiterTest {

    private final AtomicLong nanos = new AtomicLong();
    private final RateLimiter limiter = new RateLimiter(nanos::get);

    private boolean hit(String uri, String ip, MockHttpServletResponse response) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRemoteAddr(ip);
        return limiter.preHandle(request, response, new Object());
    }

    private boolean hit(String uri, String ip) {
        return hit(uri, ip, new MockHttpServletResponse());
    }

    @Test
    void blocksAuthBurstThenRefills() {
        for (int i = 0; i < RateLimiter.AUTH_LIMIT; i++) hit("/api/auth/login", "1.1.1.1");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuxException e = assertThrows(AuxException.class, () -> hit("/api/auth/login", "1.1.1.1", response));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        // 10 per minute = one token every 6 seconds
        assertEquals("6", response.getHeader(HttpHeaders.RETRY_AFTER));

        // Other IPs and non-auth routes have their own buckets
        hit("/api/auth/login", "2.2.2.2");
        hit("/api/users/me", "1.1.1.1");

        // After 6 seconds exactly one request gets through
        nanos.addAndGet(6_000_000_000L);
        hit("/api/auth/login", "1.1.1.1");
        assertThrows(AuxException.class, () -> hit("/api/auth/login", "1.1.1.1"));
    }

    @Test
    void uploadsHaveTheirOwnTighterBucket() {
        for (int i = 0; i < RateLimiter.UPLOAD_LIMIT; i++) upload("1.1.1.1");
        assertThrows(AuxException.class, () -> upload("1.1.1.1"));

        // JSON requests from the same IP still go through
        hit("/api/users/me", "1.1.1.1");
    }

    private boolean upload(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/music-pieces/create");
        request.setRemoteAddr(ip);
        request.setContentType("multipart/form-data; boundary=x");
        return limiter.preHandle(request, new MockHttpServletResponse(), new Object());
    }
}
