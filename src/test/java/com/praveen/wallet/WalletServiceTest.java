package com.praveen.wallet;

import com.praveen.wallet.domain.InsufficientFundsException;
import com.praveen.wallet.domain.LedgerEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletServiceTest extends IntegrationTestBase {

    @Test
    void depositAndWithdrawUpdateBalanceAndLedger() {
        UUID id = newAccount("asha", "100.00");

        wallet.deposit(id, new BigDecimal("50.00"));
        wallet.withdraw(id, new BigDecimal("30.00"));

        assertThat(balanceOf(id)).isEqualByComparingTo("120.00");
        List<LedgerEntry> history = wallet.history(id);
        assertThat(history).extracting(LedgerEntry::getType).containsExactlyInAnyOrder("DEPOSIT", "WITHDRAW");
    }

    @Test
    void withdrawingMoreThanTheBalanceIsRejectedAndNothingChanges() {
        UUID id = newAccount("ravi", "10.00");

        assertThatThrownBy(() -> wallet.withdraw(id, new BigDecimal("10.01")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(id)).isEqualByComparingTo("10.00");
        assertThat(wallet.history(id)).isEmpty();
    }

    @Test
    void zeroAndNegativeAmountsAreRejected() {
        UUID id = newAccount("meena", "10.00");

        assertThatThrownBy(() -> wallet.deposit(id, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wallet.withdraw(id, new BigDecimal("-5"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(balanceOf(id)).isEqualByComparingTo("10.00");
    }
}
