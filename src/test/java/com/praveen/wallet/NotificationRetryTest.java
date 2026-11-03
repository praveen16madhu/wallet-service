package com.praveen.wallet;

import com.praveen.wallet.notify.NotificationService;
import com.praveen.wallet.notify.NotifyResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=18089", "wallet.notify.url=http://localhost:18089/mock/notify"})
@ActiveProfiles("test")
class NotificationRetryTest {

    @Autowired NotificationService notifications;

    @Test
    void healthyServiceIsCalledOnce() {
        NotifyResult r = notifications.send("ok", new AtomicInteger());
        assertThat(r.delivered()).isTrue();
        assertThat(r.attempts()).isEqualTo(1);
    }

    @Test
    void flakyServiceSucceedsOnTheThirdAttempt() {
        NotifyResult r = notifications.send("flaky", new AtomicInteger());
        assertThat(r.delivered()).isTrue();
        assertThat(r.attempts()).isEqualTo(3);
    }

    @Test
    void slowServiceHitsTheReadTimeoutAndGivesUpAfterThreeAttempts() {
        NotifyResult r = notifications.send("slow", new AtomicInteger());
        assertThat(r.delivered()).isFalse();
        assertThat(r.attempts()).isEqualTo(3);
        assertThat(r.detail().toLowerCase()).contains("timed out");
    }

    @Test
    void serverDownIsRefusedImmediatelyNotATimeout() {
        NotifyResult r = notifications.send("down", new AtomicInteger());
        assertThat(r.delivered()).isFalse();
        assertThat(r.attempts()).isEqualTo(3);
        assertThat(r.detail().toLowerCase()).contains("connection refused");
    }
}
