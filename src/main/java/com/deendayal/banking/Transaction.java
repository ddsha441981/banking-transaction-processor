package com.deendayal.banking;

import java.time.Instant;
import java.util.Objects;

public record Transaction(TransactionType type, Money amount, Money balanceAfter,
                          Instant timestamp, String counterpartyId) {

    public Transaction {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(balanceAfter, "balanceAfter must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}