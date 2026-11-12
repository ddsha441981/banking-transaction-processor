package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void sameValueWithDifferentScaleIsEqual() {
        assertThat(Money.of("10")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("-0.01"));
    }

    @Test
    void rejectsNullAmount() {
        assertThatNullPointerException()
                .isThrownBy(() -> Money.of((BigDecimal) null));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("10.005"));
    }

    @Test
    void rejectsNonNumericText() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("abc"));
    }

    @Test
    void addsAmounts() {
        assertThat(Money.of("10.50").plus(Money.of("2.25"))).isEqualTo(Money.of("12.75"));
    }

    @Test
    void subtractsAmounts() {
        assertThat(Money.of("10.00").minus(Money.of("2.50"))).isEqualTo(Money.of("7.50"));
    }

    @Test
    void subtractionCannotGoBelowZero() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Money.of("1.00").minus(Money.of("2.00")));
    }

    @Test
    void comparesAmounts() {
        assertThat(Money.of("5.00")).isGreaterThan(Money.of("4.99"));
        assertThat(Money.ZERO.isZero()).isTrue();
        assertThat(Money.of("0.01").isZero()).isFalse();
    }

    @Test
    void exposesItsAmountWithTwoDecimalPlaces() {
        assertThat(Money.of("12.5").amount()).isEqualByComparingTo("12.50");
        assertThat(Money.of("12.5").amount().scale()).isEqualTo(2);
    }
}