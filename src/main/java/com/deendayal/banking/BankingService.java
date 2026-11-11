package com.deendayal.banking;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public class BankingService {

    private final AccountRepository accounts;
    private final Clock clock;

    public BankingService(AccountRepository accounts) {
        this(accounts, Clock.systemUTC());
    }

    public BankingService(AccountRepository accounts, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "Repository must not be null");
        this.clock = Objects.requireNonNull(clock, "Clock must not be null");
    }

    public void openAccount(String id, Money openingBalance) {
        accounts.add(new Account(id, openingBalance, clock));
    }

    public void deposit(String id, Money amount) {
        findAccount(id).deposit(amount);
    }

    public void withdraw(String id, Money amount) {
        findAccount(id).withdraw(amount);
    }

    public void transfer(String fromId, String toId, Money amount) {
        if (Objects.equals(fromId, toId)) {
            throw new InvalidTransferException("Cannot transfer to the same account: " + fromId);
        }
        Account source = findAccount(fromId);
        Account target = findAccount(toId);
        Account first = fromId.compareTo(toId) < 0 ? source : target;
        Account second = (first == source) ? target : source;

        synchronized (first) {
            synchronized (second) {
                source.debitForTransfer(amount, toId);
                target.creditForTransfer(amount, fromId);
            }
        }
    }

    public Money getBalance(String id) {
        return findAccount(id).getBalance();
    }

    public List<Transaction> getTransactionHistory(String id) {
        return findAccount(id).getTransactions();
    }

    private Account findAccount(String id) {
        return accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }
}