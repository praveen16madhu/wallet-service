package com.praveen.wallet.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Switches the UI uses to make races visible. Defaults are the production-safe values. */
@Component
public class DemoSettings {

    private final AtomicReference<LockingMode> lockingMode = new AtomicReference<>(LockingMode.SAFE);
    private final AtomicInteger delayMs;

    public DemoSettings(@Value("${wallet.demo.delay-ms:0}") int delayMs) {
        this.delayMs = new AtomicInteger(delayMs);
    }

    public LockingMode lockingMode() { return lockingMode.get(); }
    public void setLockingMode(LockingMode mode) { lockingMode.set(mode); }
    public int delayMs() { return delayMs.get(); }
    public void setDelayMs(int ms) { delayMs.set(Math.max(0, ms)); }

    /** Simulates the time real work takes between reading a balance and writing it back. */
    public void pause() {
        int ms = delayMs.get();
        if (ms > 0) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
