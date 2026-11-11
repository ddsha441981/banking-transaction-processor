package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class AccountTest {

    @Test
    void newAccountHasGivenIdAndOpeningBalance() {
        Account account = new Account("ACC-1", new BigDecimal("100.00"));

        assertThat(account.getId()).isEqualTo("ACC-1");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void rejectsBlankId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Account("  ", BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeOpeningBalance() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Account("ACC-1", new BigDecimal("-1")));
    }
}