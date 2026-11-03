package com.praveen.wallet.web;

import com.praveen.wallet.domain.LedgerEntry;
import com.praveen.wallet.service.AccountView;
import com.praveen.wallet.service.IdempotencyService;
import com.praveen.wallet.service.TransferResult;
import com.praveen.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WalletController {

    private static final String KEY = "Idempotency-Key";

    private final WalletService wallet;
    private final IdempotencyService idempotency;

    public WalletController(WalletService wallet, IdempotencyService idempotency) {
        this.wallet = wallet;
        this.idempotency = idempotency;
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountView create(@Valid @RequestBody Requests.CreateAccount body) {
        return wallet.createAccount(body.owner(), body.initialBalance());
    }

    @GetMapping("/accounts")
    public List<AccountView> list() {
        return wallet.list();
    }

    @GetMapping("/accounts/{id}")
    public AccountView get(@PathVariable UUID id) {
        return wallet.get(id);
    }

    @GetMapping("/accounts/{id}/transactions")
    public List<LedgerEntry> history(@PathVariable UUID id) {
        return wallet.history(id);
    }

    @PostMapping("/accounts/{id}/deposit")
    public AccountView deposit(@PathVariable UUID id, @Valid @RequestBody Requests.Amount body,
                               @RequestHeader(value = KEY, required = false) String key) {
        return idempotency.execute(key, "deposit|" + id + "|" + body.amount().toPlainString(), AccountView.class,
                () -> wallet.deposit(id, body.amount()));
    }

    @PostMapping("/accounts/{id}/withdraw")
    public AccountView withdraw(@PathVariable UUID id, @Valid @RequestBody Requests.Amount body,
                                @RequestHeader(value = KEY, required = false) String key) {
        return idempotency.execute(key, "withdraw|" + id + "|" + body.amount().toPlainString(), AccountView.class,
                () -> wallet.withdraw(id, body.amount()));
    }

    @PostMapping("/transfers")
    public TransferResult transfer(@Valid @RequestBody Requests.Transfer body,
                                   @RequestHeader(value = KEY, required = false) String key) {
        String description = "transfer|" + body.fromAccount() + "|" + body.toAccount() + "|" + body.amount().toPlainString();
        return idempotency.execute(key, description, TransferResult.class,
                () -> wallet.transfer(body.fromAccount(), body.toAccount(), body.amount(), false));
    }
}
