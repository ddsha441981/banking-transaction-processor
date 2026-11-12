package com.deendayal.banking.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class TransactionApiTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void openAccounts() throws Exception {
        send("/api/accounts", """
                {"id":"ALICE","openingBalance":100.00}
                """);
        send("/api/accounts", """
                {"id":"BOB","openingBalance":50.00}
                """);
    }

    // --- deposit ---

    @Test
    void depositIncreasesTheBalanceAndReturnsIt() throws Exception {
        send("/api/accounts/ALICE/deposits", """
                {"amount":25.00}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ALICE"))
                .andExpect(jsonPath("$.balance").value(125.0));

        assertBalance("ALICE", 125.0);
    }

    @Test
    void depositToAnUnknownAccountIsNotFound() throws Exception {
        send("/api/accounts/NOPE/deposits", """
                {"amount":1.00}
                """)
                .andExpect(status().isNotFound());
    }

    // --- withdraw ---

    @Test
    void withdrawalDecreasesTheBalanceAndReturnsIt() throws Exception {
        send("/api/accounts/ALICE/withdrawals", """
                {"amount":40.25}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(59.75));
    }

    @Test
    void overdraftIsUnprocessableAndLeavesTheBalanceUnchanged() throws Exception {
        send("/api/accounts/ALICE/withdrawals", """
                {"amount":100.01}
                """)
                .andExpect(status().isUnprocessableEntity());

        assertBalance("ALICE", 100.0);
    }

    // --- invalid amounts ---

    @Test
    void zeroNegativeOverPreciseAndMissingAmountsAreBadRequests() throws Exception {
        for (String body : new String[]{
                "{\"amount\":0}", "{\"amount\":-5}", "{\"amount\":10.005}", "{}", "not json"}) {
            send("/api/accounts/ALICE/deposits", body).andExpect(status().isBadRequest());
        }

        assertBalance("ALICE", 100.0);
        mvc.perform(get("/api/accounts/ALICE/transactions")).andExpect(content().json("[]"));
    }

    // --- transfer ---

    @Test
    void transferMovesFundsBetweenAccounts() throws Exception {
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"BOB","amount":30.00}
                """)
//                .andExpect(status().isNoContent());
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").isNotEmpty());

        assertBalance("ALICE", 70.0);
        assertBalance("BOB", 80.0);
    }

    @Test
    void failedTransfersChangeNothing() throws Exception {
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"ALICE","amount":1.00}
                """)
                .andExpect(status().isBadRequest());
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"NOPE","amount":1.00}
                """)
                .andExpect(status().isNotFound());
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"BOB","amount":100.01}
                """)
                .andExpect(status().isUnprocessableEntity());
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"BOB"}
                """)
                .andExpect(status().isBadRequest());

        assertBalance("ALICE", 100.0);
        assertBalance("BOB", 50.0);
    }

    // --- history ---

    @Test
    void historyListsTheAccountsTransactionsInOrder() throws Exception {
        send("/api/accounts/ALICE/deposits", """
                {"amount":25.00}
                """);
        String body = send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"BOB","amount":60.00}
                """).andReturn().getResponse().getContentAsString();
        String transferId = JsonPath.read(body, "$.transferId");

        mvc.perform(get("/api/accounts/ALICE/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$[0].amount").value(25.0))
                .andExpect(jsonPath("$[0].balanceAfter").value(125.0))
                .andExpect(jsonPath("$[0].timestamp").isNotEmpty())
                .andExpect(jsonPath("$[0].transferId").doesNotExist())
                .andExpect(jsonPath("$[1].type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$[1].balanceAfter").value(65.0))
                .andExpect(jsonPath("$[1].counterpartyId").value("BOB"))
                .andExpect(jsonPath("$[1].transferId").value(transferId));

        mvc.perform(get("/api/accounts/BOB/transactions"))
                .andExpect(jsonPath("$[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$[0].counterpartyId").value("ALICE"))
                .andExpect(jsonPath("$[0].transferId").value(transferId));
    }

    @Test
    void historyOfANewAccountIsEmptyAndOfAnUnknownAccountIsNotFound() throws Exception {
        mvc.perform(get("/api/accounts/ALICE/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/accounts/NOPE/transactions"))
                .andExpect(status().isNotFound());
    }

    private void assertBalance(String id, double expected) throws Exception {
        mvc.perform(get("/api/accounts/" + id + "/balance"))
                .andExpect(jsonPath("$.balance").value(expected));
    }

    private ResultActions send(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void transferMovesFundsAndReturnsItsId() throws Exception {
        send("/api/transfers", """
                {"fromAccountId":"ALICE","toAccountId":"BOB","amount":30.00}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transferId").isNotEmpty());

        assertBalance("ALICE", 70.0);
        assertBalance("BOB", 80.0);
    }
}