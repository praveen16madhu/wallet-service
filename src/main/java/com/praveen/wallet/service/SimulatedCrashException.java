package com.praveen.wallet.service;

/** Thrown on purpose after the debit to show that the whole transfer rolls back. */
public class SimulatedCrashException extends RuntimeException {
    public SimulatedCrashException(String message) {
        super(message);
    }
}
