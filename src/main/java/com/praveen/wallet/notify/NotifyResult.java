package com.praveen.wallet.notify;

public record NotifyResult(boolean delivered, int attempts, long elapsedMs, String detail) {
}
