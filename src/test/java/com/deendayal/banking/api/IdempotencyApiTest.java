package com.deendayal.banking.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IdempotencyApiTest {

    private static final String KEY_HEADER = "Idempotency-Key";
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void openAccounts() throws Exception {
        send("/api/accounts", null, "{\"id\":\"ALICE\",\"openingBalance\":100.00}");
        send("/api/accounts", null, "{\"id\":\"BOB\",\"openingBalance\":50.00}");
    }

    @Test
    void retryingADepositWithTheSameKeyAppliesItOnce() throws Exception {
        send("/api/accounts/ALICE/deposits", "k-1", "{\"amount\":25.00}")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(REPLAYED_HEADER))
                .andExpect(jsonPath("$.balance").value(125.0));

        // written differently, but the same amount, so it is the same request
        send("/api/accounts/ALICE/deposits", "k-1", "{\"amount\":25.0000}")
                .andExpect(status().isOk())
                .andExpect(header().string(REPLAYED_HEADER, "true"))
                .andExpect(jsonPath("$.balance").value(125.0));

        assertBalance("ALICE", 125.0);
        mvc.perform(get("/api/accounts/ALICE/transactions"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void withoutAKeyEveryRequestIsApplied() throws Exception {
        send("/api/accounts/ALICE/deposits", null, "{\"amount\":25.00}");
        send("/api/accounts/ALICE/deposits", null, "{\"amount\":25.00}");

        assertBalance("ALICE", 150.0);
    }

    @Test
    void retryingATransferReturnsTheSameTransferIdAndMovesMoneyOnce() throws Exception {
        String body = "{\"fromAccountId\":\"ALICE\",\"toAccountId\":\"BOB\",\"amount\":30.00}";

        String first = send("/api/transfers", "t-1", body)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = send("/api/transfers", "t-1", body)
                .andExpect(status().isOk())
                .andExpect(header().string(REPLAYED_HEADER, "true"))
                .andReturn().getResponse().getContentAsString();

        String firstId = JsonPath.read(first, "$.transferId");
        String secondId = JsonPath.read(second, "$.transferId");
        assertThat(secondId).isEqualTo(firstId);
        assertBalance("ALICE", 70.0);
        assertBalance("BOB", 80.0);
        mvc.perform(get("/api/accounts/ALICE/transactions"))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        send("/api/accounts/ALICE/deposits", "k-1", "{\"amount\":25.00}");

        send("/api/accounts/ALICE/deposits", "k-1", "{\"amount\":26.00}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
        send("/api/accounts/ALICE/withdrawals", "k-1", "{\"amount\":25.00}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertBalance("ALICE", 125.0);
    }

    @Test
    void aFailedRequestDoesNotConsumeTheKey() throws Exception {
        send("/api/accounts/ALICE/withdrawals", "w-1", "{\"amount\":150.00}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

        send("/api/accounts/ALICE/deposits", null, "{\"amount\":100.00}");

        send("/api/accounts/ALICE/withdrawals", "w-1", "{\"amount\":150.00}")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(REPLAYED_HEADER))
                .andExpect(jsonPath("$.balance").value(50.0));
    }

    @Test
    void aRequestRejectedAsInvalidDoesNotConsumeTheKey() throws Exception {
        send("/api/accounts/ALICE/deposits", "d-1", "{\"amount\":0}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));

        send("/api/accounts/ALICE/deposits", "d-1", "{\"amount\":5.00}")
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(REPLAYED_HEADER))
                .andExpect(jsonPath("$.balance").value(105.0));
    }

    @Test
    void aTooLongKeyIsABadRequest() throws Exception {
        send("/api/accounts/ALICE/deposits", "x".repeat(256), "{\"amount\":5.00}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertBalance("ALICE", 100.0);
    }

    private void assertBalance(String id, double expected) throws Exception {
        mvc.perform(get("/api/accounts/" + id + "/balance"))
                .andExpect(jsonPath("$.balance").value(expected));
    }

    private ResultActions send(String url, String key, String json) throws Exception {
        MockHttpServletRequestBuilder request = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (key != null) {
            request.header(KEY_HEADER, key);
        }
        return mvc.perform(request);
    }
}