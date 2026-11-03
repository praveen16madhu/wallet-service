package com.praveen.wallet;

import com.praveen.wallet.domain.InsufficientFundsException;
import com.praveen.wallet.service.LockingMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyTest extends IntegrationTestBase {

    private record Outcome(int accepted, int rejected) {
    }

    private Outcome withdrawInParallel(UUID id, int threads, BigDecimal amount) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    wallet.withdraw(id, amount);
                    accepted.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    rejected.incrementAndGet();
                }
            });
        }
        ready.await();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        return new Outcome(accepted.get(), rejected.get());
    }

    @Test
    void withLockingFiftyParallelWithdrawalsLeaveExactlyTheRightBalance() throws Exception {
        UUID id = newAccount("a", "100.00");

        Outcome outcome = withdrawInParallel(id, 50, BigDecimal.ONE);

        assertThat(outcome.accepted()).isEqualTo(50);
        assertThat(balanceOf(id)).isEqualByComparingTo("50.00");
    }

    @Test
    void withLockingTheBalanceNeverGoesNegative() throws Exception {
        UUID id = newAccount("a", "10.00");

        Outcome outcome = withdrawInParallel(id, 40, BigDecimal.ONE);

        assertThat(outcome.accepted()).isEqualTo(10);        // exactly the money that exists
        assertThat(outcome.rejected()).isEqualTo(30);
        assertThat(balanceOf(id)).isEqualByComparingTo("0.00");
    }

    @Test
    void withoutLockingUpdatesAreLost() throws Exception {
        settings.setLockingMode(LockingMode.UNSAFE);
        settings.setDelayMs(25);                              // widen the read-to-write window
        UUID id = newAccount("a", "100.00");

        Outcome outcome = withdrawInParallel(id, 20, BigDecimal.ONE);

        BigDecimal expected = new BigDecimal("100.00").subtract(BigDecimal.valueOf(outcome.accepted()));
        assertThat(balanceOf(id)).isGreaterThan(expected);    // some withdrawals were accepted but never subtracted
    }
}
