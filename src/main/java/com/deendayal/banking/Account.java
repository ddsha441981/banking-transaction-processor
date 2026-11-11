package com.deendayal.banking;

import java.math.BigDecimal;

public class Account {

    private final String id;
    private Money balance;

    public Account(String id, Money openingBalance) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }



        this.id = id;
        this.balance = openingBalance;
    }

    public String getId() {
        return id;
    }

    public Money getBalance() {
        return balance;
    }
}