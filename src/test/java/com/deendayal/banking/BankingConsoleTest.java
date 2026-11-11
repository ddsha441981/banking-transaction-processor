package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BankingConsoleTest {

    private String run(String input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        BankingConsole console =
                new BankingConsole(new BankingService(new InMemoryAccountRepository()), out);

        console.run(new BufferedReader(new StringReader(input)));

        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void balancesReflectDepositAndTransfer() throws IOException {
        String output = run("""
                open ALICE 100.00
                open BOB 50.00
                deposit ALICE 25.00
                transfer ALICE BOB 60.00
                balance ALICE
                balance BOB
                exit
                """);

        assertThat(output).contains("ALICE: 65.00").contains("BOB: 110.00");
    }

    @Test
    void domainErrorsArePrintedAndTheSessionContinues() throws IOException {
        String output = run("""
                open ALICE 10.00
                withdraw ALICE 10.01
                balance ALICE
                exit
                """);

        assertThat(output).contains("Error: Insufficient funds").contains("ALICE: 10.00");
    }

    @Test
    void unknownCommandsAndMissingArgumentsAreReported() throws IOException {
        String output = run("""
                fly
                deposit ALICE
                exit
                """);

        assertThat(output)
                .contains("Unknown command: fly")
                .contains("Usage: deposit <id> <amount>");
    }

    @Test
    void historyListsTransactionsOfAnAccount() throws IOException {
        String output = run("""
                open ALICE 10.00
                deposit ALICE 5.00
                history ALICE
                exit
                """);

        assertThat(output).contains("DEPOSIT").contains("15.00");
    }

    @Test
    void sessionEndsCleanlyWhenInputEnds() throws IOException {
        assertThat(run("")).contains("Bye.");
    }
}