package com.praveen.wallet.domain;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId, BigDecimal balance, BigDecimal requested) {
        super("Account " + accountId + " has " + balance + ", cannot withdraw " + requested);
    }
}
