package com.praveen.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Written by the Kafka consumer. transferId is the primary key, so a redelivered event cannot add a second row. */
@Entity
@Table(name = "transfer_audit")
public class TransferAudit {

    @Id
    private UUID transferId;

    @Column(nullable = false)
    private UUID fromAccount;

    @Column(nullable = false)
    private UUID toAccount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant receivedAt;

    protected TransferAudit() {
    }

    public UUID getTransferId() { return transferId; }
    public UUID getFromAccount() { return fromAccount; }
    public UUID getToAccount() { return toAccount; }
    public BigDecimal getAmount() { return amount; }
    public Instant getReceivedAt() { return receivedAt; }
}
