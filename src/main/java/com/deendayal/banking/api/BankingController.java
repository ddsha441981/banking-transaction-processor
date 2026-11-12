package com.deendayal.banking.api;

import com.deendayal.banking.BankingService;
import com.deendayal.banking.Money;
import com.deendayal.banking.Transaction;
import com.deendayal.banking.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BankingController {

    private final BankingService bank;

    public BankingController(BankingService bank) {
        this.bank = bank;
    }

    @PostMapping("/accounts")
    public ResponseEntity<BalanceResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        bank.openAccount(request.id(), Money.of(request.openingBalance()));
        URI location = UriComponentsBuilder.fromPath("/api/accounts/{id}/balance")
                .buildAndExpand(request.id()).encode().toUri();
        return ResponseEntity.created(location).body(balanceOf(request.id()));
    }

    @GetMapping("/accounts/{id}/balance")
    public BalanceResponse balance(@PathVariable String id) {
        return balanceOf(id);
    }

    private BalanceResponse balanceOf(String id) {
        return new BalanceResponse(id, bank.getBalance(id).amount());
    }

    public record OpenAccountRequest(@NotBlank String id, @NotNull BigDecimal openingBalance) {
    }

    public record BalanceResponse(String accountId, BigDecimal balance) {
    }

    @PostMapping("/accounts/{id}/deposits")
    public BalanceResponse deposit(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        bank.deposit(id, Money.of(request.amount()));
        return balanceOf(id);
    }

    @PostMapping("/accounts/{id}/withdrawals")
    public BalanceResponse withdraw(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        bank.withdraw(id, Money.of(request.amount()));
        return balanceOf(id);
    }

    @PostMapping("/transfers")
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        String transferId = bank.transfer(request.fromAccountId(), request.toAccountId(),
                Money.of(request.amount()));
        return new TransferResponse(transferId);
    }

    @GetMapping("/accounts/{id}/transactions")
    public List<TransactionResponse> history(@PathVariable String id) {
        return bank.getTransactionHistory(id).stream().map(TransactionResponse::from).toList();
    }

    public record AmountRequest(@NotNull BigDecimal amount) {
    }

    public record TransferRequest(@NotBlank String fromAccountId, @NotBlank String toAccountId,
                                  @NotNull BigDecimal amount) {
    }
    public record TransferResponse(String transferId) {
    }

    public record TransactionResponse(TransactionType type, BigDecimal amount, BigDecimal balanceAfter,
                                      Instant timestamp, String counterpartyId, String transferId) {

        static TransactionResponse from(Transaction t) {
            return new TransactionResponse(t.type(), t.amount().amount(), t.balanceAfter().amount(),
                    t.timestamp(), t.counterpartyId(), t.transferId());
        }
    }
}