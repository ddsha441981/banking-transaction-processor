package com.deendayal.banking;

import java.time.Instant;
import java.util.Objects;

public record Transaction(TransactionType type, Money amount, Money balanceAfter,
                          Instant timestamp, String counterpartyId, String transferId) {

    public Transaction {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        boolean isTransfer = type == TransactionType.TRANSFER_OUT || type == TransactionType.TRANSFER_IN;
        if (isTransfer && (counterpartyId == null || transferId == null)) {
            throw new IllegalArgumentException("A transfer entry needs a counterparty and a transfer id");
        }
        if (!isTransfer && (counterpartyId != null || transferId != null)) {
            throw new IllegalArgumentException("Only transfer entries carry a counterparty and a transfer id");
        }
    }
}