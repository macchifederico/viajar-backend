package ar.com.viajar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@SpringBootApplication
public class ViajarApplication {

    public static void main(String[] args) {
        SpringApplication.run(ViajarApplication.class, args);
    }

    @RestController
    static class HealthController {
        @GetMapping("/health")
        Map<String, String> health() {
            return Map.of("status", "ok", "timestamp", Instant.now().toString());
        }
    }
}
