package com.deendayal.banking;

import org.junit.jupiter.api.Test;


import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;


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
}