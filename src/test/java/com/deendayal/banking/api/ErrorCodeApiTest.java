package com.deendayal.banking.api;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ErrorCodeApiTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void openAccounts() throws Exception {
        send("/api/accounts", "{\"id\":\"ALICE\",\"openingBalance\":100.00}");
        send("/api/accounts", "{\"id\":\"BOB\",\"openingBalance\":50.00}");
    }

    @Test
    void unknownAccountHasItsOwnCode() throws Exception {
        assertError(mvc.perform(get("/api/accounts/NOPE/balance")), 404, "ACCOUNT_NOT_FOUND");
    }

    @Test
    void duplicateAccountHasItsOwnCode() throws Exception {
        assertError(send("/api/accounts", "{\"id\":\"ALICE\",\"openingBalance\":1.00}"),
                409, "ACCOUNT_ALREADY_EXISTS");
    }

    @Test
    void overdraftHasItsOwnCode() throws Exception {
        assertError(send("/api/accounts/ALICE/withdrawals", "{\"amount\":100.01}"),
                422, "INSUFFICIENT_FUNDS");
    }

    @Test
    void zeroNegativeAndOverPreciseAmountsAreInvalidAmount() throws Exception {
        for (String amount : new String[]{"0", "-5", "10.005"}) {
            assertError(send("/api/accounts/ALICE/deposits", "{\"amount\":" + amount + "}"),
                    400, "INVALID_AMOUNT");
        }
    }

    @Test
    void transferToTheSameAccountIsAnInvalidTransfer() throws Exception {
        assertError(send("/api/transfers",
                        "{\"fromAccountId\":\"ALICE\",\"toAccountId\":\"ALICE\",\"amount\":1.00}"),
                400, "INVALID_TRANSFER");
    }

    @Test
    void missingFieldsAndMalformedJsonAreInvalidRequests() throws Exception {
        assertError(send("/api/accounts/ALICE/deposits", "{}"), 400, "INVALID_REQUEST");
        assertError(send("/api/accounts/ALICE/deposits", "not json"), 400, "INVALID_REQUEST");
    }

    private void assertError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private ResultActions send(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}