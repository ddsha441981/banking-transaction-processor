package com.deendayal.banking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentApiTest {

    private static final int REQUESTS_PER_THREAD = 100;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void oppositeTransfersConserveMoneyAndKeepLedgersConsistent() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            open("A", "1000.00");
            open("B", "1000.00");

            List<Callable<List<Integer>>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(() -> transfers("A", "B"));
                tasks.add(() -> transfers("B", "A"));
            }
            List<Integer> statuses = runConcurrently(tasks);

            // 1600 transfers in total, 800 each way; neither account can ever run dry
            assertThat(statuses).hasSize(1600).containsOnly(200);
            assertThat(balance("A")).isEqualByComparingTo("1000.00");
            assertThat(balance("B")).isEqualByComparingTo("1000.00");
            // every transfer wrote one entry on each side
            assertThat(historySize("A")).isEqualTo(1600);
            assertThat(historySize("B")).isEqualTo(1600);
        });
    }

    @Test
    void concurrentWithdrawalsNeverOverdrawTheAccount() {
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
            open("ACC", "100.00");

            List<Callable<List<Integer>>> tasks = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                tasks.add(() -> withdrawals("ACC", 25));
            }
            List<Integer> statuses = runConcurrently(tasks);

            // 400 attempts of 1.00 against 100.00: exactly 100 succeed, the rest are rejected
            assertThat(statuses.stream().filter(s -> s == 200).count()).isEqualTo(100);
            assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(300);
            assertThat(balance("ACC")).isEqualByComparingTo("0.00");
            assertThat(historySize("ACC")).isEqualTo(100);
        });
    }

    private List<Integer> transfers(String from, String to) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
            statuses.add(post("/api/transfers",
                    "{\"fromAccountId\":\"" + from + "\",\"toAccountId\":\"" + to + "\",\"amount\":1.00}"));
        }
        return statuses;
    }

    private List<Integer> withdrawals(String id, int count) throws Exception {
        List<Integer> statuses = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            statuses.add(post("/api/accounts/" + id + "/withdrawals", "{\"amount\":1.00}"));
        }
        return statuses;
    }

    /** Releases every task at the same moment to maximise contention. */
    private static List<Integer> runConcurrently(List<Callable<List<Integer>>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<List<Integer>>> futures = new ArrayList<>();
            for (Callable<List<Integer>> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();

            List<Integer> all = new ArrayList<>();
            for (Future<List<Integer>> future : futures) {
                all.addAll(future.get());
            }
            return all;
        } finally {
            pool.shutdownNow();
        }
    }

    private void open(String id, String balance) throws Exception {
        assertThat(post("/api/accounts", "{\"id\":\"" + id + "\",\"openingBalance\":" + balance + "}"))
                .isEqualTo(201);
    }

    private BigDecimal balance(String id) throws Exception {
        return mapper.readTree(get("/api/accounts/" + id + "/balance")).get("balance").decimalValue();
    }

    private int historySize(String id) throws Exception {
        return mapper.readTree(get("/api/accounts/" + id + "/transactions")).size();
    }

    private int post(String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}