package com.deendayal.banking.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ConcurrentIdempotencyApiTest {

    private static final int CLIENTS = 16;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper mapper;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void sixteenSimultaneousRetriesOfOneTransferMoveTheMoneyOnce() throws Exception {
        post("/api/accounts", "{\"id\":\"A\",\"openingBalance\":100.00}", null);
        post("/api/accounts", "{\"id\":\"B\",\"openingBalance\":0.00}", null);
        String transfer = "{\"fromAccountId\":\"A\",\"toAccountId\":\"B\",\"amount\":30.00}";

        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        try {
            CountDownLatch ready = new CountDownLatch(CLIENTS);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<HttpResponse<String>>> futures = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return post("/api/transfers", transfer, "retry-1");
                }));
            }
            ready.await();
            go.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            // each retry either got the transfer's result or was told the first one is still running
            //assertThat(responses).extracting(HttpResponse::statusCode).containsOnly(200, 409);
            // any mix of 200 (ran or replayed) and 409 (first still running) is fine, nothing else
            assertThat(responses).extracting(HttpResponse::statusCode).isSubsetOf(200, 409);
            
            List<HttpResponse<String>> accepted = responses.stream()
                    .filter(r -> r.statusCode() == 200).toList();
            assertThat(accepted).isNotEmpty();
            Set<String> transferIds = accepted.stream()
                    .map(r -> read(r.body()).get("transferId").asText())
                    .collect(Collectors.toSet());
            assertThat(transferIds).hasSize(1);

            assertThat(read(get("/api/accounts/A/balance")).get("balance").decimalValue())
                    .isEqualByComparingTo("70.00");
            assertThat(read(get("/api/accounts/B/balance")).get("balance").decimalValue())
                    .isEqualByComparingTo("30.00");
            assertThat(read(get("/api/accounts/A/transactions"))).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> post(String path, String json, String key) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}