package com.deendayal.banking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class BankingConfig {

    @Bean
    AccountRepository accountRepository() {
        return new InMemoryAccountRepository();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    BankingService bankingService(AccountRepository accountRepository, Clock clock) {
        return new BankingService(accountRepository, clock);
    }
}