package com.praveen.wallet.notify;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Stands in for an unreliable third-party service so timeouts and retries can be seen. */
@RestController
@RequestMapping("/mock")
public class MockNotifyController {

    @GetMapping("/notify")
    public ResponseEntity<String> notify(@RequestParam String mode,
                                         @RequestParam(defaultValue = "1") int attempt) throws InterruptedException {
        return switch (mode) {
            case "slow" -> {
                Thread.sleep(5_000);                                  // longer than the 1.5s read timeout
                yield ResponseEntity.ok("delivered (too late)");
            }
            case "flaky" -> attempt < 3
                    ? ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("busy, try again")
                    : ResponseEntity.ok("delivered on attempt " + attempt);
            case "fail" -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("always broken");
            default -> ResponseEntity.ok("delivered");
        };
    }
}
