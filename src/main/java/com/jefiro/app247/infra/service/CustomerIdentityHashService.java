package com.jefiro.app247.infra.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
public class CustomerIdentityHashService {
    private final String secret;

    public CustomerIdentityHashService(@Value("${app247.identity.hash-secret}") String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("IDENTITY_HASH_SECRET é obrigatório para inicializar a identidade por CPF");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException("IDENTITY_HASH_SECRET deve possuir pelo menos 32 caracteres");
        }
        this.secret = secret;
    }

    public String hashCpf(String normalizedCpf) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedCpf.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 indisponível", exception);
        }
    }
}
