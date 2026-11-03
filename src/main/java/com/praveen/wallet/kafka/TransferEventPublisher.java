package com.praveen.wallet.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.praveen.wallet.service.TransferCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Publishes only AFTER the transfer committed, so a rolled-back transfer never produces an event. */
@Component
@ConditionalOnProperty(name = "wallet.kafka.enabled", havingValue = "true")
public class TransferEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransferEventPublisher.class);

    private final KafkaTemplate<Object, Object> kafka;
    private final ObjectMapper json;

    public TransferEventPublisher(KafkaTemplate<Object, Object> kafka, ObjectMapper json) {
        this.kafka = kafka;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTransfer(TransferCompleted event) throws JsonProcessingException {
        TransferEvent payload = new TransferEvent(event.transferId(), event.fromAccount(), event.toAccount(), event.amount());
        kafka.send(KafkaConfig.TOPIC, event.transferId().toString(), json.writeValueAsString(payload));
        log.info("published transfer event {}", event.transferId());
    }
}
