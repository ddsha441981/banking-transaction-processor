package com.deendayal.banking.api;

import com.deendayal.banking.MutableClock;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyStoreTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T09:00:00Z"));
    private final IdempotencyStore store = new IdempotencyStore(clock, Duration.ofHours(24));
    private final AtomicInteger executions = new AtomicInteger();

    @Test
    void firstCallRunsTheOperation() {
        IdempotencyStore.Result<String> result = call("k1", "deposit A 5.00");

        assertThat(result.value()).isEqualTo("result-1");
        assertThat(result.replayed()).isFalse();
        assertThat(executions).hasValue(1);
    }

    @Test
    void sameKeyAndSameRequestReplaysTheFirstResultWithoutRunningAgain() {
        call("k1", "deposit A 5.00");

        IdempotencyStore.Result<String> second = call("k1", "deposit A 5.00");

        assertThat(second.value()).isEqualTo("result-1");
        assertThat(second.replayed()).isTrue();
        assertThat(executions).hasValue(1);
    }

    @Test
    void sameKeyWithADifferentRequestIsRejectedAndNothingRuns() {
        call("k1", "deposit A 5.00");

        assertThatThrownBy(() -> call("k1", "deposit A 6.00"))
                .isInstanceOf(IdempotencyKeyReusedException.class);
        assertThat(executions).hasValue(1);
    }

    @Test
    void differentKeysAreIndependent() {
        call("k1", "deposit A 5.00");
        call("k2", "deposit A 5.00");

        assertThat(executions).hasValue(2);
    }

    @Test
    void aFailedOperationReleasesTheKeySoTheRequestCanBeRetried() {
        assertThatThrownBy(() -> store.execute("k1", List.of("r"), () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        IdempotencyStore.Result<String> retry = call("k1", "r");

        assertThat(retry.replayed()).isFalse();
        assertThat(executions).hasValue(1);
    }

    @Test
    void aSecondRequestWhileTheFirstIsRunningIsRejectedAndTheFirstStillCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<IdempotencyStore.Result<String>> first = pool.submit(() ->
                    store.execute("k1", List.of("r"), () -> {
                        started.countDown();
                        await(release);
                        return "done";
                    }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> call("k1", "r"))
                    .isInstanceOf(IdempotencyRequestInProgressException.class);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).value()).isEqualTo("done");
            assertThat(call("k1", "r").replayed()).isTrue();
            assertThat(executions).hasValue(0);
        } finally {
            pool.shutdownNow();
        }
    }

    //Experiment
//    @Test
    @RepeatedTest(50)
    void manyThreadsRacingOnOneKeyRunTheOperationExactlyOnce() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    try {
                        return call("k1", "r").value();
                    } catch (IdempotencyRequestInProgressException e) {
                        return "in-progress";
                    }
                }));
            }
            go.countDown();

            for (Future<String> future : futures) {
                assertThat(future.get(10, TimeUnit.SECONDS)).isIn("result-1", "in-progress");
            }
            assertThat(executions).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aKeyIsRememberedForTheTimeToLiveAndThenForgotten() {
        call("k1", "r");

        clock.advance(Duration.ofHours(23));
        assertThat(call("k1", "r").replayed()).isTrue();

        clock.advance(Duration.ofHours(2));
        assertThat(call("k1", "r").replayed()).isFalse();
        assertThat(executions).hasValue(2);
    }

    @Test
    void purgingRemovesOnlyExpiredEntries() {
        call("k1", "r");
        call("k2", "r");
        clock.advance(Duration.ofHours(25));
        call("k3", "r");

        store.purgeExpired();

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void missingBlankOrTooLongKeysAreRejected() {
        for (String bad : new String[]{null, "", "  ", "x".repeat(256)}) {
            assertThatIllegalArgumentException().isThrownBy(() -> call(bad, "r"));
        }

        assertThat(store.size()).isZero();
        assertThat(executions).hasValue(0);
    }

    private IdempotencyStore.Result<String> call(String key, String request) {
        return store.execute(key, List.of(request), () -> "result-" + executions.incrementAndGet());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}