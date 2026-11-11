package com.deendayal.banking;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Account {

    private final String id;
    private final Clock clock;
    private final List<Transaction> ledger = new ArrayList<>();
    private Money balance;

    public Account(String id, Money openingBalance) {
        this(id, openingBalance, Clock.systemUTC());
    }

    public Account(String id, Money openingBalance, Clock clock) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        this.id = id;
        this.balance = Objects.requireNonNull(openingBalance, "Opening balance must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public String getId() {
        return id;
    }

    public synchronized Money getBalance() {
        return balance;
    }

    public synchronized List<Transaction> getTransactions() {
        return List.copyOf(ledger);
    }

    public synchronized void deposit(Money amount) {
        credit(amount, TransactionType.DEPOSIT, null);
    }

    public synchronized void withdraw(Money amount) {
        debit(amount, TransactionType.WITHDRAWAL, null);
    }

    synchronized void debitForTransfer(Money amount, String toId) {
        debit(amount, TransactionType.TRANSFER_OUT, toId);
    }

    synchronized void creditForTransfer(Money amount, String fromId) {
        credit(amount, TransactionType.TRANSFER_IN, fromId);
    }

    private void credit(Money amount, TransactionType type, String counterpartyId) {
        requirePositive(amount);
        balance = balance.plus(amount);
        record(type, amount, counterpartyId);
    }

    private void debit(Money amount, TransactionType type, String counterpartyId) {
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(amount, balance);
        }
        balance = balance.minus(amount);
        record(type, amount, counterpartyId);
    }

    private void record(TransactionType type, Money amount, String counterpartyId) {
        ledger.add(new Transaction(type, amount, balance, clock.instant(), counterpartyId));
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }
}