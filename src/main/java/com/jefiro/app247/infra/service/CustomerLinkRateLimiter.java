package com.jefiro.app247.infra.service;

import com.jefiro.app247.infra.exception.ApiBusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class CustomerLinkRateLimiter {
    @Autowired private StringRedisTemplate redis;
    @Value("${app.customer-link.rate-limit-per-minute:30}") private long limit;

    public void check(String terminalId) {
        long minute = Instant.now().getEpochSecond() / 60;
        String key = "rate:customer-link:terminal:" + terminalId + ":" + minute;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(2));
            if (count != null && count > Math.max(5, limit)) {
                throw new ApiBusinessException(HttpStatus.TOO_MANY_REQUESTS,
                        "CUSTOMER_LINK_RATE_LIMITED", "Muitas tentativas de identificação");
            }
        } catch (ApiBusinessException exception) {
            throw exception;
        } catch (RuntimeException unavailable) {
            // Alta entropia + credencial individual continuam protegendo a rota.
            // Indisponibilidade do Redis não deve parar vendas no Terminal.
        }
    }
}
