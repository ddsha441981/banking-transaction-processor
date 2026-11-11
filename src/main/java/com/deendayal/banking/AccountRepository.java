package com.deendayal.banking;

import java.util.Optional;

public interface AccountRepository {
    void add(Account account);

    Optional<Account> findById(String id);
}