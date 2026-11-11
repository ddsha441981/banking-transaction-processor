package com.deendayal.banking;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String id) {
        super("Account not found: " + id);
    }
}