package com.praveen.wallet.service;

import com.praveen.wallet.domain.Account;
import com.praveen.wallet.domain.AccountNotFoundException;
import com.praveen.wallet.domain.LedgerEntry;
import com.praveen.wallet.repo.AccountRepository;
import com.praveen.wallet.repo.LedgerRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final DemoSettings demo;
    private final ApplicationEventPublisher events;

    public WalletService(AccountRepository accounts, LedgerRepository ledger,
                         DemoSettings demo, ApplicationEventPublisher events) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.demo = demo;
        this.events = events;
    }

    @Transactional
    public AccountView createAccount(String owner, BigDecimal initialBalance) {
        requireNotNegative(initialBalance);
        Account account = accounts.save(new Account(owner, initialBalance));
        return AccountView.of(account);
    }

    @Transactional(readOnly = true)
    public AccountView get(UUID id) {
        return AccountView.of(accounts.findById(id).orElseThrow(() -> new AccountNotFoundException(id)));
    }

    @Transactional(readOnly = true)
    public List<AccountView> list() {
        return accounts.findAll().stream().map(AccountView::of).toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> history(UUID accountId) {
        return ledger.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    @Transactional
    public AccountView deposit(UUID id, BigDecimal amount) {
        requirePositive(amount);
        Account account = load(id);
        demo.pause();
        account.credit(amount);
        ledger.save(new LedgerEntry(id, LedgerEntry.Type.DEPOSIT, amount, account.getBalance(), null, null));
        return AccountView.of(account);
    }

    @Transactional
    public AccountView withdraw(UUID id, BigDecimal amount) {
        requirePositive(amount);
        Account account = load(id);
        demo.pause();
        account.debit(amount);
        ledger.save(new LedgerEntry(id, LedgerEntry.Type.WITHDRAW, amount, account.getBalance(), null, null));
        return AccountView.of(account);
    }

    /**
     * Debit and credit happen in one transaction: both are saved or neither is.
     * Locks are taken in a fixed order (smaller id first) so two opposite transfers cannot deadlock.
     */
    @Transactional
    public TransferResult transfer(UUID from, UUID to, BigDecimal amount, boolean crashAfterDebit) {
        requirePositive(amount);
        if (from.equals(to)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        UUID first = from.compareTo(to) < 0 ? from : to;
        UUID second = first.equals(from) ? to : from;
        Account a1 = load(first);
        Account a2 = load(second);
        Account source = first.equals(from) ? a1 : a2;
        Account target = first.equals(from) ? a2 : a1;

        demo.pause();
        UUID transferId = UUID.randomUUID();

        source.debit(amount);
        ledger.save(new LedgerEntry(from, LedgerEntry.Type.TRANSFER_OUT, amount, source.getBalance(), transferId, to));

        if (crashAfterDebit) {
            throw new SimulatedCrashException("Crashed after the debit, before the credit");
        }

        target.credit(amount);
        ledger.save(new LedgerEntry(to, LedgerEntry.Type.TRANSFER_IN, amount, target.getBalance(), transferId, from));

        events.publishEvent(new TransferCompleted(transferId, from, to, amount));
        return new TransferResult(transferId, from, to, amount, source.getBalance(), target.getBalance());
    }

    private Account load(UUID id) {
        var found = demo.lockingMode() == LockingMode.SAFE ? accounts.findByIdForUpdate(id) : accounts.findById(id);
        return found.orElseThrow(() -> new AccountNotFoundException(id));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private static void requireNotNegative(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
    }
}
