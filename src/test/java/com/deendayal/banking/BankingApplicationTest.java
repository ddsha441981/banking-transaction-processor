package com.deendayal.banking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BankingApplicationTest {

    @Autowired
    private BankingService service;

    @Test
    void applicationStartsAndWiresTheService() {
        assertThat(service).isNotNull();
    }
}