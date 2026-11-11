package com.deendayal.banking;

import java.util.List;
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

    public void transfer(String fromId, String toId, Money amount) {
        if (Objects.equals(fromId, toId)) {
            throw new InvalidTransferException("Cannot transfer to the same account: " + fromId);
        }
        Account source = findAccount(fromId);
        Account target = findAccount(toId);
        source.debitForTransfer(amount, toId);
        target.creditForTransfer(amount, fromId);
    }
    public List<Transaction> getTransactionHistory(String id) {
        return findAccount(id).getTransactions();
    }
}