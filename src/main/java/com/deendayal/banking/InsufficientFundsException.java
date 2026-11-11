package com.deendayal.banking;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(Money requested, Money available) {
        super("Insufficient funds: requested " + requested + " but available " + available);
    }
}
