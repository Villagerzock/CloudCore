package net.villagerzock.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LinkAttemptLimiter {
    private static final int MAX_ATTEMPTS = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();

    public void check(String ipAddress) {
        Instant now = Instant.now();
        AttemptWindow current = attempts.compute(ipAddress, (ignored, previous) -> {
            if (previous == null || !now.isBefore(previous.startedAt().plus(WINDOW))) {
                return new AttemptWindow(now, 1);
            }
            return new AttemptWindow(previous.startedAt(), previous.count() + 1);
        });

        if (attempts.size() > CLEANUP_THRESHOLD) {
            attempts.entrySet().removeIf(entry ->
                    !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
        }

        if (current.count() > MAX_ATTEMPTS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many link attempts");
        }
    }

    public void clear(String ipAddress) {
        attempts.remove(ipAddress);
    }

    private record AttemptWindow(Instant startedAt, int count) {
    }
}
