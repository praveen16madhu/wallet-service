package com.praveen.wallet.kafka;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferEvent(UUID transferId, UUID fromAccount, UUID toAccount, BigDecimal amount) {
}
