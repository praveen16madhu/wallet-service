package com.praveen.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.wallet.domain.IdempotencyRecord;
import com.praveen.wallet.repo.IdempotencyRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Runs a money operation at most once per Idempotency-Key.
 *
 * The business change and the key record are saved in the SAME transaction. If two identical requests race,
 * the database primary key lets exactly one commit; the other rolls back (its money change disappears with it)
 * and then replays the stored response.
 */
@Service
public class IdempotencyService {

    private final IdempotencyRepository records;
    private final TransactionTemplate tx;
    private final ObjectMapper json;
    private final EntityManager em;

    public IdempotencyService(IdempotencyRepository records, TransactionTemplate tx, ObjectMapper json, EntityManager em) {
        this.records = records;
        this.tx = tx;
        this.json = json;
        this.em = em;
    }

    public <T> T execute(String key, String requestDescription, Class<T> type, Supplier<T> business) {
        if (key == null || key.isBlank()) {
            return tx.execute(status -> business.get());   // no key: no protection (the UI shows why this is bad)
        }
        String hash = sha256(requestDescription);

        var existing = records.findById(key);
        if (existing.isPresent()) {
            return replay(existing.get(), key, hash, type);
        }

        try {
            return tx.execute(status -> {
                T result = business.get();
                // persist() is a strict INSERT. save() would MERGE: if another request already committed this key,
                // merge turns into an UPDATE, nothing fails, and the money would move twice.
                em.persist(new IdempotencyRecord(key, hash, toJson(result)));
                em.flush();
                return result;
            });
        } catch (DataIntegrityViolationException | PersistenceException duplicate) {
            IdempotencyRecord winner = records.findById(key).orElseThrow(() -> duplicate);
            return replay(winner, key, hash, type);
        }
    }

    private <T> T replay(IdempotencyRecord record, String key, String hash, Class<T> type) {
        if (!record.getRequestHash().equals(hash)) {
            throw new IdempotencyConflictException(key);
        }
        try {
            return json.readValue(record.getResponseJson(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored response for key " + key + " is unreadable", e);
        }
    }

    private String toJson(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
