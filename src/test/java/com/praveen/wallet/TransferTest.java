package com.praveen.wallet;

import com.praveen.wallet.domain.InsufficientFundsException;
import com.praveen.wallet.service.SimulatedCrashException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest extends IntegrationTestBase {

    @Test
    void transferMovesMoneyAndWritesBothLedgerEntries() {
        UUID a = newAccount("a", "100.00");
        UUID b = newAccount("b", "5.00");

        wallet.transfer(a, b, new BigDecimal("40.00"), false);

        assertThat(balanceOf(a)).isEqualByComparingTo("60.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("45.00");
        assertThat(wallet.history(a)).hasSize(1);
        assertThat(wallet.history(b)).hasSize(1);
    }

    @Test
    void crashAfterTheDebitRollsBackEverything() {
        UUID a = newAccount("a", "100.00");
        UUID b = newAccount("b", "5.00");

        assertThatThrownBy(() -> wallet.transfer(a, b, new BigDecimal("40.00"), true))
                .isInstanceOf(SimulatedCrashException.class);

        assertThat(balanceOf(a)).isEqualByComparingTo("100.00");   // the debit was undone
        assertThat(balanceOf(b)).isEqualByComparingTo("5.00");
        assertThat(wallet.history(a)).isEmpty();                    // and so was the ledger row
    }

    @Test
    void insufficientFundsLeavesBothAccountsUntouched() {
        UUID a = newAccount("a", "10.00");
        UUID b = newAccount("b", "5.00");

        assertThatThrownBy(() -> wallet.transfer(a, b, new BigDecimal("50.00"), false))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(a)).isEqualByComparingTo("10.00");
        assertThat(balanceOf(b)).isEqualByComparingTo("5.00");
    }

    @Test
    void transferToTheSameAccountIsRejected() {
        UUID a = newAccount("a", "10.00");
        assertThatThrownBy(() -> wallet.transfer(a, a, BigDecimal.ONE, false)).isInstanceOf(IllegalArgumentException.class);
    }

    /** A to B and B to A at the same time would deadlock if locks were taken in opposite orders. */
    @Test
    void oppositeTransfersAtTheSameTimeDoNotDeadlockAndConserveMoney() throws Exception {
        UUID a = newAccount("a", "1000.00");
        UUID b = newAccount("b", "1000.00");
        int rounds = 100;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> work = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            work.add(pool.submit(() -> { go.await(); wallet.transfer(a, b, BigDecimal.ONE, false); return null; }));
            work.add(pool.submit(() -> { go.await(); wallet.transfer(b, a, BigDecimal.ONE, false); return null; }));
        }
        go.countDown();
        for (Future<?> f : work) {
            f.get(30, TimeUnit.SECONDS);       // a deadlock would time out here
        }
        pool.shutdown();

        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("2000.00");
        assertThat(balanceOf(a)).isEqualByComparingTo("1000.00");   // equal numbers of transfers each way
    }
}
