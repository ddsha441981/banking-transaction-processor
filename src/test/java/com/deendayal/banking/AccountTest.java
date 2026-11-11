package com.deendayal.banking;

import org.junit.jupiter.api.Test;


import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class AccountTest {

    @Test
    void newAccountHasGivenIdAndOpeningBalance() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThat(account.getId()).isEqualTo("ACC-1");
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
    }

    @Test
    void rejectsBlankId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Account("  ", Money.ZERO));
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Account("ACC-1", null));
    }



    @Test
    void depositIncreasesBalance() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        account.deposit(Money.of("25.50"));

        assertThat(account.getBalance()).isEqualTo(Money.of("125.50"));
    }

    @Test
    void multipleDepositsAccumulate() {
        Account account = new Account("ACC-1", Money.ZERO);

        account.deposit(Money.of("10.00"));
        account.deposit(Money.of("0.01"));

        assertThat(account.getBalance()).isEqualTo(Money.of("10.01"));
    }

    @Test
    void depositRejectsZeroAmount() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThatThrownBy(() -> account.deposit(Money.ZERO))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessageContaining("greater than zero");
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
    }

    @Test
    void depositRejectsNullAmount() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThatNullPointerException()
                .isThrownBy(() -> account.deposit(null));
    }

    @Test
    void withdrawDecreasesBalance() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        account.withdraw(Money.of("40.25"));

        assertThat(account.getBalance()).isEqualTo(Money.of("59.75"));
    }

    @Test
    void canWithdrawEntireBalance() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        account.withdraw(Money.of("100.00"));

        assertThat(account.getBalance()).isEqualTo(Money.ZERO);
    }

    @Test
    void withdrawRejectsOverdraftAndKeepsBalance() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThatThrownBy(() -> account.withdraw(Money.of("100.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("100.01")
                .hasMessageContaining("100.00");
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
    }

    @Test
    void withdrawRejectsZeroAmount() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThatThrownBy(() -> account.withdraw(Money.ZERO))
                .isInstanceOf(InvalidAmountException.class);
        assertThat(account.getBalance()).isEqualTo(Money.of("100.00"));
    }

    @Test
    void withdrawRejectsNullAmount() {
        Account account = new Account("ACC-1", Money.of("100.00"));

        assertThatNullPointerException()
                .isThrownBy(() -> account.withdraw(null));
    }

}