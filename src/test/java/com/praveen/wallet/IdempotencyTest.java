package com.praveen.wallet;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class IdempotencyTest extends IntegrationTestBase {

    @Autowired MockMvc mvc;

    private MvcResult deposit(UUID id, String amount, String key) throws Exception {
        var request = post("/api/accounts/" + id + "/deposit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + "}");
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mvc.perform(request).andReturn();
    }

    @Test
    void sameKeyTwiceChangesTheBalanceOnceAndReplaysTheAnswer() throws Exception {
        UUID id = newAccount("a", "100.00");

        MvcResult first = deposit(id, "25.00", "key-1");
        MvcResult second = deposit(id, "25.00", "key-1");

        assertThat(first.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getStatus()).isEqualTo(200);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        assertThat(balanceOf(id)).isEqualByComparingTo("125.00");     // not 150
        assertThat(wallet.history(id)).hasSize(1);
    }

    @Test
    void withoutAKeyTheSameRequestRunsTwice() throws Exception {
        UUID id = newAccount("a", "100.00");

        deposit(id, "25.00", null);
        deposit(id, "25.00", null);

        assertThat(balanceOf(id)).isEqualByComparingTo("150.00");     // the double-submit problem
    }

    @Test
    void reusingAKeyWithADifferentBodyIsRejected() throws Exception {
        UUID id = newAccount("a", "100.00");

        deposit(id, "25.00", "key-2");
        MvcResult other = deposit(id, "99.00", "key-2");

        assertThat(other.getResponse().getStatus()).isEqualTo(422);
        assertThat(balanceOf(id)).isEqualByComparingTo("125.00");
    }

    @Test
    void tenIdenticalRequestsAtOnceApplyExactlyOnce() throws Exception {
        UUID id = newAccount("a", "100.00");
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcResult>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> { go.await(); return deposit(id, "10.00", "race-key"); }));
        }
        go.countDown();
        Set<String> bodies = new HashSet<>();
        for (Future<MvcResult> f : results) {
            MvcResult r = f.get(30, TimeUnit.SECONDS);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            bodies.add(r.getResponse().getContentAsString());
        }
        pool.shutdown();

        assertThat(balanceOf(id)).isEqualByComparingTo("110.00");     // one deposit, not ten
        assertThat(wallet.history(id)).hasSize(1);
        assertThat(bodies).hasSize(1);                                // every caller got the same answer
    }
}
