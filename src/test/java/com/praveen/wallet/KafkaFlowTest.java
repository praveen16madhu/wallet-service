package com.praveen.wallet;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "wallet.kafka.enabled=true",
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"})
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = {"wallet.transfers", "wallet.transfers.DLT"})
@DirtiesContext
class KafkaFlowTest extends IntegrationTestBase {

    @Autowired KafkaTemplate<Object, Object> kafka;
    @Autowired EmbeddedKafkaBroker broker;

    private String event(UUID transferId) {
        return "{\"transferId\":\"" + transferId + "\",\"fromAccount\":\"" + UUID.randomUUID()
                + "\",\"toAccount\":\"" + UUID.randomUUID() + "\",\"amount\":10.00}";
    }

    @Test
    void aCommittedTransferIsAuditedViaKafka() {
        UUID a = newAccount("a", "100.00");
        UUID b = newAccount("b", "0.00");

        var result = wallet.transfer(a, b, new BigDecimal("25.00"), false);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(audits.findById(result.transferId())).isPresent());
    }

    @Test
    void aRolledBackTransferPublishesNothing() throws Exception {
        UUID a = newAccount("a", "100.00");
        UUID b = newAccount("b", "0.00");

        try {
            wallet.transfer(a, b, new BigDecimal("25.00"), true);
        } catch (RuntimeException expected) {
            // simulated crash
        }

        Thread.sleep(2_000);
        assertThat(audits.count()).isZero();
    }

    @Test
    void theSameEventDeliveredTwiceIsStoredOnce() {
        UUID transferId = UUID.randomUUID();
        String payload = event(transferId);

        kafka.send("wallet.transfers", transferId.toString(), payload);
        kafka.send("wallet.transfers", transferId.toString(), payload);   // duplicate delivery
        UUID marker = UUID.randomUUID();
        kafka.send("wallet.transfers", marker.toString(), event(marker));  // proves the duplicate was processed first

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(audits.findById(marker)).isPresent());
        assertThat(audits.findAll().stream().filter(x -> x.getTransferId().equals(transferId))).hasSize(1);
    }

    @Test
    void aMalformedEventGoesToTheDeadLetterTopicAndDoesNotBlockTheNext() {
        kafka.send("wallet.transfers", "bad", "this is not json");
        UUID good = UUID.randomUUID();
        kafka.send("wallet.transfers", good.toString(), event(good));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(audits.findById(good)).isPresent());          // the good one was not blocked

        Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-check", "true", broker);
        props.put("auto.offset.reset", "earliest");
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), new StringDeserializer()).createConsumer()) {
            broker.consumeFromAnEmbeddedTopic(consumer, "wallet.transfers.DLT");
            ConsumerRecord<String, String> parked = KafkaTestUtils.getSingleRecord(consumer, "wallet.transfers.DLT",
                    Duration.ofSeconds(20));
            assertThat(parked.value()).isEqualTo("this is not json");
        }
    }
}
