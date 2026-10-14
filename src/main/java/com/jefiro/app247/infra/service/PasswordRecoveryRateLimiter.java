package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PasswordRecoveryRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryRateLimiter.class);
    @Autowired private StringRedisTemplate redis;
    @Autowired private CustomerIdentityHashService hashes;
    @Value("${app.security.password-recovery.window:PT15M}") private Duration window;
    @Value("${app.security.password-recovery.request-limit:5}") private long requestLimit;
    @Value("${app.security.password-recovery.validate-limit:5}") private long validateLimit;
    @Value("${app.security.password-recovery.reset-limit:5}") private long resetLimit;
    private final Map<String, LocalWindow> fallback = new ConcurrentHashMap<>();

    public void request(String ip, String cpf) { require("request", ip, cpf, requestLimit); }
    public void validate(String ip, String cpf) { require("validate", ip, cpf, validateLimit); }
    public void reset(String ip, String cpf) { require("reset", ip, cpf, resetLimit); }

    private void require(String operation, String ip, String cpf, long maximum) {
        String identity = hashes.hash("password-rate-identity", cpf);
        String origin = hashes.hash("password-rate-origin", ip == null ? "unknown" : ip);
        if (!increment(operation + ":identity:" + identity, maximum)
                || !increment(operation + ":origin:" + origin, maximum * 4)) {
            log.warn("[PASSWORD-RECOVERY] operation={} status=RATE_LIMITED", operation);
            throw new ApiBusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "PASSWORD_RESET_RATE_LIMITED",
                    "Muitas tentativas de recuperação. Tente novamente mais tarde.");
        }
    }

    private boolean increment(String suffix, long maximum) {
        String key = "app247:password-rate:" + suffix;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, window);
            return count == null || count <= maximum;
        } catch (RuntimeException unavailable) {
            log.warn("Redis indisponível para rate limit de recuperação; usando limite local");
            long now = System.currentTimeMillis();
            LocalWindow value = fallback.compute(key, (ignored, current) ->
                    current == null || current.resetAt <= now
                            ? new LocalWindow(1, now + window.toMillis())
                            : new LocalWindow(current.count + 1, current.resetAt));
            return value.count <= maximum;
        }
    }

    private record LocalWindow(long count, long resetAt) {}
}
