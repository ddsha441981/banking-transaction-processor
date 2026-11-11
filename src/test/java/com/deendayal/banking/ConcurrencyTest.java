package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ConcurrencyTest {

    private static final int THREADS = 16;

    private final BankingService service = new BankingService(new InMemoryAccountRepository());

    @Test
    void concurrentDepositsAreNotLost() throws Exception {
        service.openAccount("ACC-1", Money.ZERO);

        runConcurrently(THREADS, () -> {
            for (int i = 0; i < 500; i++) {
                service.deposit("ACC-1", Money.of("1.00"));
            }
        });

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("8000.00"));
        assertThat(service.getTransactionHistory("ACC-1")).hasSize(8000);
    }

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() throws Exception {
        service.openAccount("ACC-1", Money.of("100.00"));
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(THREADS, () -> {
            for (int i = 0; i < 25; i++) {
                try {
                    service.withdraw("ACC-1", Money.of("1.00"));
                    succeeded.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                }
            }
        });
        assertThat(succeeded.get()).isEqualTo(100);
        assertThat(rejected.get()).isEqualTo(300);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.ZERO);
    }

    private static void runConcurrently(int threads, Runnable task) throws Exception {
        runConcurrently(Collections.nCopies(threads, task));
    }

    private static void runConcurrently(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    task.run();
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void oppositeTransfersRunningTogetherConserveMoneyAndDoNotDeadlock() {
        service.openAccount("A", Money.of("1000.00"));
        service.openAccount("B", Money.of("1000.00"));

        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            tasks.add(() -> repeat(200, () -> transferQuietly("A", "B")));
            tasks.add(() -> repeat(200, () -> transferQuietly("B", "A")));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> runConcurrently(tasks));

        Money total = service.getBalance("A").plus(service.getBalance("B"));
        assertThat(total).isEqualTo(Money.of("2000.00"));
        assertThat(lastBalanceAfter("A")).isEqualTo(service.getBalance("A"));
        assertThat(lastBalanceAfter("B")).isEqualTo(service.getBalance("B"));
        assertThat(countOf("A", TransactionType.TRANSFER_OUT))
                .isEqualTo(countOf("B", TransactionType.TRANSFER_IN));
        assertThat(countOf("B", TransactionType.TRANSFER_OUT))
                .isEqualTo(countOf("A", TransactionType.TRANSFER_IN));
    }

    private void transferQuietly(String from, String to) {
        try {
            service.transfer(from, to, Money.of("1.00"));
        } catch (InsufficientFundsException ignored) {
            // an account can run dry under load; that rejection is correct behaviour
        }
    }

    private static void repeat(int times, Runnable action) {
        for (int i = 0; i < times; i++) {
            action.run();
        }
    }

    private Money lastBalanceAfter(String id) {
        List<Transaction> history = service.getTransactionHistory(id);
        return history.get(history.size() - 1).balanceAfter();
    }

    private long countOf(String id, TransactionType type) {
        return service.getTransactionHistory(id).stream().filter(t -> t.type() == type).count();
    }
}