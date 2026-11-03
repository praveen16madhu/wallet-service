package com.praveen.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Published inside the transfer transaction; listeners only see it after the commit succeeded. */
public record TransferCompleted(UUID transferId, UUID fromAccount, UUID toAccount, BigDecimal amount) {
}
