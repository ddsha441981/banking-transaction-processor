package com.deendayal.banking;

import org.junit.jupiter.api.Test;


import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

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
}