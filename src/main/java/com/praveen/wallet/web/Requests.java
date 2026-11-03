package com.praveen.wallet.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public final class Requests {

    private Requests() {
    }

    public record CreateAccount(@NotBlank String owner, @NotNull @DecimalMin("0.00") BigDecimal initialBalance) {
    }

    public record Amount(@NotNull @DecimalMin("0.01") BigDecimal amount) {
    }

    public record Transfer(@NotNull UUID fromAccount, @NotNull UUID toAccount,
                           @NotNull @DecimalMin("0.01") BigDecimal amount) {
    }
}
