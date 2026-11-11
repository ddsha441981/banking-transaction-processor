package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingServiceTest {

    private final BankingService service = new BankingService(new InMemoryAccountRepository());

    @Test
    void opensAccountWithOpeningBalance() {
        service.openAccount("ACC-1", Money.of("100.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("100.00"));
    }

    @Test
    void depositChangesOnlyTheTargetedAccount() {
        service.openAccount("ACC-1", Money.of("100.00"));
        service.openAccount("ACC-2", Money.of("50.00"));

        service.deposit("ACC-1", Money.of("25.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("125.00"));
        assertThat(service.getBalance("ACC-2")).isEqualTo(Money.of("50.00"));
    }

    @Test
    void withdrawDecreasesBalance() {
        service.openAccount("ACC-1", Money.of("100.00"));

        service.withdraw("ACC-1", Money.of("40.00"));

        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("60.00"));
    }

    @Test
    void overdraftIsRejectedAndBalanceIsUnchanged() {
        service.openAccount("ACC-1", Money.of("10.00"));

        assertThatThrownBy(() -> service.withdraw("ACC-1", Money.of("10.01")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void openingAnExistingAccountIdIsRejected() {
        service.openAccount("ACC-1", Money.of("10.00"));

        assertThatThrownBy(() -> service.openAccount("ACC-1", Money.of("99.00")))
                .isInstanceOf(DuplicateAccountException.class);
        assertThat(service.getBalance("ACC-1")).isEqualTo(Money.of("10.00"));
    }

    @Test
    void operationsOnAnUnknownAccountFail() {
        assertThatThrownBy(() -> service.deposit("NOPE", Money.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("NOPE");
        assertThatThrownBy(() -> service.withdraw("NOPE", Money.of("1.00")))
                .isInstanceOf(AccountNotFoundException.class);
        assertThatThrownBy(() -> service.getBalance("NOPE"))
                .isInstanceOf(AccountNotFoundException.class);
    }
}