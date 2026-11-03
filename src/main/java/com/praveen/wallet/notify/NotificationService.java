package com.praveen.wallet.notify;

import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Calls an external notification service with explicit timeouts and a bounded retry.
 *
 * connect timeout: how long to wait to open the connection (nobody picks up the phone).
 * read timeout:    how long to wait for the answer once connected (picked up, then silence).
 */
@Service
public class NotificationService {

    static final int CONNECT_TIMEOUT_MS = 1_000;
    static final int READ_TIMEOUT_MS = 1_500;

    private final RestClient client;
    private final String baseUrl;

    public NotificationService(@Value("${wallet.notify.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.client = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl;
    }

    /**
     * @param mode     ok | slow | flaky | fail | down
     * @param attempts shared across retries so we can report how many calls were made
     */
    @Retry(name = "notify", fallbackMethod = "giveUp")
    public NotifyResult send(String mode, AtomicInteger attempts) {
        int attempt = attempts.incrementAndGet();
        long start = System.currentTimeMillis();
        String url = "down".equals(mode)
                ? "http://localhost:9/notify"                       // nothing listens on port 9: connection refused
                : baseUrl + "?mode=" + mode + "&attempt=" + attempt;
        String body = client.get().uri(url).retrieve().body(String.class);
        return new NotifyResult(true, attempt, System.currentTimeMillis() - start, body);
    }

    NotifyResult giveUp(String mode, AtomicInteger attempts, Exception error) {
        return new NotifyResult(false, attempts.get(), 0,
                error.getClass().getSimpleName() + ": " + error.getMessage());
    }
}
