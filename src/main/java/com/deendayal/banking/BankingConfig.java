package com.deendayal.banking;

import com.deendayal.banking.api.IdempotencyStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

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

    @Bean
    IdempotencyStore idempotencyStore(Clock clock) {
        return new IdempotencyStore(clock, Duration.ofHours(24));
    }
}