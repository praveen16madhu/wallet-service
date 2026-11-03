package com.praveen.wallet.service;

import com.praveen.wallet.domain.Account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountView(UUID id, String owner, BigDecimal balance) {
    public static AccountView of(Account a) {
        return new AccountView(a.getId(), a.getOwner(), a.getBalance());
    }
}
