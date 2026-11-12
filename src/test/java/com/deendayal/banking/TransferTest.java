package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private final AccountRepository repository = new InMemoryAccountRepository();
    private final BankingService service = new BankingService(new InMemoryAccountRepository());

    @Test
    void transferMovesFundsBetweenAccounts() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        service.transfer("ACC-1", "ACC-2", Money.of("30.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("70.00"));
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("50.00"));
    }

    @Test
    void transferWritesALedgerEntryOnBothSides() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        service.transfer("ACC-1", "ACC-2", Money.of("30.00"));

        Transaction out = ledgerOf("ACC-1").get(0);
        assertThat(out.type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(out.amount()).isEqualTo(Money.of("30.00"));
        assertThat(out.balanceAfter()).isEqualTo(Money.of("70.00"));

        Transaction in = ledgerOf("ACC-2").get(0);
        assertThat(in.type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(in.amount()).isEqualTo(Money.of("30.00"));
        assertThat(in.balanceAfter()).isEqualTo(Money.of("50.00"));
    }

    @Test
    void canTransferTheEntireBalance() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.ZERO);

        service.transfer("ACC-1", "ACC-2", Money.of("100.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.ZERO);
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("100.00"));
    }

    private List<Transaction> ledgerOf(String id) {
        return service.getTransactionHistory(id);
    }


    @Test
    void transferToTheSameAccountIsRejected() {
        service.openAccount("ACC-1", Money.of("100.00"));

        assertThatThrownBy(() -> service.transfer("ACC-1", "ACC-1", Money.of("10.00")))
                .isInstanceOf(InvalidTransferException.class);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("100.00"));
        assertThat(ledgerOf("ACC-1")).isEmpty();
    }

    @Test
    void transferWithInsufficientFundsChangesNothing() {
        service.openAccount("ACC-1", Money.of("10.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        assertThatThrownBy(() -> service.transfer("ACC-1", "ACC-2", Money.of("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("20.00"));
        assertThat(ledgerOf("ACC-1")).isEmpty();
        assertThat(ledgerOf("ACC-2")).isEmpty();
    }

    @Test
    void transferOfZeroChangesNothing() {
        service.openAccount("ACC-1", Money.of("10.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        assertThatThrownBy(() -> service.transfer("ACC-1", "ACC-2", Money.ZERO))
                .isInstanceOf(InvalidAmountException.class);

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("20.00"));
    }

    @Test
    void transferToAnUnknownAccountLeavesTheSourceUntouched() {
        service.openAccount("ACC-1", Money.of("100.00"));

        assertThatThrownBy(() -> service.transfer("ACC-1", "NOPE", Money.of("10.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOPE");

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("100.00"));
        assertThat(ledgerOf("ACC-1")).isEmpty();
    }

    @Test
    void transferFromAnUnknownAccountFails() {
        service.openAccount("ACC-2", Money.of("20.00"));

        assertThatThrownBy(() -> service.transfer("NOPE", "ACC-2", Money.of("10.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOPE");

        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("20.00"));
    }

    @Test
    void transferEntriesNameTheOtherSide() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        service.transfer("ACC-1", "ACC-2", Money.of("30.00"));

        assertThat(ledgerOf("ACC-1").get(0).counterpartyId()).isEqualTo("ACC-2");
        assertThat(ledgerOf("ACC-2").get(0).counterpartyId()).isEqualTo("ACC-1");
    }

    @Test
    void transferReturnsAnIdThatBothLedgerEntriesShare() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        String transferId = service.transfer("ACC-1", "ACC-2", Money.of("30.00"));

        assertThat(transferId).isNotBlank();
        assertThat(ledgerOf("ACC-1").get(0).transferId()).isEqualTo(transferId);
        assertThat(ledgerOf("ACC-2").get(0).transferId()).isEqualTo(transferId);
    }

    @Test
    void everyTransferGetsItsOwnId() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("20.00"));

        String first = service.transfer("ACC-1", "ACC-2", Money.of("1.00"));
        String second = service.transfer("ACC-1", "ACC-2", Money.of("1.00"));

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void transferIdsComeFromTheInjectedGenerator() {
        BankingService fixed = new BankingService(
                new InMemoryAccountRepository(), Clock.systemUTC(), () -> "TRF-1");
        fixed.openAccount("ACC-1", Money.of("100.00"));
        fixed.openAccount("ACC-2", Money.of("20.00"));

        assertThat(fixed.transfer("ACC-1", "ACC-2", Money.of("5.00"))).isEqualTo("TRF-1");
    }
}