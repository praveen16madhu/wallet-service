package com.praveen.wallet.service;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResult(UUID transferId, UUID fromAccount, UUID toAccount, BigDecimal amount,
                             BigDecimal fromBalance, BigDecimal toBalance) {
}
