package com.deendayal.banking.api;

import com.deendayal.banking.BankingService;
import com.deendayal.banking.Money;
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
}