package com.deendayal.banking;

import java.util.Objects;

public class BankingService {

    private final AccountRepository accounts;

    public BankingService(AccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "Repository must not be null");
    }

    public void openAccount(String id, Money openingBalance) {
        accounts.add(new Account(id, openingBalance));
    }

    public void deposit(String id, Money amount) {
        findAccount(id).deposit(amount);
    }

    public void withdraw(String id, Money amount) {
        findAccount(id).withdraw(amount);
    }

    public Money getBalance(String id) {
        return findAccount(id).getBalance();
    }

    private Account findAccount(String id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}