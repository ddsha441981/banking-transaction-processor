package com.deendayal.banking.api;

public class IdempotencyRequestInProgressException extends RuntimeException {

    public IdempotencyRequestInProgressException(String key) {
        super("A request with this Idempotency-Key is still being processed: " + key);
    }
}