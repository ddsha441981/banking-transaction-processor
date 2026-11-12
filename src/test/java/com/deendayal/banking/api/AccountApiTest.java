package com.deendayal.banking.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountApiTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void openingAnAccountReturnsCreatedWithItsBalance() throws Exception {
        send("/api/accounts", """
                {"id":"ALICE","openingBalance":100.00}
                """)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/accounts/ALICE/balance"))
                .andExpect(jsonPath("$.accountId").value("ALICE"))
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    void balanceEndpointReturnsTheCurrentBalance() throws Exception {
        send("/api/accounts", """
                {"id":"ALICE","openingBalance":42.50}
                """);

        mvc.perform(get("/api/accounts/ALICE/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ALICE"))
                .andExpect(jsonPath("$.balance").value(42.5));
    }

    @Test
    void balanceOfAnUnknownAccountIsNotFound() throws Exception {
        mvc.perform(get("/api/accounts/NOPE/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("NOPE")));
    }

    @Test
    void openingAnExistingAccountIsAConflictAndKeepsTheOriginal() throws Exception {
        send("/api/accounts", """
                {"id":"ALICE","openingBalance":10.00}
                """);

        send("/api/accounts", """
                {"id":"ALICE","openingBalance":99.00}
                """)
                .andExpect(status().isConflict());

        mvc.perform(get("/api/accounts/ALICE/balance"))
                .andExpect(jsonPath("$.balance").value(10.0));
    }

    @Test
    void openingAnAccountWithoutABalanceIsABadRequest() throws Exception {
        send("/api/accounts", """
                {"id":"ALICE"}
                """)
                .andExpect(status().isBadRequest());
    }

    private ResultActions send(String url, String json) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }
}