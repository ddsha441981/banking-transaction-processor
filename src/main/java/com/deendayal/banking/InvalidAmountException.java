package com.deendayal.banking;

public class InvalidAmountException extends IllegalArgumentException {

    public InvalidAmountException(String message) {
        super(message);
    }
}