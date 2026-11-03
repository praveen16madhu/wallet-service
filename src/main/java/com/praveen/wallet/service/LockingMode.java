package com.praveen.wallet.service;

public enum LockingMode {
    /** SELECT ... FOR UPDATE: concurrent writers queue up, so no update is lost. */
    SAFE,
    /** Plain read then write: two requests can read the same balance and overwrite each other. Demo only. */
    UNSAFE
}
