package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class RequirementsAcceptanceTest {

    private final Instant start = Instant.parse("2026-11-10T09:00:00Z");
    private final MutableClock clock = new MutableClock(start);
    private final BankingService bank = new BankingService(new InMemoryAccountRepository(), clock);

    @Test
    void multipleAccountsWithUniqueIdsAndBalances() {
        bank.openAccount("ALICE", Money.of("100.00"));
        bank.openAccount("BOB", Money.of("50.00"));

        assertThat(bank.getBalance("ALICE")).isEqualTo(Money.of("100.00"));
        assertThat(bank.getBalance("BOB")).isEqualTo(Money.of("50.00"));
        assertThatThrownBy(() -> bank.openAccount("ALICE", Money.of("1.00")))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void depositWithdrawalAndTransferUpdateBalancesAndLedgers() {
        bank.openAccount("ALICE", Money.of("100.00"));
        bank.openAccount("BOB", Money.of("50.00"));

        bank.deposit("ALICE", Money.of("25.00"));            // ALICE 125
        clock.advance(Duration.ofMinutes(1));
        bank.withdraw("ALICE", Money.of("15.00"));           // ALICE 110
        clock.advance(Duration.ofMinutes(1));
        bank.transfer("ALICE", "BOB", Money.of("60.00"));    // ALICE 50, BOB 110

        assertThat(bank.getBalance("ALICE")).isEqualTo(Money.of("50.00"));
        assertThat(bank.getBalance("BOB")).isEqualTo(Money.of("110.00"));

        assertThat(bank.getTransactionHistory("ALICE"))
                .extracting(Transaction::type, Transaction::amount,
                        Transaction::balanceAfter, Transaction::timestamp)
                .containsExactly(
                        tuple(TransactionType.DEPOSIT, Money.of("25.00"),
                                Money.of("125.00"), start),
                        tuple(TransactionType.WITHDRAWAL, Money.of("15.00"),
                                Money.of("110.00"), start.plus(Duration.ofMinutes(1))),
                        tuple(TransactionType.TRANSFER_OUT, Money.of("60.00"),
                                Money.of("50.00"), start.plus(Duration.ofMinutes(2))));

        assertThat(bank.getTransactionHistory("BOB"))
                .extracting(Transaction::type, Transaction::amount,
                        Transaction::balanceAfter, Transaction::counterpartyId)
                .containsExactly(
                        tuple(TransactionType.TRANSFER_IN, Money.of("60.00"),
                                Money.of("110.00"), "ALICE"));
    }

    @Test
    void invalidOperationsAreRejectedAndLeaveNoTrace() {
        bank.openAccount("ALICE", Money.of("50.00"));
        bank.openAccount("BOB", Money.of("10.00"));

        assertThatThrownBy(() -> bank.withdraw("ALICE", Money.of("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> bank.deposit("ALICE", Money.ZERO))
                .isInstanceOf(InvalidAmountException.class);
        assertThatThrownBy(() -> Money.of("-5"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bank.transfer("ALICE", "ALICE", Money.of("1.00")))
                .isInstanceOf(InvalidTransferException.class);
        assertThatThrownBy(() -> bank.transfer("ALICE", "BOB", Money.of("50.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> bank.transfer("ALICE", "NOPE", Money.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class);

        assertThat(bank.getBalance("ALICE")).isEqualTo(Money.of("50.00"));
        assertThat(bank.getBalance("BOB")).isEqualTo(Money.of("10.00"));
        assertThat(bank.getTransactionHistory("ALICE")).isEmpty();
        assertThat(bank.getTransactionHistory("BOB")).isEmpty();
    }
}