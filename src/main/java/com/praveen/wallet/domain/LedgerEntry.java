package com.praveen.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One immutable row per money movement on an account. */
@Entity
@Table(name = "ledger_entry", indexes = @Index(name = "idx_ledger_account", columnList = "accountId"))
public class LedgerEntry {

    public enum Type { DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN }

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID accountId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    private UUID transferId;

    private UUID counterpartyId;

    @Column(nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID accountId, Type type, BigDecimal amount, BigDecimal balanceAfter,
                       UUID transferId, UUID counterpartyId) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.type = type.name();
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.transferId = transferId;
        this.counterpartyId = counterpartyId;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public UUID getTransferId() { return transferId; }
    public UUID getCounterpartyId() { return counterpartyId; }
    public Instant getCreatedAt() { return createdAt; }
}
