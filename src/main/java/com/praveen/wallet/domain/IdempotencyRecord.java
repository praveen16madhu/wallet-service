package com.praveen.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Remembers the outcome of a request so a retry with the same key replays it instead of running again. */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    private String id;

    @Column(nullable = false)
    private String requestHash;

    @Column(nullable = false, length = 8000)
    private String responseJson;

    @Column(nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String id, String requestHash, String responseJson) {
        this.id = id;
        this.requestHash = requestHash;
        this.responseJson = responseJson;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getRequestHash() { return requestHash; }
    public String getResponseJson() { return responseJson; }
}
