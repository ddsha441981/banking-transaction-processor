package com.deendayal.banking;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

public class BankingConsole {

    private static final List<String> DEMO_SCRIPT = List.of(
            "open ALICE 100.00",
            "open BOB 50.00",
            "deposit ALICE 25.00",
            "transfer ALICE BOB 60.00",
            "balance ALICE",
            "balance BOB",
            "history ALICE",
            "history BOB",
            "withdraw ALICE 1000.00",
            "transfer ALICE NOPE 1.00",
            "transfer ALICE ALICE 1.00",
            "deposit ALICE 0",
            "balance ALICE");

    private final BankingService bank;
    private final PrintStream out;

    public BankingConsole(BankingService bank, PrintStream out) {
        this.bank = Objects.requireNonNull(bank, "Service must not be null");
        this.out = Objects.requireNonNull(out, "Output must not be null");
    }

    public static void main(String[] args) throws IOException {
        BankingConsole console = new BankingConsole(
                new BankingService(new InMemoryAccountRepository()), System.out);
        console.run(new BufferedReader(new InputStreamReader(System.in)));
    }

    public void run(BufferedReader in) throws IOException {
        out.println("Banking console. Type 'help' for commands, 'exit' to quit.");
        while (true) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null || line.trim().equalsIgnoreCase("exit")) {
                break;
            }
            if (!line.isBlank()) {
                execute(line.trim());
            }
        }
        out.println("Bye.");
    }

    private void execute(String line) {
        String[] parts = line.split("\\s+");
        try {
            switch (parts[0].toLowerCase()) {
                case "help" -> printHelp();
                case "demo" -> runDemo();
                case "open" -> {
                    requireArgs(parts, 3, "open <id> <openingBalance>");
                    bank.openAccount(parts[1], Money.of(parts[2]));
                    out.println("Opened " + parts[1]);
                }
                case "deposit" -> {
                    requireArgs(parts, 3, "deposit <id> <amount>");
                    bank.deposit(parts[1], Money.of(parts[2]));
                    out.println("OK");
                }
                case "withdraw" -> {
                    requireArgs(parts, 3, "withdraw <id> <amount>");
                    bank.withdraw(parts[1], Money.of(parts[2]));
                    out.println("OK");
                }
                case "transfer" -> {
                    requireArgs(parts, 4, "transfer <fromId> <toId> <amount>");
                    bank.transfer(parts[1], parts[2], Money.of(parts[3]));
                    out.println("OK");
                }
                case "balance" -> {
                    requireArgs(parts, 2, "balance <id>");
                    out.println(parts[1] + ": " + bank.getBalance(parts[1]));
                }
                case "history" -> {
                    requireArgs(parts, 2, "history <id>");
                    printHistory(bank.getTransactionHistory(parts[1]));
                }
                default -> out.println("Unknown command: " + parts[0] + " (type 'help')");
            }
        } catch (RuntimeException e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private static void requireArgs(String[] parts, int expected, String usage) {
        if (parts.length != expected) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
    }

    private void printHistory(List<Transaction> history) {
        if (history.isEmpty()) {
            out.println("(no transactions)");
            return;
        }
        for (Transaction t : history) {
            String counterparty = t.counterpartyId() == null ? "" : "  counterparty=" + t.counterpartyId();
            out.printf("%s  %-12s %10s  balance=%s%s%n",
                    t.timestamp(), t.type(), t.amount(), t.balanceAfter(), counterparty);
        }
    }

    private void runDemo() {
        for (String command : DEMO_SCRIPT) {
            out.println("> " + command);
            execute(command);
        }
    }

    private void printHelp() {
        out.println("""
                Commands:
                  open <id> <openingBalance>
                  deposit <id> <amount>
                  withdraw <id> <amount>
                  transfer <fromId> <toId> <amount>
                  balance <id>
                  history <id>
                  demo      run a scripted walk-through
                  help
                  exit""");
    }
}