package com.praveen.wallet.web;

import com.praveen.wallet.domain.InsufficientFundsException;
import com.praveen.wallet.domain.TransferAudit;
import com.praveen.wallet.notify.NotificationService;
import com.praveen.wallet.notify.NotifyResult;
import com.praveen.wallet.repo.TransferAuditRepository;
import com.praveen.wallet.service.DemoSettings;
import com.praveen.wallet.service.LockingMode;
import com.praveen.wallet.service.SimulatedCrashException;
import com.praveen.wallet.service.WalletService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Endpoints that exist only so the UI can demonstrate each problem and its fix. */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final WalletService wallet;
    private final DemoSettings settings;
    private final NotificationService notifications;
    private final TransferAuditRepository audits;

    public DemoController(WalletService wallet, DemoSettings settings,
                          NotificationService notifications, TransferAuditRepository audits) {
        this.wallet = wallet;
        this.settings = settings;
        this.notifications = notifications;
        this.audits = audits;
    }

    @GetMapping("/settings")
    public Map<String, Object> settings() {
        return Map.of("lockingMode", settings.lockingMode(), "delayMs", settings.delayMs());
    }

    @PostMapping("/settings")
    public Map<String, Object> update(@RequestParam LockingMode lockingMode, @RequestParam(defaultValue = "0") int delayMs) {
        settings.setLockingMode(lockingMode);
        settings.setDelayMs(delayMs);
        return settings();
    }

    /** N threads withdraw at the same instant. With locking the numbers add up; without it money is "lost". */
    @PostMapping("/concurrent-withdrawals")
    public Map<String, Object> concurrentWithdrawals(@RequestParam UUID accountId,
                                                     @RequestParam(defaultValue = "20") int threads,
                                                     @RequestParam(defaultValue = "1") BigDecimal amount) throws InterruptedException {
        BigDecimal start = wallet.get(accountId).balance();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    wallet.withdraw(accountId, amount);
                    succeeded.incrementAndGet();
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
        pool.awaitTermination(60, TimeUnit.SECONDS);

        BigDecimal actual = wallet.get(accountId).balance();
        BigDecimal expected = start.subtract(amount.multiply(BigDecimal.valueOf(succeeded.get())));
        return Map.of(
                "lockingMode", settings.lockingMode(),
                "startBalance", start,
                "withdrawalsAccepted", succeeded.get(),
                "withdrawalsRejected", rejected.get(),
                "expectedBalance", expected,
                "actualBalance", actual,
                "moneyLost", actual.compareTo(expected) != 0);
    }

    /** Debits, then "crashes" before crediting. The transaction rolls back, so no money disappears. */
    @PostMapping("/transfer-crash")
    public Map<String, Object> transferCrash(@RequestParam UUID from, @RequestParam UUID to,
                                             @RequestParam BigDecimal amount) {
        BigDecimal fromBefore = wallet.get(from).balance();
        BigDecimal toBefore = wallet.get(to).balance();
        String outcome;
        try {
            wallet.transfer(from, to, amount, true);
            outcome = "completed";
        } catch (SimulatedCrashException e) {
            outcome = e.getMessage();
        }
        return Map.of(
                "outcome", outcome,
                "fromBefore", fromBefore, "fromAfter", wallet.get(from).balance(),
                "toBefore", toBefore, "toAfter", wallet.get(to).balance());
    }

    /** mode: ok | slow | flaky | fail | down */
    @PostMapping("/notify")
    public NotifyResult notifyDemo(@RequestParam(defaultValue = "ok") String mode) {
        return notifications.send(mode, new AtomicInteger());
    }

    @GetMapping("/audit")
    public List<TransferAudit> audit() {
        return audits.findAll();
    }
}
