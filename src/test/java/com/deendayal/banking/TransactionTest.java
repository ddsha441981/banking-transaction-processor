package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TransactionTest {

    private static final Instant NOW = Instant.parse("2026-11-10T09:00:00Z");
    private static final Money AMOUNT = Money.of("10.00");

    @Test
    void transferEntriesNeedACounterpartyAndATransferId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entry(TransactionType.TRANSFER_OUT, "BOB", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entry(TransactionType.TRANSFER_IN, null, "TRF-1"));

        assertThat(entry(TransactionType.TRANSFER_OUT, "BOB", "TRF-1").transferId()).isEqualTo("TRF-1");
    }

    @Test
    void depositsAndWithdrawalsMustNotCarryTransferDetails() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entry(TransactionType.DEPOSIT, "BOB", null));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> entry(TransactionType.WITHDRAWAL, null, "TRF-1"));

        assertThat(entry(TransactionType.DEPOSIT, null, null).transferId()).isNull();
    }

    private static Transaction entry(TransactionType type, String counterpartyId, String transferId) {
        return new Transaction(type, AMOUNT, AMOUNT, NOW, counterpartyId, transferId);
    }
}