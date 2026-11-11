package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class BankingServiceTest {

    private final BankingService service = new BankingService(new InMemoryAccountRepository());

    @Test
    void opensAccountWithOpeningBalance() {
        service.openAccount("ACC-1", Money.of("100.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("100.00"));
    }

    @Test
    void depositChangesOnlyTheTargetedAccount() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("50.00"));

        service.deposit("ACC-1", Money.of("25.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("125.00"));
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("50.00"));
    }

    @Test
    void withdrawDecreasesBalance() {
        service.openAccount("ACC-1", Money.of("100.00"));

        service.withdraw("ACC-1", Money.of("40.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("60.00"));
    }

    @Test
    void overdraftIsRejectedAndBalanceIsUnchanged() {
        service.openAccount("ACC-1", Money.of("10.00"));

        assertThatThrownBy(() -> service.withdraw("ACC-1", Money.of("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void openingAnExistingAccountIdIsRejected() {
        service.openAccount("ACC-1", Money.of("10.00"));

        assertThatThrownBy(() -> service.openAccount("ACC-1", Money.of("99.00")))
                .isInstanceOf(DuplicateAccountException.class);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void operationsOnAnUnknownAccountFail() {
        assertThatThrownBy(() -> service.deposit("NOPE", Money.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOPE");
        assertThatThrownBy(() -> service.withdraw("NOPE", Money.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getBalance("NOPE"))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void historyContainsOnlyThatAccountsTransactions() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("50.00"));

        service.deposit("ACC-1", Money.of("10.00"));
        service.withdraw("ACC-2", Money.of("5.00"));

        assertThat(service.getTransactionHistory("ACC-1"))
                .extracting(Transaction::type, Transaction::amount)
                .containsExactly(tuple(TransactionType.DEPOSIT, Money.of("10.00")));
        assertThat(service.getTransactionHistory("ACC-2"))
                .extracting(Transaction::type, Transaction::amount)
                .containsExactly(tuple(TransactionType.WITHDRAWAL, Money.of("5.00")));
    }

    @Test
    void historyOfANewAccountIsEmpty() {
        service.openAccount("ACC-1", Money.of("100.00"));

        assertThat(service.getTransactionHistory("ACC-1")).isEmpty();
    }

    @Test
    void historyOfAnUnknownAccountFails() {
        assertThatThrownBy(() -> service.getTransactionHistory("NOPE"))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOPE");
    }

    @Test
    void historyCannotBeModifiedByTheCaller() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.deposit("ACC-1", Money.of("5.00"));

        List<Transaction> history = service.getTransactionHistory("ACC-1");

        assertThatThrownBy(history::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(service.getTransactionHistory("ACC-1")).hasSize(1);
    }

    @Test
    void anEarlierHistoryIsASnapshotAndDoesNotChangeLater() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.deposit("ACC-1", Money.of("5.00"));
        List<Transaction> before = service.getTransactionHistory("ACC-1");

        service.deposit("ACC-1", Money.of("7.00"));

        assertThat(before).hasSize(1);
        assertThat(service.getTransactionHistory("ACC-1")).hasSize(2);
    }

    @Test
    void historyIsInChronologicalOrderWithTimestampsFromTheClock() {
        Instant start = Instant.parse("2026-11-10T09:00:00Z");
        MutableClock clock = new MutableClock(start);
        BankingService timedService = new BankingService(new InMemoryAccountRepository(), clock);
        timedService.openAccount("ACC-1", Money.of("100.00"));

        timedService.deposit("ACC-1", Money.of("10.00"));
        clock.advance(Duration.ofMinutes(5));
        timedService.withdraw("ACC-1", Money.of("4.00"));
        clock.advance(Duration.ofMinutes(5));
        timedService.deposit("ACC-1", Money.of("1.00"));

        assertThat(timedService.getTransactionHistory("ACC-1"))
                .extracting(Transaction::type, Transaction::timestamp)
                .containsExactly(
                        tuple(TransactionType.DEPOSIT, start),
                        tuple(TransactionType.WITHDRAWAL, start.plus(Duration.ofMinutes(5))),
                        tuple(TransactionType.DEPOSIT, start.plus(Duration.ofMinutes(10))));
    }
}