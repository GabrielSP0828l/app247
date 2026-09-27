package com.jefiro.app247.infra.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RegistrationRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RegistrationRateLimiter.class);
    private static final Duration WINDOW = Duration.ofHours(1);
    private final Map<String, LocalWindow> fallback = new ConcurrentHashMap<>();
    private final StringRedisTemplate redis;

    public RegistrationRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public boolean allow(String ip, String email, String cpf) {
        return allowKey("ip", ip, 20)
                && allowKey("email", email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT), 5)
                && allowKey("cpf", cpf == null ? "unknown" : cpf.replaceAll("\\D", ""), 5);
    }

    private boolean allowKey(String scope, String value, long maximum) {
        String key = "app247:registration-rate:" + scope + ":" + hash(value);
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, WINDOW);
            return count == null || count <= maximum;
        } catch (RuntimeException exception) {
            log.warn("Redis indisponível para rate limit de cadastro; usando limite local");
            return allowLocal(key, maximum);
        }
    }

    private boolean allowLocal(String key, long maximum) {
        long now = System.currentTimeMillis();
        LocalWindow window = fallback.compute(key, (ignored, current) ->
                current == null || current.resetAt() <= now
                        ? new LocalWindow(1, now + WINDOW.toMillis())
                        : new LocalWindow(current.count() + 1, current.resetAt()));
        return window.count() <= maximum;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private record LocalWindow(long count, long resetAt) {}
}
