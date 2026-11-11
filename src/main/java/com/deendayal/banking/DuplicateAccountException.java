package com.deendayal.banking;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String id) {
        super("Account already exists: " + id);
    }
}