package com.praveen.wallet.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Idempotent consumer: Kafka may deliver the same event more than once.
 * INSERT ... ON CONFLICT DO NOTHING on the transfer id makes a repeat harmless.
 */
@Component
@ConditionalOnProperty(name = "wallet.kafka.enabled", havingValue = "true")
public class TransferAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransferAuditConsumer.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public TransferAuditConsumer(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @KafkaListener(topics = KafkaConfig.TOPIC)
    public void onMessage(String message) {
        TransferEvent event = parse(message);
        int inserted = jdbc.update("""
                INSERT INTO transfer_audit (transfer_id, from_account, to_account, amount, received_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (transfer_id) DO NOTHING
                """, event.transferId(), event.fromAccount(), event.toAccount(), event.amount());
        log.info("transfer {} {}", event.transferId(), inserted == 1 ? "audited" : "duplicate ignored");
    }

    private TransferEvent parse(String message) {
        try {
            TransferEvent event = json.readValue(message, TransferEvent.class);
            if (event.transferId() == null || event.fromAccount() == null
                    || event.toAccount() == null || event.amount() == null) {
                throw new IllegalArgumentException("Event is missing required fields");
            }
            return event;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event: " + e.getOriginalMessage(), e);   // not retryable
        }
    }
}
