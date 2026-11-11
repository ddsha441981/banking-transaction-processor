package com.deendayal.banking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryAccountRepositoryTest {

    private final AccountRepository repository = new InMemoryAccountRepository();

    @Test
    void findsAnAccountThatWasAdded() {
        Account account = new Account("ACC-1", Money.of("10.00"));

        repository.add(account);

        assertThat(repository.findById("ACC-1")).containsSame(account);
    }

    @Test
    void returnsEmptyForUnknownId() {
        assertThat(repository.findById("NOPE")).isEmpty();
    }

    @Test
    void rejectsDuplicateIdAndKeepsTheOriginalAccount() {
        Account original = new Account("ACC-1", Money.of("10.00"));
        repository.add(original);

        assertThatThrownBy(() -> repository.add(new Account("ACC-1", Money.of("99.00"))))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("ACC-1");
        assertThat(repository.findById("ACC-1")).containsSame(original);
    }
}