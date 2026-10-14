package com.jefiro.app247.infra.service;

import com.jefiro.app247.domain.model.auth.User;
import com.jefiro.app247.domain.model.dto.ResetPasswordRequest;
import com.jefiro.app247.domain.model.dto.ValidateCodeRequest;
import com.jefiro.app247.infra.exception.ApiBusinessException;
import com.jefiro.app247.infra.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

@Service
public class PasswordRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CONSUME_CODE = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]); return 1 else return 0 end", Long.class);
    private static final DefaultRedisScript<Long> CONSUME_TOKEN = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] and redis.call('GET', KEYS[2]) == ARGV[2] " +
                    "then redis.call('DEL', KEYS[1]); redis.call('DEL', KEYS[2]); return 1 else return 0 end", Long.class);

    @Autowired private UserRepository users;
    @Autowired private CpfService cpfs;
    @Autowired private CustomerIdentityHashService hashes;
    @Autowired private PasswordRecoveryRateLimiter rateLimiter;
    @Autowired private StringRedisTemplate strings;
    @Autowired private RedisTemplate<String, Object> objects;
    @Autowired private PasswordEncoder passwordEncoder;
    @Value("${app.security.password-recovery.code-ttl:PT15M}") private Duration codeTtl;
    @Value("${app.security.password-recovery.token-ttl:PT15M}") private Duration tokenTtl;

    public void request(String rawCpf, String ip) {
        String cpf = cpfs.requireValid(rawCpf);
        rateLimiter.request(ip, cpf);
        users.getByCpf(cpf).ifPresent(user -> {
            try {
                String code = String.valueOf(100000 + RANDOM.nextInt(900000));
                strings.opsForValue().set(codeKey(cpf), codeHash(cpf, code), codeTtl);
                objects.opsForList().leftPush("recovery_queue",
                        new ValidateCodeRequest(code, user.getCpf(), user.getEmail(), user.getNome()));
            } catch (RuntimeException unavailable) {
                log.error("[PASSWORD-RECOVERY] operation=REQUEST status=DELIVERY_UNAVAILABLE");
            }
        });
    }

    public String validateCode(String rawCpf, String code, String ip) {
        String cpf = cpfs.requireValid(rawCpf);
        rateLimiter.validate(ip, cpf);
        Long consumed = strings.execute(CONSUME_CODE, List.of(codeKey(cpf)), codeHash(cpf, code));
        if (!Long.valueOf(1).equals(consumed)) {
            log.warn("[PASSWORD-RECOVERY] operation=VALIDATE status=INVALID");
            throw invalid();
        }
        User user = users.getByCpf(cpf).orElseThrow(this::invalid);
        String token = randomToken();
        String tokenHash = hashes.hash("password-reset-token", token);
        String activeKey = activeKey(cpf);
        String previous = strings.opsForValue().get(activeKey);
        if (previous != null) strings.delete(tokenKey(previous));
        strings.opsForValue().set(tokenKey(tokenHash), cpfHash(cpf), tokenTtl);
        strings.opsForValue().set(activeKey, tokenHash, tokenTtl);
        return token;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ip) {
        String cpf = cpfs.requireValid(request.cpf());
        rateLimiter.reset(ip, cpf);
        String tokenHash = hashes.hash("password-reset-token", request.token());
        Long consumed = strings.execute(CONSUME_TOKEN,
                List.of(activeKey(cpf), tokenKey(tokenHash)), tokenHash, cpfHash(cpf));
        if (!Long.valueOf(1).equals(consumed)) {
            log.warn("[PASSWORD-RECOVERY] operation=RESET status=INVALID");
            throw invalid();
        }
        User user = users.findByIdForUpdate(users.getByCpf(cpf).orElseThrow(this::invalid).getIdUser())
                .orElseThrow(this::invalid);
        user.setSenha(passwordEncoder.encode(request.novaSenha()));
        users.save(user);
    }

    private String codeKey(String cpf) { return "app247:password-code:" + cpfHash(cpf); }
    private String activeKey(String cpf) { return "app247:password-active:" + cpfHash(cpf); }
    private String tokenKey(String tokenHash) { return "app247:password-token:" + tokenHash; }
    private String cpfHash(String cpf) { return hashes.hash("password-cpf", cpf); }
    private String codeHash(String cpf, String code) { return hashes.hash("password-code", cpf + "|" + code); }
    private String randomToken() { byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private ApiBusinessException invalid() { return new ApiBusinessException(HttpStatus.BAD_REQUEST,
            "PASSWORD_RESET_INVALID", "Código ou token de recuperação inválido ou expirado"); }
}
