package com.deendayal.banking;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryAccountRepository implements AccountRepository {

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    @Override
    public void add(Account account) {
        // putIfAbsent makes the uniqueness check and the insert one atomic step
        if (accounts.putIfAbsent(account.getId(), account) != null) {
            throw new DuplicateAccountException(account.getId());
        }
    }

    @Override
    public Optional<Account> findById(String id) {
        return Optional.ofNullable(accounts.get(id));
    }
}