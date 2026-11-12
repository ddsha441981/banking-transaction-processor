package com.deendayal.banking.api;

public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String key) {
        super("Idempotency-Key was already used with a different request: " + key);
    }
}