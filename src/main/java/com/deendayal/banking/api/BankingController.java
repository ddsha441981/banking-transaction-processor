package com.deendayal.banking.api;

import com.deendayal.banking.BankingService;
import com.deendayal.banking.Money;
import com.deendayal.banking.Transaction;
import com.deendayal.banking.TransactionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api")
public class BankingController {

    private final BankingService bank;
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final IdempotencyStore idempotency;

    public BankingController(BankingService bank, IdempotencyStore idempotency) {
        this.bank = bank;
        this.idempotency = idempotency;
    }

//    public BankingController(BankingService bank) {
//        this.bank = bank;
//    }

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
    public ResponseEntity<BalanceResponse> deposit(
            @PathVariable String id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key) {
        Money amount = Money.of(request.amount()); // invalid input is rejected before the key is used
        return idempotent(key, List.of("deposit", id, amount.toString()), () -> {
            bank.deposit(id, amount);
            return balanceOf(id);
        });
    }


    @PostMapping("/accounts/{id}/withdrawals")
    public ResponseEntity<BalanceResponse> withdraw(
            @PathVariable String id,
            @Valid @RequestBody AmountRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key) {
        Money amount = Money.of(request.amount());
        return idempotent(key, List.of("withdraw", id, amount.toString()), () -> {
            bank.withdraw(id, amount);
            return balanceOf(id);
        });
    }

    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String key) {
        Money amount = Money.of(request.amount());
        List<String> fingerprint = List.of(
                "transfer", request.fromAccountId(), request.toAccountId(), amount.toString());
        return idempotent(key, fingerprint, () ->
                new TransferResponse(bank.transfer(request.fromAccountId(), request.toAccountId(), amount)));
    }

    private <T> ResponseEntity<T> idempotent(String key, List<String> fingerprint, Supplier<T> operation) {
        if (key == null) {
            return ResponseEntity.ok(operation.get());
        }
        IdempotencyStore.Result<T> result = idempotency.execute(key, fingerprint, operation);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (result.replayed()) {
            response.header("Idempotent-Replayed", "true");
        }
        return response.body(result.value());
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