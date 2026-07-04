package ar.com.viajar.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OtpService {
    private static final long TTL_MS = 5 * 60 * 1000L;

    private record OtpEntry(String code, Instant expiresAt) {}

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public void generate(String phone) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        store.put(phone, new OtpEntry(code, Instant.now().plusMillis(TTL_MS)));
        log.info("OTP para {}: {} (Twilio pendiente)", phone, code);
    }

    public boolean verify(String phone, String code) {
        OtpEntry entry = store.get(phone);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            store.remove(phone);
            return false;
        }
        if (entry.code().equals(code)) {
            store.remove(phone);
            return true;
        }
        return false;
    }
}
