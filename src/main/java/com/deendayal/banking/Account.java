package com.deendayal.banking;

import java.math.BigDecimal;
import java.util.Objects;

public class Account {

    private final String id;
    private Money balance;

    public Account(String id, Money openingBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }

        this.id = id;
        this.balance = Objects.requireNonNull(openingBalance, "Opening balance must not be null");
    }

    public String getId() {
        return id;
    }

    public Money getBalance() {
        return balance;
    }


    public void deposit(Money amount) {
        requirePositive(amount);
        balance = balance.plus(amount);
    }

    public void withdraw(Money amount) {
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(amount, balance);
        }
        balance = balance.minus(amount);
    }

    private static void requirePositive(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        if (amount.isZero()) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
    }
}