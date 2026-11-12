package com.deendayal.banking.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Makes retries safe: the first request with a given key runs, later identical requests get the
 * first result back instead of running again. The key is claimed atomically before the operation
 * starts, so simultaneous retries cannot both execute. Framework-free; time comes from a Clock.
 */
public class IdempotencyStore {

    private static final int MAX_KEY_LENGTH = 255;
    private static final long PURGE_EVERY_N_CALLS = 1000;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong calls = new AtomicLong();
    private final Clock clock;
    private final Duration timeToLive;

    public IdempotencyStore(Clock clock, Duration timeToLive) {
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
        this.timeToLive = Objects.requireNonNull(timeToLive, "Time to live must not be null");
    }

    public record Result<T>(T value, boolean replayed) {
    }

    /**
     * @param fingerprint what makes two requests "the same" (operation plus parsed values)
     * @param operation   runs at most once per key; if it throws, the key is released
     */
    public <T> Result<T> execute(String key, List<String> fingerprint, Supplier<T> operation) {
        requireValidKey(key);
        Objects.requireNonNull(fingerprint, "Fingerprint must not be null");
        Objects.requireNonNull(operation, "Operation must not be null");
        if (calls.incrementAndGet() % PURGE_EVERY_N_CALLS == 0) {
            purgeExpired();
        }

        Instant now = clock.instant();
        Entry mine = new Entry(List.copyOf(fingerprint), now);
        while (true) {
            Entry existing = entries.putIfAbsent(key, mine);
            if (existing == null) {
                return runAndRecord(key, mine, operation);
            }
            if (existing.isExpiredAt(now, timeToLive)) {
                entries.remove(key, existing); // stale record: forget it and try to claim the key again
                continue;
            }
            return replayOrReject(key, existing, mine.fingerprint);
        }
    }

    /** Drops finished entries older than the time to live. Also runs periodically on its own. */
    public void purgeExpired() {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> entry.isExpiredAt(now, timeToLive));
    }

    public int size() {
        return entries.size();
    }

    private <T> Result<T> runAndRecord(String key, Entry mine, Supplier<T> operation) {
        boolean completed = false;
        try {
            T value = operation.get();
            mine.complete(value);
            completed = true;
            return new Result<>(value, false);
        } finally {
            if (!completed) {
                entries.remove(key, mine); // the operation failed, so release the key for a retry
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Result<T> replayOrReject(String key, Entry existing, List<String> fingerprint) {
        if (!existing.fingerprint.equals(fingerprint)) {
            throw new IdempotencyKeyReusedException(key);
        }
        if (!existing.done) {
            throw new IdempotencyRequestInProgressException(key);
        }
        return new Result<>((T) existing.value, true);
    }

    private static void requireValidKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "Idempotency-Key must be between 1 and " + MAX_KEY_LENGTH + " characters");
        }
    }

    private static final class Entry {
        private final List<String> fingerprint;
        private final Instant createdAt;
        // value is written before the volatile flag, so a reader that sees done also sees value
        private volatile Object value;
        private volatile boolean done;

        private Entry(List<String> fingerprint, Instant createdAt) {
            this.fingerprint = fingerprint;
            this.createdAt = createdAt;
        }

        private void complete(Object result) {
            this.value = result;
            this.done = true;
        }

        private boolean isExpiredAt(Instant now, Duration timeToLive) {
            return done && !createdAt.plus(timeToLive).isAfter(now);
        }
    }
}