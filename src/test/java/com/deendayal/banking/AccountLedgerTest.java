package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountLedgerTest {

    @Test
    void newAccountHasEmptyLedger() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThat(account.getTransactions()).isEmpty();
    }

    @Test
    void depositIsRecordedInLedger() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        account.deposit(Money.of("25.00"));

        assertThat(account.getTransactions()).hasSize(1);
        Transaction transaction = account.getTransactions().get(0);
        assertThat(transaction.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.amount()).isEqualTo(Money.of("25.00"));
        assertThat(transaction.balanceAfter()).isEqualTo(Money.of("125.00"));
        assertThat(transaction.timestamp()).isNotNull();
    }

    @Test
    void withdrawalIsRecordedInLedger() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        account.withdraw(Money.of("30.00"));

        assertThat(account.getTransactions()).hasSize(1);
        Transaction transaction = account.getTransactions().get(0);
        assertThat(transaction.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transaction.amount()).isEqualTo(Money.of("30.00"));
        assertThat(transaction.balanceAfter()).isEqualTo(Money.of("70.00"));
    }

    @Test
    void failedOperationsAreNotRecorded() {
        Account account = new Account("ACC-1", Money.of("10.00"));

        assertThatThrownBy(() -> account.withdraw(Money.of("50.00")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> account.deposit(Money.ZERO))
                .isInstanceOf(InvalidAmountException.class);

        assertThat(account.getTransactions()).isEmpty();
    }


    @Test
    void transactionsAreStampedWithTheInjectedClock() {
        Instant now = Instant.parse("2026-11-10T10:15:30Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        Account account = new Account("ACC-1", Money.of("100.00"), fixedClock);

        account.deposit(Money.of("5.00"));

        assertThat(account.getTransactions().get(0).timestamp()).isEqualTo(now);
    }
}