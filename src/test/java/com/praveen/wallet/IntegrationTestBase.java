package com.praveen.wallet;

import com.praveen.wallet.repo.AccountRepository;
import com.praveen.wallet.repo.IdempotencyRepository;
import com.praveen.wallet.repo.LedgerRepository;
import com.praveen.wallet.repo.TransferAuditRepository;
import com.praveen.wallet.service.DemoSettings;
import com.praveen.wallet.service.LockingMode;
import com.praveen.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

/** Uses the local PostgreSQL database "wallet_test" (locking behaviour must be tested on a real database). */
@SpringBootTest
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Autowired protected WalletService wallet;
    @Autowired protected AccountRepository accounts;
    @Autowired protected LedgerRepository ledger;
    @Autowired protected IdempotencyRepository idempotency;
    @Autowired protected TransferAuditRepository audits;
    @Autowired protected DemoSettings settings;

    @BeforeEach
    void cleanDatabase() {
        ledger.deleteAll();
        idempotency.deleteAll();
        audits.deleteAll();
        accounts.deleteAll();
        settings.setLockingMode(LockingMode.SAFE);
        settings.setDelayMs(0);
    }

    protected UUID newAccount(String owner, String balance) {
        return wallet.createAccount(owner, new BigDecimal(balance)).id();
    }

    protected BigDecimal balanceOf(UUID id) {
        return wallet.get(id).balance();
    }
}
